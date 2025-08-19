(ns demo.server
  (:require [org.httpkit.server :as hk-server]
            [ring.adapter.jetty :as jetty]))

(defn httpkit-server
  [{:keys [handler]}]
  (hk-server/run-server handler {:port 3000}))

(defn jetty-server
  [{:keys [handler]}]
  (let [server (jetty/run-jetty handler {:port 3000 :join? false})]
    (fn []
      (.stop server))))
