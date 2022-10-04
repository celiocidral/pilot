(ns pilot.firebase
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.string :as str])
  (:import [com.github.benmanes.caffeine.cache
            CacheLoader
            Caffeine
            Expiry
            LoadingCache]
           [java.util.concurrent TimeUnit]))

(defn- http-post [url content-type body]
  (http/post url
             {:headers {"content-type" content-type}
              :body (if (= "application/json" content-type)
                      (json/write-str body)
                      body)}))

(defn- generate-access-token [firebase-key email password]
  {:pre [(string? email) (string? password)]}
  (tap> [:generate-access-token email])
  (let [url (str "https://www.googleapis.com/identitytoolkit/v3/relyingparty/verifyPassword?key=" firebase-key)
        response (http-post url "application/json" {:email email
                                                    :password password
                                                    :returnSecureToken true})
        status (:status response)
        body (json/read-str (:body response) :key-fn keyword)]
    (when (not= 200 status)
      (throw (ex-info "Error generating access token" response)))
    (let [expires-in (Integer/parseInt (:expiresIn body))]
      {:access-token (:idToken body)
       :refresh-token (:refreshToken body)
       :expires-in expires-in})))

(def ^:private token-ttl-expiry
  (reify Expiry
    (expireAfterCreate [_ _k firebase-claim _current-time]
      (-> TimeUnit/SECONDS
          (.toNanos (:expires-in firebase-claim))))

    (expireAfterUpdate [_ _k _v _current-time current-duration]
      current-duration)

    (expireAfterRead [_ _k _v _current-time current-duration]
      current-duration)))

(def ^:private cache-loader
  (reify CacheLoader
    (load [_ k]
      (let [[firebase-key email password] (str/split k #"\t")]
        (generate-access-token firebase-key email password)))))

(defonce ^:private ^LoadingCache cache
  (-> (Caffeine/newBuilder)
      (.expireAfter token-ttl-expiry)
      (.build cache-loader)))

(defn get-access-token [firebase-key email password]
  (let [k (str firebase-key "\t" email "\t" password)]
    (-> (.get cache k)
        :access-token)))
