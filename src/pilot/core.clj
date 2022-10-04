(ns pilot.core
  (:require [clojure.java.io :as io]
            [pilot.firebase :as firebase]
            [pilot.db :as db]
            [pilot.graphql :as g]))

(defn firebase-token
  "Generates a firebase token. The token is cached and regenerated once expired.
   
   `env` must be a map.

   `auth` must be either a keyword or a map. If it's a keyword, the credentials
   are looked up in `env`. If it's a map it must containg `:username` and
   `password`."
  ([env auth]
   {:pre [(map? env) (or (keyword? auth)
                         (map? auth))]}
   (let [{:keys [username password]} (cond
                                       (keyword? auth)
                                       (get-in env [:firebase-accounts auth])

                                       (map? auth)
                                       auth)]
     (firebase-token env username password)))
  ([env username password]
   {:pre [(map? env) (string? username) (string? password)]}
   (firebase/get-access-token (:firebase-key env) username password)))

(defn- http-headers [env auth]
  (cond-> {}
    (some? auth)
    (assoc "authorization" (str "Bearer " (firebase-token env auth)))))

(defn gql-query
  "Runs a graphql query.
   
   `env` must be a map.

   `request` must be a map with:

   `:query` must be a graphql query string.

   `:variables` (optional) must be a map containing the query input arguments.

   `:auth` (optional) must be either a keyword or a map (see docstring of `firebase-token`).
   "
  [env {:keys [query variables auth]}]
  {:pre [(map? env)
         (string? query)
         (or (nil? variables) (map? variables))]}
  (g/exec (:graphql-url env)
          (http-headers env auth)
          query
          variables))

(def ^:private introspection-query
  (slurp (io/resource "pilot/graphql/introspection.gql")))

(defn introspect
  [env]
  (let [response (gql-query env {:query introspection-query})]
    (with-meta (get-in response [:data :__schema])
      {:graphql-schema? true})))

(defn db
  "Returns a db connection for the provided environment. The db connection is
   established only once and remains open until `close-db!` is called on the
   same `env`.
   
   `env` must be a map."
  [env]
  (db/connect! (:db env)))

(defn close-db!
  "Closes the db connection for the provided environment.
   
   `env` must be a map."
  [env]
  (db/disconnect! (:db env)))
