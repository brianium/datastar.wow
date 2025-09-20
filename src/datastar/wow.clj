(ns datastar.wow
  (:require [datastar.wow.middleware :as mw]
            [datastar.wow.schema :as schema]
            [starfederation.datastar.clojure.api :as d*]
            [starfederation.datastar.clojure.utils :refer [def-clone]]))

(defn with-datastar
  "Add the power of declarative Datastar to your ring app. Handlers leveraging this
   middleware should return a response map containing a vector of Datastar events as effects.
   Effects are powered by [nexus](https://github.com/cjohansen/nexus). The ->sse-response function
   is expected to be an ->sse-response function provided by the official [Datastar SDK for Clojure](https://github.com/starfederation/datastar-clojure) or
   a custom adapter implemented according to the rules of said SDK. Additonal options may be provided as keyword
   arguments or a map.

   Options:

   | key                | description                                                                                                                     |
   | ------------------ | ------------------------------------------------------------------------------------------------------------------------------- |
   | `::with-open-sse?` | If true, all SSE responses will be wrapped in `d*/with-open-sse`. Defaults to false. Can be configured per response             |
   | `::write-profile`  | Applies a `:d*.sse/write-profile` to all SSE responses. Defaults to the SDK default. Can be configured per response             |
   | `::registries`     | A vector of effect registries. An effect registry is an effect map, a function returning an effect map, or a vector (see below) |
   | `::dispatch`       | An existing dispatch function. If present, `::registries`, `::write-html`, and `::write-json` will be ignored. See [[dispatch]] |
   | `::write-html`     | The html serialization function used for :body and events. Defaults to dev.onionpancakes.chassis.core/html (recommended)        |
   | `::read-json`      | The json function used to deserialize datastar signals. Defaults to a custom parse-fn powered by charred.api/parse-json-fn      |
   | `::write-json`     | The json function used to serialize Clojure structures to json strings. Defaults to charred.api/write-json-str                  |
   | `::html-attrs`     | A map of html attributes that will be provided to any hiccup forms used in the :body key of any response                        |

  Example ring responses for handlers using with-datastar:
  ```clojure
  (require '[datastar.wow :as d*])
  {:body [some-hiccup-form]} ; Server rendered for non-Datastar requests
  {::d*/fx [[::d*/patch-signals {:foo 1 :bar \"baz\"} {::d*/only-if-missing true}]
            [::d*/patch-elements [:h1#demo \"Hello\"]]]} ; send datastar events
  {::d*/fx [[::d*/patch-signals {:foo 1 :bar \"baz\"}]]
   ::d*/connection (get-open-connection-somehow)} ; use an existing connection
  {::d*/fx [[::d*/patch-elements [:h1#demo \"Hello\"]]]
   ::d*/with-open-sse? true} ; close sse connection after sending events
  {:🚀 [[::d*/patch-elements [:h1#demo \"Hello\"] {::d*/patch-mode ::d*/pm-replace}]]} ; enhance fun with the rocket emoji alias
  ```

  The `::registries` option is used to extend a datastar.wow app. Application specific effects, actions, placeholders, and interceptors can be added.
  See the [nexus](https://github.com/cjohansen/nexus) documentation for more information. A registry is an effect map, a zero arity function that returns an effect map
  or a vector containing a function and any args it will be invoked with. This function should return an effect map.

  ```clojure
  (def effect-map
    {::d*/effects
      {::myeffect
        (fn [ctx system arg1]
          (println arg1))}})

  (defn effect-map-fn []
    {::d*/effects
      {::myeffect
        (fn [ctx system arg1]
          (println arg1))}})

  (defn effect-map-fn-1
    [arg1]
    {::d*/effects
      {::othereffect
        (fn [ctx system]
          (println arg1))}})

  (d*/with-datastar ->sse-response {::d*/registries [effect-map effect-map-fn [effect-map-fn-1 \"Mama mia!\"]]})
  ```

  The \"system\" given to nexus.core/dispatch will contain the following keys:

  | key        | description                                       |
  | ---------- | ------------------------------------------------- |
  | `:sse`     | The current SSEGen instance. nil for close events |
  | `:request` | The ring request used to initiate the connection  |

  The following keys will exist on Nexus dispatch data by default:

  | key                | description                                                         |
  | ------------------ | ------------------------------------------------------------------- |
  | `::response`       | The response returned by the handler.                               |
  | `::request`        | The ring request used to initiate the connection                    |
  | `::with-open-sse?` | Whether or not the connection is set to close after events are sent |

  Nexus actions (pure functions) will receive signals as their state value"
  {:malli/schema schema/=>with-datastar}
  ([->sse-response]
   (mw/with-datastar ->sse-response {}))
  ([->sse-response & opts]
   (mw/with-datastar ->sse-response opts)))

(defn dispatch
  "Create a dispatch function. Useful for \"out of band\" dispatch or creating the middleware dispatch ahead of time.
   The returned dispatch function has the following signature:

  ```clojure
  (fn dispatch
    ([dispatch-data fx])
    ([system dispatch-data fx]))
  ```

  Please note that dispatching datastar.wow's bundled effects/actions requires system to be a map containing an `:sse` key and a `:request` key
  with an instance of SSEGen and a ring request respectively."
  [opts]
  (mw/create-dispatch opts))

;;; Official SDK constants re-exported here for convenience

(def-clone CDN-url d*/CDN-url)
(def-clone CDN-map-url d*/CDN-map-url)
(def-clone id d*/id)
(def-clone retry-duration d*/retry-duration)
(def-clone selector d*/selector)
(def-clone patch-mode d*/patch-mode)
(def-clone use-view-transition d*/use-view-transition)
(def-clone only-if-missing d*/only-if-missing)
(def-clone auto-remove d*/auto-remove)
(def-clone attributes d*/attributes)
(def-clone pm-outer d*/pm-outer)
(def-clone pm-inner d*/pm-inner)
(def-clone pm-remove d*/pm-remove)
(def-clone pm-prepend d*/pm-prepend)
(def-clone pm-append d*/pm-append)
(def-clone pm-before d*/pm-before)
(def-clone pm-after d*/pm-after)
(def-clone pm-replace d*/pm-replace)

;;; Action helpers

(def-clone sse-get d*/sse-get)
(def-clone sse-post d*/sse-post)
(def-clone sse-put d*/sse-put)
(def-clone sse-patch d*/sse-patch)
(def-clone sse-delete d*/sse-delete)
