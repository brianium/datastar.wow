(ns demo.config
  (:require [demo.app :as app]
            [integrant.core :as ig]))

(defmethod ig/init-key ::constant [_ x] x)
(derive ::initial-state ::constant)

(defmethod ig/halt-key! ::app/server [_ stop-fn]
  (stop-fn))

(def config
  {::app/with-datastar {:type :httpkit}
   ::app/router        {:routes     app/routes
                        :middleware [(ig/ref ::app/with-datastar)]}
   ::app/handler       {:router     (ig/ref ::app/router)
                        ;;; include starfederation.datastar.clojure.adapter.http-kit2/start-responding-middleware if using http-kit2 adapter
                        :middleware []} 
   ::app/server        {:handler (ig/ref ::app/handler)
                        :type :httpkit}
   ::app/state         app/initial-state})
