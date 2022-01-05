(ns pilot.graphql.schema
  (:require [clojure.walk]
            [pilot.util :refer [=ignore-case emptiness? includes-ignore-case? some-keys strip update-every-map-once]]))

(defn search-operations [schema root-type pred]
  {:pre [(map? schema) (#{:mutation :query :subscription} root-type) (fn? pred)]}
  (let [root-type-key (-> root-type name (str "Type") keyword)
        root-type-name (get-in schema [root-type-key :name])]
    (-> schema
        :types
        (->> (filter #(= (:name %) root-type-name)))
        first
        :fields
        (->> (filter #(pred (:name %)))
             (map #(with-meta % {:graphql-schema schema}))))))

(defn search-mutations [mutation-name schema]
  {:pre [(string? mutation-name)]}
  (search-operations schema :mutation #(includes-ignore-case? % mutation-name)))

(defn search-queries [query-name schema]
  {:pre [(string? query-name)]}
  (search-operations schema :query #(includes-ignore-case? % query-name)))

(defn- search-types*
  [schema pred]
  (->> (:types schema)
       (filter #(pred (:name %)))
       (map #(with-meta % {:graphql-schema schema}))))

(defn search-types
  ([type-name schema]
   (search-types* schema #(includes-ignore-case? (:name %) type-name))))

(defn find-operation [operation-type operation-name schema]
  {:pre [(#{:query :mutation :subscription} operation-type)
         (string? operation-name)
         (map? schema)]}
  (first (search-operations schema operation-type (partial =ignore-case operation-name))))

(defn find-mutation [mutation-name schema]
  (find-operation :mutation mutation-name schema))

(defn find-query [query-name schema]
  (find-operation :query query-name schema))

(defn find-subscription [subscription-name schema]
  (find-operation :subscription subscription-name schema))

(defn find-type [type-name schema]
  (first (search-types* schema (partial =ignore-case type-name))))

(defn queries [schema]
  (:fields (find-type "Query" schema)))

(defn mutations [schema]
  (:fields (find-type "Mutation" schema)))

(defn subscriptions [schema]
  (:fields (find-type "Subscription" schema)))

(defn types [schema]
  (:types schema))

(defn- expand* [element schema expand?]
  {:pre [(map? element) (map? schema)]}
  (let [accepted-kind? #{"OBJECT" "UNION" "ENUM" "INPUT_OBJECT"}
        replace? (fn [x] (and (map? x)
                              (accepted-kind? (:kind x))
                              (expand? (:name x))))]
    (update-every-map-once
     (fn [x]
       (if (replace? x)
         (find-type (:name x) schema)
         x))
     element)))

(defn expand [element schema & type-names]
  (expand* element schema (set type-names)))

(defn cleanup [m]
  {:pre [(or (nil? m) (map? m))]}
  (if (map? m)
    (strip emptiness? m)
    m))

(defn simplify [element schema]
  {:pre [(map? element) (map? schema)]}
  (let [replace-with-name? #{"SCALAR" "INTERFACE"}
        has-name? #(and (map? %) (:name %))]
    (clojure.walk/prewalk
     #(cond
        (replace-with-name? (:kind %))
        (:name %)

        (and (map? %) (= #{:kind :name} (-> % some-keys set)))
        (:name %)

        (and (map? %) 
             (= #{:type} (-> % some-keys set))
             (or (string? (:type %))
                 (string? (get-in % [:type :name]))
                 (list? (:type %))))
        (:type %)

        (= "LIST" (:kind %))
        (list 'list (simplify (:ofType %) schema))

        (= "NON_NULL" (:kind %))
        (let [x (simplify (:ofType %) schema)]
          (cond
            (string? x) (str x "!")
            (list? x) (conj (rest x) 'list!)
            (map? x) (assoc x :required? true)
            :else %))

        (and (vector? %) (every? has-name? %))
        (reduce (fn [m e] (assoc m (-> e :name keyword) (dissoc e :name))) {} %)

        :else %)
     element)))
