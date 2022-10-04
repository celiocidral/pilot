# What's Pilot?

A bunch of Clojure fns to ease some of the pains that come from using GraphQL from the REPL.

# Motivation

The pain points I have when using GraphQL during development:

* The GraphiQL IDE and other IDEs I've used are ok but:
  1. Too much switching from keyboard to mouse and back again,
  2. Manual generation of Firebase tokens,
  3. Manual auth header setup,
  4. Not straightforward to maintain a large history of queries,
  5. Doesn't come with the benefits of running queries from a programming language, e.g. manipulate/generate stuff.
* Switching between different environments, i.e. local, development, staging, etc, is difficult:
  1. Requires juggling different GraphQL URLs, Firebase keys, etc.
  2. It's difficult to manage/use Firebase accounts from different environments, you have to remember usernames & passwords.

To address those pain points, Pilot provides the following:

* Per environment configuration, where you define things like GraphQL URL, Firebase key, user credentials.
* Easily switch between different environments.
* Automatic generation of Firebase tokens.
* Refer to user credentials by short aliases.
* A [Reveal](https://vlaaad.github.io/reveal/) extension for browsing GraphQL schema.

There's also support for connecting to databases, which is something I didn't want to support initially because I wanted to interact with GraphQL only when testing locally, but some things that I need, like finding an organization by code and type, are not available in our GraphQL API, so I added db access.

# Configuring Environments

Create an EDN file at `~/.pilot/pilot.edn` and add enviroment configuration like the example below. Each top-level key contains configuration specific to an environment.

```clojure
{:local {:graphql-url "http://localhost:3000/graphql"

         :firebase-key "xxxxxxxx"

         ;; a map of aliases -> firebase account credentials
         ;; in this example we specify two accounts, an admin and an moc
         :firebase-accounts
         {:admin {:username "youremail+admin@example.com"
                  :password "xxxxxxxx"}
          :moc {:username "youremail+moc@example.com"
                :password "xxxxxxxx"}}

         :db {:username           "postgres"
              :password           "password"
              :database-name      "beacon_dev"
              :server-name        "localhost"
              :port-number        8354
              :auto-commit        true
              :read-only          false
              :connection-timeout 30000
              :validation-timeout 5000
              :idle-timeout       600000
              :max-lifetime       1800000
              :minimum-idle       10
              :maximum-pool-size  10
              :pool-name          "db-pool"
              :adapter            "postgresql"
              :register-mbeans    false}}
 
 :development { ... }
 :staging { ... }
 :production { ... }}
```

# Environments

Everything Pilot needs is contained in an environment map. In the example below I'm loading the local environment and storing it in a var so I can pass it later to other functions.

```clojure
(require '[pilot.env])

(def env (pilot.env/env :local))
```

# Getting a DB Connection

```clojure
(require '[pilot.core :as p])
(require '[pilot.env :as env])

(def env (pilot.env/env :local))

(comment
  ;; get a db connection. the connection is cached on the first call and remains
  ;; open until you call close-db! so you can call p/db any number of times on
  ;; the same env.
  (p/db env)

  (p/close-db! env))
```

# Executing GraphQL Queries and Mutations

```clojure
(require '[pilot.env])
(require '[pilot.core :as p])

(def env (pilot.env/env :local))

(comment
  (p/gql-query env
               ;; the :auth key specifies the firebase account
               {:auth :moc
                :query "query { viewer { user3 { email } } }"})

  (p/gql-query env
               {:auth :moc
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
```

# Outdated Stuff

The sections below are probably outdated.

## Inspecting The GraphQL Schema

```clojure
(require '[pilot.env.graphql :as g])

(def schema (g/introspect))
```

`introspect` returns the entire GraphQL schema. The Beacon API schema is huge so you'll probably want to store the schema in some var to inspect parts of it instead of spitting it out in your REPL output.

The `pilot.graphql.schema` namespace has fns for searching/finding queries, mutations and types. Their output is kind of hard to read though, and it's not easy to navigate to other types, so you'll probably need some help there. For example, you could show the `similarCases` query in your REPL output like this:

```clojure
(require '[pilot.graphql.schema :as s])

(s/find-query "similarCases" schema)
```

You will see that its input type is `SimilarCasesInput`. If you want to look into its structure you'd have to find that type and print it out:

```clojure
(s/find-type "SimilarCasesInput" schema)
```

An easier way to navigate, though, is by specifying the types you want to "expand", for example, let's take the `users` query and expand some of the types that it refers to:

```clojure
(-> "users"
    (s/find-query schema)
    (s/expand schema "User3" "Organization2" "Position"))
```

`expand` looks up the types you specified and expand them in place.
That helps with navigation, but still the output makes your eyes bleed. It looks cluttered with null/empty fields and its structure is not easy to read. You can use `cleanup` to strip off empty fields, and then `simplify` to make the structure easier to read:

```clojure
(-> "users"
    (s/find-query schema)
    (s/expand schema "User3" "Organization2" "Position")
    (s/cleanup)
    (s/simplify schema))
```

## Reveal Extension

If you use [Reveal](https://vlaaad.github.io/reveal/) there's a fn that renders the GraphQL schema on Reveal's result panel. It allows you to browse the schema, search for queries, mutations, types, etc.

```clojure
(require '[pilot.env.graphql :as g])
(require '[pilot.graphql.reveal :as r])

(def schema (g/introspect))

(r/show-schema schema)
```

## Next Steps

* Generate GraphQL query text.
* Write some kind of linter maybe? Could check for naming & structure conventions.
