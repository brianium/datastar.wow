(ns demo.server
  (:require [org.httpkit.server :as hk-server]))

(defn httpkit-server
  "http-kit powered server. if environment is :development - will attach
   a web socket handler for reload messaging"
  [{:keys [handler]}]
  (hk-server/run-server handler {:port 3000}))
