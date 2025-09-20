(ns datastar.wow.middleware
  "Implementation for the with-datastar middleware"
  (:require [charred.api :as json]
            [clojure.set :as set]
            [datastar.wow.events :as events]
            [dev.onionpancakes.chassis.core :as c]
            [nexus.core :as nexus]
            [starfederation.datastar.clojure.api :as d*]))

(def default-read-json (json/parse-json-fn {:async? false :bufsize 1024 :key-fn keyword}))

(defn- with-signals
  "Parse signals and place on request as a map"
  ([]
   (with-signals default-read-json))
  ([read-json]
   (fn [handler]
     (fn [request]
       (if (d*/datastar-request? request)
         (let [raw-signals (d*/get-signals request)]
           (if (some? raw-signals)
             (handler (assoc request :signals (read-json raw-signals)))
             (handler request)))
         (handler request))))))

(defn- datastar-response?
  [response]
  (and (map? response)
       (contains? response :datastar.wow/fx)))

(defn- create-send
  [write-json write-html]
  (fn [_ {:keys [sse]} & [action payload ?opts]]
    (let [args (if (some? ?opts) [payload ?opts] [payload])]
      (case action
        :datastar.wow/patch-elements     (apply events/patch-elements! write-html sse args)
        :datastar.wow/patch-elements-seq (apply events/patch-elements-seq! write-html sse args)
        :datastar.wow/patch-signals      (apply events/patch-signals! write-json sse args)
        :datastar.wow/execute-script     (apply d*/execute-script! sse args)))))

(def ^:private default-send
  "The default send function using default json/html serialization/deserialization
   via charred and chassis respectively"
  (create-send json/write-json-str c/html))

(def ^:private default-registry
  "Default effect configuration. All datastar events are described
   as effects and system state is whatever signals can be read from the request"
  {:datastar.wow/system->state
   (fn [{:keys [request]}]
     (:signals request))

   :datastar.wow/effects
   {:datastar.wow/connection
    (fn [{{:datastar.wow/keys [connection]} :dispatch-data} _]
      connection)
    :datastar.wow/close-sse
    (fn [_ {:keys [sse]}]
      (d*/close-sse! sse))
    :datastar.wow/sse-closed (constantly nil) ;;; dispatched in on-close callback
    :datastar.wow/send ;;; Route through a single :datastar.wow/send action so we can audit all sends
    default-send}

   :datastar.wow/actions
   {:datastar.wow/patch-elements
    (fn [_ elements & [?opts]]
      [[:datastar.wow/send :datastar.wow/patch-elements elements ?opts]])
    :datastar.wow/patch-elements-seq
    (fn [_ elements-seq & [?opts]]
      [[:datastar.wow/send :datastar.wow/patch-elements-seq elements-seq ?opts]])
    :datastar.wow/patch-signals
    (fn [_ signals & [?opts]]
      [[:datastar.wow/send :datastar.wow/patch-signals signals ?opts]])
    :datastar.wow/execute-script
    (fn [_ script-content & [?opts]]
      [[:datastar.wow/send :datastar.wow/execute-script script-content ?opts]])}})

(defn- get-connection
  "The connection used by dispatch. Order of priority is:
   - 1. A :datastar.wow/connection key on the response map returned by a handler
   - 2. A :datastar.wow/connection key found in nexus dispatch-data
   - 3. A new sse-gen created in sse-response"
  [dispatch request dispatch-data]
  (if-some [connection (get-in dispatch-data [:datastar.wow/response :datastar.wow/connection])]
    connection
    (let [dispatch-result (dispatch {:sse nil :request request} dispatch-data [[:datastar.wow/connection]])]
      (some->
       dispatch-result
       (:results)
       (first)
       (:res)))))

(def ^:private nexus-aliases
  {:datastar.wow/system->state :nexus/system->state
   :datastar.wow/effects :nexus/effects
   :datastar.wow/placeholders :nexus/placeholders
   :datastar.wow/actions :nexus/actions
   :datastar.wow/interceptors :nexus/interceptors})

(defn- merge-registry
  [acc m]
  (reduce-kv
   (fn [r k v]
     (if-some [nk (nexus-aliases k)]
       (update r nk (condp = nk
                      :nexus/interceptors (fnil into [])
                      :nexus/system->state (constantly v)
                      merge) v)
       r)) acc m))

