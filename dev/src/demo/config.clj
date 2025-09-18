(ns demo.config
  (:require [datastar.wow :as d*]
            [demo.app :as app]
            [integrant.core :as ig]
            [starfederation.datastar.clojure.adapter.http-kit2 :refer [start-responding-middleware]]
            [starfederation.datastar.clojure.brotli :as brotli]))

(defmethod ig/init-key ::constant [_ x] x)
(derive ::initial-state ::constant)

(defmethod ig/halt-key! ::app/server [_ stop-fn]
  (stop-fn))

(def adapter
  "Change this to :jetty, :httpkit, or :httpkit2 then call (clj-reload.core/reload).
   Useful for testing with different adapters"
  :httpkit)

(def write-profile
  "Set this to nil if you want to test without a write profile"
  (brotli/->brotli-profile))

(def config
  {::app/with-datastar (cond-> {:type adapter}
                         (some? write-profile) (assoc ::d*/write-profile write-profile))
   ::app/router        {:routes     app/routes
                        :middleware [(ig/ref ::app/with-datastar)]}
   ::app/handler       {:router     (ig/ref ::app/router)
                        :middleware (cond-> []
                                      (= adapter :httpkit2) (conj start-responding-middleware))}
   ::app/server        {:handler (ig/ref ::app/handler)
                        :type (condp = adapter
                                :httpkit  adapter
                                :httpkit2 :httpkit
                                :jetty    adapter)}
   ::app/state         app/initial-state})
