(ns pilot.env
  (:require [clj-time.jdbc]
            [clojure.java.io :as io]
            [integrant.core :as ig]))

(def ^:private home-dir (io/file (System/getenv "HOME") ".pilot"))

(defonce ^:private current-env (atom nil))

(defn env-file [name & names]
  (apply io/file home-dir name names))

(def ^:private config-file (env-file "pilot.edn"))

(defn- load-config [env]
  (when (.exists config-file)
    (-> config-file slurp (ig/read-string) env)))

(defn- halt! []
  (when-let [system (:system @current-env)]
    (ig/halt! system)))

(defn switch! [env-key]
  {:pre [(keyword? env-key)]}
  (let [cfg (load-config env-key)]
    (halt!)
    (ig/load-namespaces (:system cfg))
    (reset! current-env
            (update cfg :system #(ig/init %)))))

(defn value-of [k-or-ks]
  (assert (some? @current-env) "Environment not set")
  (get-in @current-env
          (if (vector? k-or-ks)
            k-or-ks
            [k-or-ks])))

(defn system-component [k]
  (-> (:system @current-env)
      (ig/find-derived-1 k)
      (second)))

(defn credentials [id]
  {:pre [(keyword? id)] :post [(map? %)]}
  (value-of [:credentials id]))

(comment
  (switch! :local))
