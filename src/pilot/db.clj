(ns pilot.db
  (:require [camel-snake-kebab.core :as csk]
            [clj-time.jdbc]
            [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as jdbc.result-set]
            [hikari-cp.core :as hikari :refer [make-datasource]]))

(defn connect-db [cfg]
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

(defn close-db [db]
  (some-> db jdbc/get-datasource .close))

(defmethod ig/init-key ::connection [_ cfg]
  {:pre [(map? cfg)]}
  (connect-db cfg))

(defmethod ig/halt-key! ::connection [_ db]
  (close-db db))
