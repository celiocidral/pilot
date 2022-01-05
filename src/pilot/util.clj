(ns pilot.util
  (:require [clojure.string :refer [includes? lower-case]]
            [clojure.walk :refer [postwalk]]))

(defn strip [pred m]
  {:pre [(or (nil? m) (map? m))]}
  (postwalk
   #(if (map? %)
      (into {} (filter (comp not pred peek) %))
      %)
   m))

(defn includes-ignore-case? [s substr]
  (includes? (lower-case s) (lower-case substr)))

(defn =ignore-case [^String a ^String b]
  {:pre [(or (nil? a) (string? a))
         (or (nil? b) (string? b))]}
  (.equalsIgnoreCase (or a "") (or b "")))

(defn update-every-map-once
  ([f x]
   (update-every-map-once f #{} x))
  ([f skip? x]
   (cond
     (skip? x)
     x

     (map? x)
     (into (empty x) (map (partial update-every-map-once f (conj skip? x)) (f x)))

     (map-entry? x)
     (update x 1 #(update-every-map-once f skip? %))

     (coll? x)
     (into (empty x) (map (partial update-every-map-once f skip?) x))

     :else
     x)))

(defn emptiness? [x]
  (or (nil? x)
      (false? x)
      (and (coll? x) (not (seq x)))))

(defn some-keys [m]
  (->> m
       (filter (fn [[_ v]] (not (emptiness? v))))
       (map first)))
