(ns pilot.local-store
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [pilot.env :refer [env-file]])
  (:import java.io.OutputStream
           [java.security MessageDigest DigestInputStream]))

(defn md5sum [some-string]
  (let [digest (MessageDigest/getInstance "MD5")]
    (with-open [input-stream  (io/input-stream (.getBytes some-string))
                digest-stream (DigestInputStream. input-stream digest)
                output-stream (io/output-stream (OutputStream/nullOutputStream))]
      (io/copy digest-stream output-stream))
    (format "%032x" (BigInteger. 1 (.digest digest)))))

(def root-dir (env-file "store"))

(defn get-file [folder id]
  (io/file root-dir folder (md5sum id)))

(defn read-edn [folder id]
  {:pre [(string? folder) (string? id)]}
  (let [file (get-file folder id)]
    (when (.exists file)
      (edn/read-string (slurp file)))))

(defn write-edn [folder id content]
  {:pre [(string? folder) (string? id)]}
  (let [file (get-file folder id)]
    (io/make-parents file)
    (spit file content)
    content))
