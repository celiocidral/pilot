(ns pilot.graphql.introspection
  (:require [clojure.java.io :as io]
            [pilot.graphql.exec :as gql]))

(def ^:private introspection-query
  (slurp (io/resource "pilot/graphql/introspection.gql")))

(defn introspect
  ([graphql-url]
   (introspect graphql-url nil))
  ([graphql-url http-headers]
   (let [response (gql/exec graphql-url http-headers introspection-query nil)]
     (with-meta (get-in response [:data :__schema])
       {:graphql-schema? true}))))

(comment (introspect "http://localhost:3000/graphql"))
