(ns dev
  (:require [malli.dev :as malli.dev]))

(defn before-ns-unload []
  (malli.dev/stop!))

(malli.dev/start!)
