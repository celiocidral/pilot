(ns pilot.graphql.exec
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.string :refer [starts-with?]])
  (:import clojure.lang.ExceptionInfo))

(defn- json? [response]
  (some-> (get-in response [:headers "Content-Type"])
          (starts-with? "application/json")
          (true?)))

(defn- http-post [url http-headers body]
  (try
    (http/post url
               {:headers (merge {"Content-Type" "application/json"}
                                http-headers)
                :body (json/write-str body)})
    (catch ExceptionInfo e
      (or (and (:status (ex-data e)) (ex-data e))
          (throw (Exception. e))))))

(defn- error? [response]
  (not= 200 (:status response)))

(defn exec
  [graphql-url http-headers query variables]
  {:pre [(string? graphql-url)
         (or (nil? http-headers) (map? http-headers))
         (string? query)
         (or (nil? variables) (map? variables))]}
  (let [request-body (cond-> {:query query}
                       (some? variables) (assoc :variables variables))
        response (http-post graphql-url http-headers request-body)
        response (cond-> response
                   (json? response) (update :body #(json/read-str % :key-fn keyword)))]
    (when (error? response)
      (tap> [::error response])
      (throw (ex-info (str "HTTP " (:status response) ": " (:body response))
                      response)))
    (:body response)))
