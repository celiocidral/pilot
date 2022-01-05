(ns pilot.graphql.reveal
  (:require [clojure.string :refer [lower-case upper-case trim]]
            [pilot.graphql.schema :as s]
            [pilot.util :refer [includes-ignore-case?]]
            [vlaaad.reveal :as r]))

(declare fx-graphql-schema-element)

(defn- root-type-name [field-name schema]
  (->> ["Query" "Mutation" "Subscription"]
       (mapcat (fn [root-type]
                 (->> (s/find-type root-type schema)
                      :fields
                      (map #(assoc % :root-type root-type)))))
       (filter #(= field-name (:name %)))
       (first)
       :root-type))

(defn- make-search-list [schema]
  (->> ["Query" "Mutation" "Subscription"]
       (mapcat (fn [root-type]
                 (->> (s/find-type root-type schema)
                      :fields
                      (map #(assoc % :root-type root-type)))))
       (concat (:types schema))))

(defn- search [schema element-name]
  (->> (:-search-list schema)
       (filter #(includes-ignore-case? (:name %) element-name))
       (sort-by #(lower-case (:name %)))))

(defn- deprecated-last [{a-deprecated? :isDeprecated a-name :name}
                        {b-deprecated? :isDeprecated b-name :name}]
  (if (= a-deprecated? b-deprecated?)
    (compare a-name b-name)
    (- (if a-deprecated? 1 0)
       (if b-deprecated? 1 0))))

(defn- resolve-type [t]
  (case (:kind t)
    "NON_NULL" (resolve-type (:ofType t))
    "LIST" (resolve-type (:ofType t))
    t))

(defn- describe-type [t]
  (case (:kind t)
    "NON_NULL" (str (describe-type (:ofType t)) "!")
    "LIST" (str "[" (describe-type (:ofType t)) "]")
    (:name t)))

(def ^:private fx-deprecated-label
  {:fx/type :label
   :text "deprecated"
   :style {:-fx-text-fill :red}})

(defn- fx-deprecation-reason-or-description [v]
  (or (when (:deprecationReason v)
        {:fx/type :label
         :text (:deprecationReason v)
         :style {:-fx-text-fill :grey}})
      (when (:description v)
        {:fx/type :label
         :text (:description v)
         :style {:-fx-text-fill :grey}})))

(defn- fx-graphql-enum-values [enum-values]
  (when enum-values
    {:fx/type :v-box
     :spacing 5
     :children (map (fn [v]
                      {:fx/type :h-box
                       :spacing 10
                       :children (keep identity
                                       [(when (:isDeprecated v) fx-deprecated-label)
                                        {:fx/type :label :text (:name v)}
                                        (fx-deprecation-reason-or-description v)])})
                    (sort deprecated-last enum-values))}))

(defn- fx-graphql-type-link [graphql-type schema]
  (when graphql-type
    {:fx/type :hyperlink
     :text (describe-type graphql-type)
     :on-action (fn [_]
                  (-> (:name (resolve-type graphql-type))
                      (s/find-type schema)
                      (fx-graphql-schema-element schema)
                      (r/open-view)
                      (r/submit-command!)))}))

(defn- fx-graphql-typed-item-list [items schema]
  (when (seq items)
    {:fx/type :v-box
     :spacing 5
     :children (map (fn [x]
                      {:fx/type :h-box
                       :spacing 10
                       :children (keep identity
                                       [(when (:isDeprecated x) fx-deprecated-label)
                                        {:fx/type :label
                                         :text (:name x)}
                                        (fx-graphql-type-link (:type x) schema)
                                        (fx-deprecation-reason-or-description x)])})
                    (sort deprecated-last items))}))

(defn- fx-graphql-type-list [named-types schema]
  (when named-types
    {:fx/type :v-box
     :spacing 5
     :children (map (fn [v]
                      (-> (:name v)
                          (s/find-type schema)
                          (fx-graphql-type-link schema)))
                    named-types)}))

(defn- fx-graphql-implemented-interfaces [interfaces schema]
  (when (seq interfaces)
    {:fx/type :h-box
     :spacing 10
     :children (into
                [{:fx/type :label
                  :text "IMPLEMENTS"
                  :style {:-fx-text-fill :grey}}]
                (map #(fx-graphql-type-link % schema)
                     interfaces))}))

(defn- fx-graphql-element-header [element schema]
  {:fx/type :h-box
   :spacing 10
   :children (keep identity
                   [(when (:isDeprecated element) fx-deprecated-label)
                    {:fx/type :label
                     :text (if (:kind element)
                             (:kind element)
                             (upper-case (root-type-name (:name element) schema)))
                     :style {:-fx-text-fill :grey}}
                    {:fx/type :label
                     :text (:name element)
                     :style {:-fx-text-fill :yellow}}
                    (fx-graphql-type-link (:type element) schema)
                    (fx-graphql-implemented-interfaces (:interfaces element) schema)])})

(defn- fx-with-caption [text content]
  (when content
    {:fx/type :v-box
     :spacing 10
     :children [{:fx/type :label
                 :text text
                 :style {:-fx-text-fill :grey}}
                content]}))

(defn- fx-scrollable [content]
  {:fx/type :scroll-pane
   :fit-to-height true
   :content {:fx/type :v-box
             :padding 10
             :children [content]}})

(defn- fx-graphql-schema-element [element schema]
  (fx-scrollable
   {:fx/type :v-box
    :spacing 20
    :children (keep identity
                    [(fx-graphql-element-header element schema)
                     (when (:description element)
                       {:fx/type :label
                        :text (:description element)})
                     (when (:deprecationReason element)
                       {:fx/type :v-box
                        :spacing 20
                        :children [{:fx/type :label
                                    :style {:-fx-text-fill :grey}
                                    :text "DEPRECATION REASON"}
                                   {:fx/type :label
                                    :text (:deprecationReason element)}]})
                     (fx-with-caption "ARGUMENTS" (fx-graphql-typed-item-list (:args element) schema))
                     (fx-with-caption "FIELDS" (fx-graphql-typed-item-list (:fields element) schema))
                     (fx-with-caption "INPUT FIELDS" (fx-graphql-typed-item-list (:inputFields element) schema))
                     (fx-with-caption "POSSIBLE TYPES" (fx-graphql-type-list (:possibleTypes element) schema))
                     (fx-graphql-enum-values (:enumValues element))])}))

(defn- fx-graphql-elements-list [schema caption elements]
  (fx-with-caption
   caption
   (let [search-text (atom "")]
     {:fx/type r/observable-view
      :ref search-text
      :fn (fn [text]
            {:fx/type :v-box
             :spacing 10
             :children [{:fx/type :text-field
                         :style {:-fx-background-color "#333"}
                         :pref-width 400
                         :text text
                         :on-text-changed #(reset! search-text %)}
                        {:fx/type :v-box
                         :spacing 5
                         :children (map (fn [q]
                                          {:fx/type :h-box
                                           :spacing 10
                                           :children (keep identity
                                                           [(when (:isDeprecated q) fx-deprecated-label)
                                                            {:fx/type :hyperlink
                                                             :text (:name q)
                                                             :on-action (fn [_]
                                                                          (-> (fx-graphql-schema-element q schema)
                                                                              (r/open-view)
                                                                              (r/submit-command!)))}])})
                                        (sort deprecated-last
                                              (if (empty? text)
                                                elements
                                                (filter #(includes-ignore-case? (:name %) text)
                                                        elements))))}]})})))

(defn- fx-graphql-schema-stats [schema]
  {:fx/type :v-box
   :spacing 5
   :children (map (fn [[caption elements]]
                    {:fx/type :hyperlink
                     :text (str (count elements) " " caption)
                     :on-action (fn [_]
                                  (-> (fx-graphql-elements-list schema (upper-case caption) elements)
                                      (fx-scrollable)
                                      (r/open-view)
                                      (r/submit-command!)))})
                  [["types" (s/types schema)]
                   ["queries" (s/queries schema)]
                   ["mutations" (s/mutations schema)]
                   ["subscriptions" (s/subscriptions schema)]])})

(defn- fx-graphql-schema-search [schema]
  (let [search-text (atom "")]
    {:fx/type r/observable-view
     :ref search-text
     :fn (fn [text]
           {:fx/type :v-box
            :spacing 20
            :children [{:fx/type :label
                        :text "SEARCH"
                        :style {:-fx-text-fill :grey}}
                       {:fx/type :text-field
                        :pref-width 400
                        :text text
                        :on-text-changed #(reset! search-text %)
                        :style {:-fx-background-color "#333"}}
                       {:fx/type :v-box
                        :spacing 10
                        :children (map (fn [element]
                                         {:fx/type :h-box
                                          :spacing 10
                                          :children [{:fx/type :label
                                                      :text (or (:kind element) (upper-case (:root-type element)))
                                                      :style {:-fx-text-fill :grey}}
                                                     {:fx/type :hyperlink
                                                      :text (:name element)
                                                      :on-action (fn [_]
                                                                   (-> (fx-graphql-schema-element element schema)
                                                                       (r/open-view)
                                                                       (r/submit-command!)))}]})
                                       (when (pos? (count (trim text)))
                                         (search schema (trim text))))}]})}))

(defn- fx-graphql-schema [schema]
  (let [schema (assoc schema :-search-list (make-search-list schema))]
    (fx-scrollable
     {:fx/type :v-box
      :spacing 20
      :children [{:fx/type :label
                  :text "GRAPHQL SCHEMA"
                  :style {:-fx-text-fill :grey}}
                 (fx-graphql-schema-stats schema)
                 (fx-graphql-schema-search schema)]})))

(defn- graphql-schema-element-action [v]
  (when-let [schema (:graphql-schema (meta v))]
    (fn [] (fx-graphql-schema-element v schema))))

(defn- graphql-schema-action [v]
  (when (:graphql-schema? (meta v))
    (fn [] (fx-graphql-schema v))))

(r/defaction ::graphql-schema-element [v] (graphql-schema-element-action v))
(r/defaction ::graphql-schema [v] (graphql-schema-action v))

(defn show-schema [schema]
  {:pre [(map? schema)]}
  (r/submit-command! (r/open-view (fx-graphql-schema schema))))

(defn show-schema-element [schema element]
  {:pre [(map? element) (map? schema)]}
  (r/submit-command! (r/open-view (fx-graphql-schema-element element schema))))

(defn show-type [schema type-name]
  {:pre [(string? type-name)]}
  (show-schema-element schema (s/find-type type-name schema)))

(defn show-query [query-name schema]
  {:pre [(string? query-name)]}
  (show-schema-element schema (s/find-query query-name schema)))

(defn show-mutation [mutation-name schema]
  {:pre [(string? mutation-name)]}
  (show-schema-element schema (s/find-mutation mutation-name schema)))

(defn show-subscription [subscription-name schema]
  {:pre [(string? subscription-name)]}
  (show-schema-element schema (s/find-subscription subscription-name schema)))

