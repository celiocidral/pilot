(ns pilot.env.graphql
  (:require [pilot.auth.core :as auth]
            [pilot.env :as env]
            [pilot.graphql.exec :as g]
            [pilot.graphql.introspection :as i]))

;; here just for convenience
(defn credentials [id]
  (env/credentials id))

(defn- graphql-url []
  (env/value-of :graphql-url))

(defn- get-access-token [credentials]
  (auth/get-access-token (env/system-component :auth/provider)
                         credentials))

(defn- http-headers [credentials]
  {"authorization" (str "Bearer " (get-access-token credentials))})

(defn exec
  ([query]
   (exec query nil))
  ([query variables]
   (g/exec (graphql-url) {} query variables)))

(defn exec-as
  ([credentials query]
   (exec-as credentials query nil))
  ([credentials query variables]
   (g/exec (graphql-url)
           (http-headers credentials)
           query
           variables)))

(defn introspect []
  (i/introspect (graphql-url)))

(defn introspect-as [credentials-id]
  (i/introspect (graphql-url)
                (http-headers (credentials credentials-id))))

(comment (introspect))
