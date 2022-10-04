(ns pilot.db
  (:require [camel-snake-kebab.core :as csk]
            [clojure.core.cache.wrapped :as caching]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as jdbc.result-set]
            [hikari-cp.core :as hikari :refer [make-datasource]]))

(defonce ^:private cache (caching/basic-cache-factory {}))

(defn- connect!* [cfg]
  (tap> [::connect! (:username cfg) (:server-name cfg)])
  (let [opts (-> jdbc/unqualified-snake-kebab-opts
                 (assoc :column-fn (fn [v]
                                     (csk/->snake_case v :separator \-)))
                 (assoc :builder-fn (fn [rs opts]
                                      (jdbc.result-set/as-unqualified-modified-maps
                                       rs
                                       (assoc opts :label-fn (fn [v] (csk/->kebab-case v :separator \_)))))))]
    (jdbc/with-options
      (make-datasource cfg)
      opts)))

(defn- close-connection! [db]
  (some-> db jdbc/get-datasource .close))

(defn- cache-key [cfg]
  (select-keys cfg [:username
                    :database-name
                    :server-name]))

(defn connect! [cfg]
  (let [k (cache-key cfg)]
    (-> cache
        (caching/through-cache k (fn [_] (connect!* cfg)))
        (get k))))

(defn disconnect! [cfg]
  (when-let [conn (caching/lookup cache (cache-key cfg))]
    (close-connection! conn)
    (caching/evict cache (cache-key cfg))
    nil))
