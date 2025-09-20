# Change Log

## [1.0.0-RC3-wow1] - 2025-09-20

### Breaking Changes

Removed `:datastar.wow/update-nexus` and replaced it with `:datastar.wow/registries`. `:datastar.wow/update-nexus` will now be ignored.

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

