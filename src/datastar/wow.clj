(ns datastar.wow
  (:require [datastar.wow.middleware :as mw]
            [datastar.wow.schema :as schema]
            [starfederation.datastar.clojure.api :as d*]
            [starfederation.datastar.clojure.utils :refer [def-clone]]))

(defn with-datastar
  "The Power Is Yours"
  {:malli/schema schema/=>with-datastar}
  ([->sse-response]
   (mw/with-datastar ->sse-response {}))
  ([->sse-response & opts]
   (mw/with-datastar ->sse-response opts)))

;;; Official SDK constants rexported here for convenience

(def-clone CDN-url d*/CDN-url)
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
