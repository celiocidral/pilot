(ns pilot.env
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def ^:private home-dir
  (io/file (System/getenv "HOME") ".pilot"))

(defn env-file [name & names]
  (apply io/file home-dir name names))

(def ^:private default-config-file
  (env-file "pilot.edn"))

(defn env [env-key]
  (when (.exists default-config-file)
    (-> default-config-file
        (slurp)
        (edn/read-string)
        (get env-key))))
