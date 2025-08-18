(ns datastar.wow.events
  "datastar.wow events are identical to the ones in the official sdk but
   they support configurable html and json serialization"
  (:require [starfederation.datastar.clojure.api :as d*]))

(defn patch-elements!
  "Same as d*/patch-elements! except it expects a hiccup form"
  ([serialize-html sse h]
   (patch-elements! serialize-html sse h {}))
  ([serialize-html sse h opts]
   (d*/patch-elements! sse (serialize-html h) opts)))

(defn patch-elements-seq!
  "Same as d*/patch-elements-seq! except hiccup forms are expected"
  ([serialize-html sse frags]
   (patch-elements-seq! serialize-html sse frags {}))
  ([serialize-html sse frags opts]
   (let [rendered (mapv serialize-html frags)]
     (d*/patch-elements-seq! sse rendered opts))))

(defn patch-signals!
  "Same as d*/patch-signals! but json encodes the given signals structure"
  ([write-json sse signals]
   (patch-signals! write-json sse signals {}))
  ([write-json sse signals opts]
   (d*/patch-signals! sse (write-json signals) opts)))

