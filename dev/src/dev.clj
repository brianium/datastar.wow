(ns dev
  (:require [demo.config :refer [config]]
            [demo.system :as system]
            [malli.dev :as malli.dev]))

(defn start []
  (malli.dev/start!)
  (system/start config))

(defn stop []
  (malli.dev/stop!)
  (system/stop))

(defn before-ns-unload []
  (stop))

(start)
