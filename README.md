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

Create an EDN file at `~/.pilot/pilot.edn` and write some config like the example below. I hope it's self-explanatory.

```clojure
{:local {:graphql-url "http://localhost:3000/graphql"

         :system
         {[:pilot.auth.firebase/provider :auth/provider]
          {:firebase-key "xxxxxxxx"}

          [:pilot.db/connection :db/connection]
          {:username           "postgres"
           :password           "xxxxxxxx"
           :database-name      "xxxxxxxx"
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

         :credentials
         {:admin {:username "youremail+admin@example.com"
                  :password "xxxxxxxx"}
          :moc {:username "youremail+moc@example.com"
                :password "xxxxxxxx"}}}
 :development { ... }
 :staging { ... }
 :production { ... }}
```

Stuff under `:system` is managed by Integrant.

# Switching Between Environments

When switching to a different environment, use the environment key as defined in the configuration. Before switching, it shuts down the currently loaded environment, i.e. closes db connection, and then loads the new environment.

```clojure
(require '[pilot.env :as env])

(env/switch! :local)
```

# Executing GraphQL Queries and Mutations

Once you switch to some environment, you can start making GraphQL requests.

```clojure
(require '[pilot.env.graphql :as g])

(g/exec-as (g/credentials :moc)
           "query { viewer { user3 { email } } }")
```

`exec-as` requires Firebase credentials. You can use the `credentials` fn to lookup some credentials by their alias as defined in the configuration. `exec-as` will generate the Firebase token and add it to the HTTP request as authorization header.

There's also an `exec` fn that doesn't take credentials.

# Inspecting The GraphQL Schema

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

# Reveal Extension

If you use [Reveal](https://vlaaad.github.io/reveal/) there's a fn that renders the GraphQL schema on Reveal's result panel. It allows you to browse the schema, search for queries, mutations, types, etc.

```clojure
(require '[pilot.env.graphql :as g])
(require '[pilot.graphql.reveal :as r])

(def schema (g/introspect))

(r/show-schema schema)
```

# Getting The DB Connection

```clojure
(require '[pilot.env :as env])

(env/system-component :db/connection)
```

# Next Steps

* Generate GraphQL query text.
* Write some kind of linter maybe? Could check for naming & structure conventions.
