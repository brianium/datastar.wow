# Change Log

## [1.0.0-RC11-wow0] - 2026-07-07

### Changed

- Update Datastar Clojure SDK (and the brotli, http-kit, and ring adapters) to version 1.0.0-RC11
- Update Nexus to 2026.06.4

## [1.0.0-RC4-wow0] - 2025-11-04

### Changed

- Update Datastar Clojure SDK to version 1.0.0-RC4
- Update Nexus to 2025.10.2
- Update demo app to use new datastar attribute syntax - i.e `data-on:click` instead of `data-on-click`

## [1.0.0-RC3-wow1] - 2025-09-20

### Breaking Changes

Removed `:datastar.wow/update-nexus` and replaced it with `:datastar.wow/registries`. `:datastar.wow/update-nexus` will now be ignored. Adds `dispatch`
function for creating a dispatch function for "out of band" dispatches.

### Changed

Effect + Nexus extension is now powered by registries. A registry IS a Nexus map, but with `:datastar.wow` namespaced keys. Keys are aliased
not to aura farm, but to reduce cognitive load (single require is a good thing).

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

;;; No more ::d*/update-nexus
(d*/with-datastar ->sse-response {::d*/registries [effect-map effect-map-fn [effect-map-fn-1 "Mama mia!"]]})
```

The vector syntax is particularly useful for scenarios using a component system like [integrant](https://github.com/weavejester/integrant).

``` clojure
(require '[integrant.core :as ig])

(def config
  {::datastar-config
    {::d*/registries [effect-map effect-fn [sql-effect (ig/ref ::database-fn)]]}})
```

### Added

Adds a `dispatch` function that takes a subset of `with-datstar` options: `::d*/registries`, `::d*/write-json`, and `::d*/write-html`. This
dispatch can be passed to `with-datastar`.

```clojure
(def my-dispatch (d*/dispatch {::d*/registries [my-app-effects]}))

(d*/with-datastar ->sse-response {::d*/dispatch my-dispatch})
```

If a dispatch function is given, `::d*/registries`, `::d*/write-json`, and `::d*/write-html` options given to `with-datastar` will be ignored.
