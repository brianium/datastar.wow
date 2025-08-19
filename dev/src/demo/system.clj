(ns demo.system
  (:require [integrant.core :as ig]))

(def ^:dynamic *system*
  "The running system"
  nil)

(defn start
  ([config]
   (start config nil))
  ([config ks]
   (let [system (-> config ig/expand (ig/init (or ks (keys config))))]
     (alter-var-root #'*system* (constantly system))
     ::started)))

(defn stop []
  (when (some? *system*)
    (ig/halt! *system*)
    (alter-var-root #'*system* (constantly nil))
    ::stopped))