(defn- create-registry
  "Create a nexus registry for all effects, actions, placeholders, and interceptors. Supports
   overriding json and html serialization"
  [registry-specs {:datastar.wow/keys [write-json write-html]}]
  (let [nexus (reduce (fn [registry spec]
                        (let [r (cond
                                  (map? spec)    spec
                                  (fn? spec)     (spec)
                                  (vector? spec) (apply (first spec) (rest spec))
                                  :else {})]
                          (merge-registry registry r)))
                      {} registry-specs)]
    (cond-> nexus
      (every? some? [write-json write-html])
      (assoc-in [:nexus/effects :datastar.wow/send] (create-send write-json write-html))

      (some? write-json)
      (assoc-in [:nexus/effects :datastar.wow/send] (create-send write-json c/html))

      (some? write-html)
      (assoc-in [:nexus/effects :datastar.wow/send] (create-send json/write-json-str write-html)))))

(defn- sse-response
  [request response opts ->sse-response]
  (let [{:datastar.wow/keys [with-open-sse? dispatch write-profile]} opts
        actions (:datastar.wow/fx response)
        dispatch-data (-> {:datastar.wow/response response :datastar.wow/request request}
                          (assoc :datastar.wow/with-open-sse? (response :datastar.wow/with-open-sse? with-open-sse?)))
        wp            (response :datastar.wow/write-profile write-profile)]
    (if-some [connection (get-connection dispatch request dispatch-data)]
      (do (dispatch {:sse connection :request request} dispatch-data actions)
          {:status 204})
      (->sse-response
       request
       (cond-> {:d*.sse/on-close
                (fn [& _]
                  (dispatch {:sse nil :request request} dispatch-data [[:datastar.wow/sse-closed]]))
                :d*.sse/on-open
                (fn [sse]
                  (let [system  {:sse sse :request request}]
                    (if (:datastar.wow/with-open-sse? dispatch-data)
                      (d*/with-open-sse sse
                        (dispatch system dispatch-data actions))
                      (dispatch system dispatch-data actions))))}
         (some? wp) (assoc :d*.sse/write-profile wp)
         (int? (:status response))  (assoc :status (:status response))
         (map? (:headers response)) (assoc :headers (:headers response)))))))

(def fun-enhancers
  "If we aren't having fun, then what is the point?"
  {:🚀 :datastar.wow/fx})

(defn- with-dispatch
  [opts ->sse-response]
  (fn [handler]
    (fn [request]
      (let [response (set/rename-keys (handler request) fun-enhancers)
            dsr?     (datastar-response? response)]
        (if-not dsr?
          response
          (let [datastar? (d*/datastar-request? request)]
            (if-not datastar?
              (dissoc response :datastar.wow/fx :datastar.wow/connection :datastar.wow/with-open-sse?)
              (sse-response request response opts ->sse-response))))))))

(defn- with-attrs
  [h attrs]
  (if (and (vector? h) (map? attrs) (seq attrs))
    (let [[_ b & _] h]
      (if (map? b)
        (update h 1 merge attrs)
        (vec (concat (subvec h 0 1)
                     [attrs]
                     (subvec h 1)))))
    h))

(defn- with-html
  "hiccup forms in :body will be assumed to be a 200 OK html response. :body is ignored
   if request is a datastar request."
  [write-html attrs]
  (fn [handler]
    (fn [request]
      (let [res (handler request)]
        (if (and (not (d*/datastar-request? request))
                 (map? res)
                 (contains? res :body))
          (-> (merge res {:body    (write-html (with-attrs (:body res) attrs))
                          :headers {"Content-Type" "text/html; charset=utf-8"}})
              (assoc :status (:status res 200))
              (update :headers (fn [h]
                                 (if (map? (:headers res))
                                   (merge h (:headers res))
                                   h))))
          res)))))

(defn with-datastar
  "Give your ring app The Power ™ 🚀"
  [->sse-response {:datastar.wow/keys [html-attrs read-json with-open-sse? registries write-json write-html write-profile]
                   :or {html-attrs     {}
                        read-json      default-read-json
                        registries     []
                        with-open-sse? false}}]
  (let [html     (with-html (or write-html c/html) html-attrs)
        signals  (with-signals read-json)
        registry (create-registry
                  (into [default-registry] registries)
                  {:datastar.wow/write-html     write-html
                   :datastar.wow/write-json     write-json})
        dispatch  (fn [system dispatch-data fx]
                    (nexus/dispatch registry system dispatch-data fx))]
    (comp
     signals
     html
     (with-dispatch
       {:datastar.wow/with-open-sse? with-open-sse?
        :datastar.wow/write-profile  write-profile
        :datastar.wow/dispatch       dispatch} ->sse-response))))
