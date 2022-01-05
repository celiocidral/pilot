(ns pilot.examples.usage
  (:require [pilot.env :as env]
            [pilot.env.graphql :as g]
            [pilot.graphql.reveal :as r]))

(comment
  ;; initialize the environment
  (env/switch! :local))

(comment
  ;; execute some query
  (g/exec-as (g/credentials :aa1)
             "query { viewer { user3 { email } } }"))

(comment
  ;; how to use the graphql reveal extension

  ;; fetch & store the graphql schema
  (def -schema (g/introspect-as :admin))

  ;; show the schema browser
  (r/show-schema -schema)

  ;; show specific types
  (r/show-type -schema "Case")
  (r/show-type -schema "User3")
  (r/show-type -schema "ChatMessage")

  ;; show specific mutations
  (r/show-mutation -schema "createCase2")
  (r/show-mutation -schema "createUser")
  (r/show-mutation -schema "changeOnDuty")

  ;; show specific queries
  (r/show-query -schema "cases")
  (r/show-query -schema "users")
  (r/show-query -schema "organizations")

  ;; show specific subscriptions
  (r/show-subscription -schema "caseUpdated2"))
