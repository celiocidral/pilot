(ns pilot.auth.firebase
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [integrant.core :as ig]
            [pilot.auth.core :as core]
            [pilot.local-store :as store])
  (:import java.time.ZonedDateTime))

(defn- now []
  (ZonedDateTime/now))

(defn- http-post [url content-type body]
  (http/post url
             {:headers {"content-type" content-type}
              :body (if (= "application/json" content-type)
                      (json/write-str body)
                      body)}))

(defn- generate-access-token [firebase-key email password]
  {:pre [(string? email) (string? password)]}
  (tap> ::generate-access-token)
  (let [created-at (now)
        url (str "https://www.googleapis.com/identitytoolkit/v3/relyingparty/verifyPassword?key=" firebase-key)
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
       :created-at (str created-at)
       :expires-in expires-in
       :expires-at (str (.plusSeconds created-at expires-in))})))

(defn- refresh-access-token [firebase-key refresh-token]
  {:pre [(string? refresh-token)]}
  (tap> ::refresh-access-token)
  (let [created-at (now)
        url (str "https://securetoken.googleapis.com/v1/token?key=" firebase-key)
        response (http-post url
                            "application/x-www-form-urlencoded"
                            (str "grant_type=refresh_token&refresh_token=" refresh-token))
        status (:status response)
        body (json/read-str (:body response) :key-fn keyword)]
    (when (not= 200 status)
      (throw (ex-info "Error refreshing access token" response)))
    (let [expires-in (Integer/parseInt (:expires_in body))]
      {:access-token (:access_token body)
       :refresh-token (:refresh_token body)
       :created-at (str created-at)
       :expires-in expires-in
       :expires-at (str (.plusSeconds created-at expires-in))})))

(defn- expired? [token]
  (.isAfter (now) (ZonedDateTime/parse (:expires-at token))))

(def ^:private read-edn (partial store/read-edn "firebase"))
(def ^:private write-edn (partial store/write-edn "firebase"))

(defrecord FirebaseAuthProvider [firebase-key]
  core/AuthProvider
  (get-access-token [_ {:keys [username password]}]
    (assert (string? username))
    (assert (string? password))
    (let [stored (read-edn username)
          token (cond
                  (nil? stored) (->> (generate-access-token firebase-key username password)
                                     (write-edn username))
                  (expired? stored) (->> (refresh-access-token firebase-key (:refresh-token stored))
                                         (write-edn username))
                  :else stored)]
      ;; TODO handle error
      (:access-token token))))

(defmethod ig/init-key ::provider [_ {:keys [firebase-key]}]
  {:pre [(string? firebase-key)]}
  (->FirebaseAuthProvider firebase-key))
