(ns pilot.examples.usage
  (:require [pilot.env]
            [pilot.core :as p]
            [pilot.graphql.reveal :as r]))

;; load the dev environment and store it in a var
(def env (pilot.env/env :dev))

;; a couple of graphql queries
(comment
  (p/gql-query env
               {:auth :rpa.aa1
                :query "query { viewer { user3 { email } } }"})

  (p/gql-query env
               {:auth :rpa.aa1
                :query "query Cases ($input: CasesQueryInput!) {
                          cases (input: $input) {
                            __typename
                            ... on Case {
                              description
                            }
                          }
                        }"
                :variables {:input {:where [{:airportIcao {:_eq "KFLL"}}]
                                    :limit 10}}}))

(comment
  ;; get a db connection. the connection is cached on the first call and remains
  ;; open until you call close-db!
  (p/db env)

  (p/close-db! env))

(comment
  ;; how to use the graphql reveal extension

  ;; fetch & store the graphql schema
  (def -schema (p/introspect env))

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
