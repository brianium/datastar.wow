<p align="center">
  <br><br>
  <img src="datastar.wow.png" />
  <br><br>
</p>


[![Clojars Project](https://img.shields.io/clojars/v/com.github.brianium/datastar.wow.svg)](https://clojars.org/com.github.brianium/datastar.wow)
[![cljdoc badge](https://cljdoc.org/badge/com.github.brianium/datastar.wow)](https://cljdoc.org/d/com.github.brianium/datastar.wow)


A more declarative and data-oriented way to build [Datastar](https://data-star.dev/) applications with Clojure. Built upon the [official Datastar Clojure SDK](https://github.com/starfederation/datastar-clojure) and [Nexus](https://github.com/cjohansen/nexus). Bring your own JSON and Hiccup library or use the very sensible (recommended even!) defaults powered by [Charred](https://github.com/cnuernber/charred) and [Chassis](https://github.com/onionpancakes/chassis).

## Table of Contents

- [Quick Example](#quick-example)
- [Installation](#installation)
- [Usage](#usage)
- [Effects](#effects)
- [Options](#with-datastar-options)
- [Responses](#responses)
- [Extending](#extending)
- [Dispatch](#dispatch)
- [Demo](#demo)
- [CHANGELOG](CHANGELOG.md)

## Quick Example

``` clojure
(ns myapp.web
  (:require [datastar.wow :as d*]
            [myapp.agents :as agents]
            [reitit.core :as r]))

(defn update-context
  "Update the text content of a context entry"
  [req]
  (let [{{{:keys [agent-store]} :data} ::r/match
         {{:keys [entry-id]} :path} :parameters
         {:keys [text-content]} :signals} req
        agent    (agents/fetch-agent agent-store entry-id {:key :entry})
        updated  (agents/update-content! agent entry-id text-content)]
    {:🚀 [[::d*/patch-elements [html/context-entry {:entry updated :tag (:tag agent)}]] ;;; ::d*/fx if you hate rockets
          [::d*/patch-signals  {:editing false}]
          [::d*/execute-script "hljs.highlightAll()"]]}))
```

## Installation

datastar.wow includes and tracks with the current version of the [official SDK](https://clojars.org/dev.data-star.clojure/sdk) - i.e you don't have to install that separately (just install datastar.wow instead). A compatibile adapter is still required:

| library       | deps coordinate                                                                                                                                         |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| http-kit      | [![Clojars Project](https://img.shields.io/clojars/v/dev.data-star.clojure/http-kit.svg)](https://clojars.org/dev.data-star.clojure/http-kit)           |
| ring          | [![Clojars Project](https://img.shields.io/clojars/v/dev.data-star.clojure/ring.svg)](https://clojars.org/dev.data-star.clojure/ring)                   |

Other tools provided by the official Clojure SDK may be useful as well (such as Brotli compression). See the [official installation instructions](https://github.com/starfederation/datastar-clojure?tab=readme-ov-file#installation) for more info.

## Usage

The `with-datastar` middleware function should be applied to your Ring handler.

Handlers leveraging this middleware benefit from the following:

- All Datastar request maps will have a `:signals` key pointing to a (keywordized) Clojure map of signals
- Response maps specify Datastar events as a vector of data-oriented effects
- The `:body` key of a response map can accept hiccup forms directly

The middleware function MUST be created with an `->sse-response` function from a valid SDK adapter (see [Installation](#installation)):

``` clojure
(require '[datastar.wow :as d*]
         '[starfederation.datastar.clojure.adapter.http-kit :as hk])

(def with-datastar
  (d*/with-datastar hk/->sse-response))

(def app
  (-> handler
      with-datastar))
```

That's it! The default options should cover most use cases, but [options](#with-datastar-options) can be configured in order to bring your own html/json serialization, control default close behavior, or add [write profiles](https://github.com/starfederation/datastar-clojure/blob/main/doc/Write-profiles.md).

## Effects

Ring handlers should return response maps containing a `:datastar.wow/fx` key containing a vector of effects/actions a-la [Nexus](https://github.com/cjohansen/nexus). The `:🚀` key can be used instead of `:datastar.wow/fx` if you like to party. A response map might look like the following:

``` clojure
 {:🚀 [[::d*/patch-elements [html/context-entry {:entry updated :tag (:tag cog)}]]
       [::d*/patch-signals  {:editing false}]
       [::d*/execute-script "hljs.highlightAll()"]]}
```

`datastar.wow` includes default actions and effects that cover all the events present in the official SDK (`patch-elements!`, `patch-elements-seq!`, `patch-signals!`, and `execute-script!`) with an expectation of data structures (hiccup, Clojure structures) as opposed to strings. 

### `:datastar.wow/patch-elements`

Patches one or more elements in the DOM. By default, Datastar morphs elements by matching top-level elements based on their ID.

``` clojure
{:🚀 [[::d*/patch-elements [:h1#demo "Hello"]]
      [::d*/patch-elements [:h2#other "Hello"] {d*/patch-mode d*/pm-replace}]]} ;;; Datastar options supported
```

All patch options from the official SDK are supported in the trailing options map, including `d*/element-ns` (for patching into a non-HTML namespace) and `d*/view-transition-selector`:

``` clojure
{:🚀 [[::d*/patch-elements [:circle#demo] {d*/element-ns d*/ns-svg}]]} ;;; ns-html (default), ns-svg, ns-mathml
```

### `:datastar.wow/patch-elements-seq`

Identical to `:datastar.wow/patch-elements` except it takes a sequence of elements to patch.

``` clojure
{:🚀 [[::d*/patch-elements-seq [[:h1#demo "Hello"] [:h2#other "Hello"]]]]}
```

### `:datastar.wow/patch-signals`

Patches signals into the existing signals on the page.

``` clojure
{:🚀 [[::d*/patch-signals {:first-name "Turjan"}]]}
```

### `:datastar.wow/execute-script`

Construct a HTML script tag using `script-text` as its content.

``` clojure
{:🚀 [[::d*/execute-script "alert('Datastar! Wow!')"]]}
```

### `:datastar.wow/close-sse`

Makes more sense when leveraging [asynchronous effects](https://github.com/cjohansen/nexus?tab=readme-ov-file#asynchronous-effects). Typically makes more sense to use the `::d*/with-open-sse?` option.

``` clojure
{:🚀 [[::d*/close-sse]]}
```

### `:datastar.wow/sse-closed`

Not used explicitly. This is dispatched when an SSE connection is closed. Useful when [extending](#extending) `with-datastar`.

### `:datastar.wow/connection`

``` clojure
{:🚀 [[::d*/connection]]}
```

Not used explicitly. This is dispatched when determining if an existing connection should be used. If this effect has a `:datastar.wow/connection` key present in `dispatch-data`, then that
value will be used as the SSE connection. Intended mainly for use by extensions providing functionality via interceptors.

The connection used by dispatch will use the following priority order:

1. A `:datastar.wow/connection` key on the response returned by a handler
2. A `:datastar.wow/connection` key found in `dispatch-data` (via interceptor)
3. A new connection will be created and used

## `with-datastar` Options

The second argument to `with-datastar` is an options map that can be used to customize and configure.

| key                   | description                                                                                                                                       |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `::d*/with-open-sse?` | If true, all SSE responses will be wrapped in `d*/with-open-sse`. Defaults to false. Can be configured per response                               |
| `::d*/write-profile`  | Applies a `:d*.sse/write-profile` to all SSE responses. Defaults to the SDK default. Can be configured per response                               |
| `::d*/registries  `   | A vector of effect registries. An effect registry is an effect map, a function returning an effect map, or a vector (see [extending](#extending)) |
| `::d*/dispatch`       | An existing dispatch function. If present, `::d*/registries`, `d*::write-html`, and `::d*/write-json` will be ignored. See [dispatch](#dispatch)  |
| `::d*/write-html`     | The html serialization function used for :body and events. Defaults to dev.onionpancakes.chassis.core/html (recommended)                          |
| `::d*/read-json`      | The json function used to deserialize datastar signals. Defaults to a custom parse-fn powered by charred.api/parse-json-fn                        |
| `::d*/write-json`     | The json function used to serialize Clojure structures to json strings. Defaults to charred.api/write-json-str                                    |
| `::d*/html-attrs`     | A map of html attributes that will be provided to any hiccup forms used in the :body key of any response                                          |

Not that `::d*/with-open-sse?` and `::d*/write-profile` keys can be provided on a per response basis.

## Responses

The response map returned by a ring handler supports a few extra keys to tailor the outcome.

``` clojure
 {:d*/write-profile (brotli/->brotli-profile) ;;; Each response can specify a write profile
  :d*/with-open-sse? false ;;; Each response can specify if the connection should be closed after events are sent or left open
  :d*/connection (fetch-existing-connection-somehow) ;;; An existing connection can be given
  :🚀 [[::d*/patch-elements [html/context-entry {:entry updated :tag (:tag cog)}]]
       [::d*/patch-signals  {:editing false}]
       [::d*/execute-script "hljs.highlightAll()"]]} ;;; Effects
```

## Extending

The extension point for `datastar.wow` is via the `::d*/registries` option. It opens a world of possibilities for things like logging, observability, error capture, connection storage, domain specific effects/actions, placeholders, etc. The [Nexus docs](https://github.com/cjohansen/nexus) are very good, and should give a good tour of what is possible.

A registry is an effect map, a zero arity function that returns an effect map or a vector containing a function and any args it will be invoked with. This function should return an effect map.

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

The vector syntax is particularly useful for scenarios using a component system like [integrant](https://github.com/weavejester/integrant).

``` clojure
(require '[integrant.core :as ig])

(def config
  {::datastar-config
    {::d*/registries [effect-map effect-fn [sql-effect (ig/ref ::database-fn)]]}})
```

### Example: Pure actions with access to signals as state

State in a Datastar application is signals. Any action added to the `datastar.wow` nexus will be able to exploit this fact to write pure actions leveraging this state.

``` clojure
(defn uc-signals
  "Convert string signals to uppercased variants for some reason"
  [signals]
  [[::d*/patch-signals
    (reduce-kv
     (fn [m k v]
       (assoc m k (string/upper-case v))) {} signals)]])
	   
(def registry
  {::d*/actions
    {::uc-signals uc-signals}})
	
(d*/with-datastar ->sse-response {::d*/registries [registry]})
``` 

### Example: Using placeholders for asynchronous effects

Sometimes we don't have data available to us when we first describe our set of effects. Nexus has a concept of [placeholders](https://github.com/cjohansen/nexus?tab=readme-ov-file#placeholder) that allow us to put a stub in for a value that will be available later. This example demonstrates a placeholder that supports streaming partial images as they become available.

``` clojure
(defn partial-image
  "Get a partial image from a job queue event. Note: this placeholder is used in a streaming
  context, and will be called multiple times. As such if there is no relevant event data, we return
  the placeholder to be tried again when event data is ready"
  [data]
  (let [event (:machina-ars.web.effects.images/event data)]
    (if (and (some? event) (:base64 event) (:mime-type event))
      (select-keys event [:base64 :mime-type])
      [::partial-image])))
	  
;;; Our ring response can then take advantage of it

{:🚀 [[::d*/patch-signals {:generations   {job-key job}
                           :previewHidden false}]
      [::fx/stream-image  {:job         job
                           :on-complete [::fx/merge-metadata agent {:jobs {job-key nil}}]
                           :on-start    [::fx/merge-metadata agent {:jobs {job-key job}}]
                           :on-partial  [::fx/save-image agent entry-id [::fx.placeholders/partial-image]]}]]} ;;; Datastar! Wow!

(def registry
  {::d*/placeholders
    {::partial-image partial-image}})
	
(d*/with-datastar ->sse-response {::d*/registries [registry]})
```

### Example: Persisting connections via interceptor

This example is from an application that streams core.async channel values to the browser. The original ring handler response is available as Nexus
dispatch data, and so we use that fact to create a unique id from the session and a user provided id. The response also contains an "abort channel" that allows us to stop reading from the channel when an SSE connection closes.

``` clojure
(defn manage-connections
  "An interceptor that manages connections that aren't closed automatically. A connection will only be stored if:
   - There is a session id
   - There is a connection id provided in the dispatch data (typically done in the response map of the handler)
   - The connection is not set to auto close - i.e it was not opened using d*/with-open-sse

  Storage is cleared in response to an sse-closed event. *abort-chs is an atom for storing abort channels persistent across dispatches
  Note: This app supports an optional ::abort-ch key on any response. If provided, it will be signaled during close"
  [store *abort-chs]
  {::d*/interceptors
   [{:id ::manage-connections
     :before-dispatch
     (fn [{:keys [system dispatch-data] :as ctx}]
       (let [{:keys [request sse]} system
             store? (not (::d*/with-open-sse? dispatch-data))
             session-id (get-in request [:session :session-id])
             conn-id    (get-in dispatch-data [::d*/response ::conn-id])] ;;; the ring handler response is available as dispatch data
         (when (and store? session-id conn-id (some? sse)) ;;; sse will be nil on close effects
           (conns/store! store [session-id conn-id] sse)))
       ctx)
     :before-effect ;;; aggregate abort channels
     (fn [{:keys [dispatch-data] :as ctx}]
       (let [{{::keys [abort-ch]} ::d*/response} dispatch-data]
         (when (some? abort-ch)
           (swap! *abort-chs conj abort-ch))
         ctx))
     :after-effect
     (fn [{:keys [effect system dispatch-data] :as ctx}]
       (let [{{{:keys [session-id]} :session} :request} system]
         (when (and effect (= ::d*/sse-closed (first effect)))
           (doseq [abort-ch @*abort-chs]
             (swap! *abort-chs disj abort-ch)
             (async/put! abort-ch ::yeet))
           (when-some [conn-id (get-in dispatch-data [::d*/response ::conn-id])]
             (conns/purge! store [session-id conn-id]))))
       ctx)}]})
	   
(d*/with-datastar ->sse-response {::d*/registries [[manage-connections (ig/ref ::store) (ig/ref ::abort-chs)]]})
```

The Nexus "system" will have the following keys:

| key        | description                                       |
| ---------- | ------------------------------------------------- |
| `:sse`     | The current SSEGen instance. nil for close events |
| `:request` | The ring request used to initiate the connection  |

The following keys will exist on Nexus dispatch data by default:

| key                   | description                                                         |
| --------------------- | ------------------------------------------------------------------- |
| `::d*/response`       | The response returned by the handler.                               |
| `::d*/request`        | The ring request used to initiate the connection                    |
| `::d*/with-open-sse?` | Whether or not the connection is set to close after events are sent |

## Dispatch

The `datastar.wow/dispatch` function can be used to create a dispatch function supporting all datastar.wow effects. It takes a subset of arguments that `with-datastar` takes:

| key                   | description                                                                                                                                       |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `::d*/registries  `   | A vector of effect registries. An effect registry is an effect map, a function returning an effect map, or a vector (see [extending](#extending)) |
| `::d*/write-html`     | The html serialization function used for :body and events. Defaults to dev.onionpancakes.chassis.core/html (recommended)                          |
| `::d*/write-json`     | The json function used to serialize Clojure structures to json strings. Defaults to charred.api/write-json-str                                    |

This is useful for creating a dispatch function that can be used "out of band" (i.e dispatch not bound to a particular sse connection or ring request). The returned function
will have the following signature.

```clojure
(fn dispatch
  ([dispatch-data fx])
  ([system dispatch-data fx]))
```

If dispatching effects reliant on an sse connection, make sure the `system` map has an `:sse` connection and a ring `:request`.

``` clojure
(dispatch {:sse some-conn :request ring-request} dispatch-data [[::effect arg1 arg2]])
```

`with-datastar` generally provides the following dispatch data:

``` clojure
{::d*/response       ring-response
 ::d*/request        ring-request
 ::d*/with-open-sse? bool}
```

Be aware that userland interceptors and effects may rely on those values being present.

### Use an existing dispatch function in with-datastar

A dispatch function created by `datastar.wow/dispatch` can be given to `with-datastar`. If present, `:datastar.wow/registries`, `:datastar.wow/write-html` and `:datastar.wow/write-json` options
will be ignored.

``` clojure
(def dispatch (d*/dispatch {::d*/registries [myappregistry]}))

(def with-datastar (d*/with-datastar ->sse-response {::d*/dispatch dispatch}))
```

## Demo

See the [demo](dev/src/demo) namespace for a demo reitit application using the `with-datastar` middleware function. The [tests](test/src/datastar/wow_test.clj) are also a great resource for seeing things in action.

``` bash
$ clj -A:dev
user => (dev) ;;; after this hit localhost:3000
```
