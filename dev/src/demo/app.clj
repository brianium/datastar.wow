(ns demo.app
  (:require [datastar.wow :as d*]
            [demo.server :as demo.server]
            [dev.onionpancakes.chassis.core :as c]
            [dev.onionpancakes.chassis.compiler :as cc]
            [integrant.core :as ig]
            [reitit.coercion.malli :as co]
            [reitit.core :as r]
            [reitit.ring :as rr]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.parameters :as rmp]
            [starfederation.datastar.clojure.adapter.http-kit :as hk]
            [starfederation.datastar.clojure.adapter.http-kit2 :as hk2]
            [starfederation.datastar.clojure.adapter.ring :as dr]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Demo components for integrant
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn with-datastar
  "datastar.wow/with-datastar component so we can tweak config in the demo app. If using
   :httpkit2 be sure to include the start-responding-middleware from the offical sdk"
  [{:keys [type]
    :or {type :httpkit}
    :as deps}]
  (println "creating with-datastar middleware:" (pr-str deps))
  (d*/with-datastar (case type
                      :httpkit  hk/->sse-response
                      :httpkit2 hk2/->sse-response
                      :jetty    dr/->sse-response) (dissoc deps :type)))

(defn handler
  [{:keys [router middleware]}]
  (rr/ring-handler
   router
   (rr/routes
    (rr/create-resource-handler {:path "/"})
    (rr/create-default-handler))
   {:middleware middleware}))

(defn router
  [{:keys [routes middleware]
    :or   {middleware []}}]
  (let [middleware (into [rmp/parameters-middleware
                          rrc/coerce-request-middleware] middleware)]
    (rr/router routes {:data {:coercion co/coercion
                              :middleware middleware}})))

(defn server
  [{:keys [type]
    :or {type :httpkit}
    :as deps}]
  (println "creating server:" (pr-str deps))
  (case type
    :httpkit (demo.server/httpkit-server (dissoc deps :type))
    :jetty   (demo.server/jetty-server (dissoc deps :type))))

(defn state
  [initial-state]
  (atom initial-state))

(def initial-state
  {:first-name "John"
   :last-name  "Doe"
   :email "joe@blow.com"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handlers and routes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod c/resolve-alias ::user
  [_ {:keys [id first-name last-name email]} _]
  (cc/compile
   [:div {:id id}
    [:p (str "First Name: " first-name)]
    [:p (str "Last Name: " last-name)]
    [:p (str "Email: " email)]
    [:div {:class ["my-2 flex gap-x-2"] :role "group"}
     [:button {:class ["bg-green-500 p-2 cursor-pointer"]
               :data-indicator-_fetching true
               :data-attr-disabled "$_fetching"
               :data-on-click (d*/sse-get "/edit")}
      "Edit"]
     [:button {:class ["bg-orange-500 p-2 cursor-pointer"]
               :data-indicator-_fetching true
               :data-attr-disabled "$_fetching"
               :data-on-click (d*/sse-patch "/reset")}
      "Reset"]]]))

(defmethod c/resolve-alias ::user-form
  [_ {:keys [id first-name last-name email]} _]
  (cc/compile
   [:div {:id id}
    [:div {:class ["flex flex-col w-40 gap-y-2"]}
     [:label.flex.flex-col "First Name"
      [:input.bg-black {:type "text"
                        :data-bind-first-name__case.kebab true
                        :data-attr-disabled "$_fetching"
                        :value first-name}]]
     [:label.flex.flex-col "Last Name"
      [:input.bg-black {:type "text"
                        :data-bind-last-name__case.kebab true
                        :data-attr-disabled "$_fetching"
                        :value last-name}]]
     [:label.flex.flex-col "Email"
      [:input.bg-black {:type "email"
                        :data-bind-email__case.kebab true
                        :data-attr-disabled "$_fetching"
                        :value email}]]]
    [:div {:class ["my-2 flex gap-x-2"] :role "group"}
     [:button {:class ["bg-green-500 p-2 cursor-pointer"]
               :data-indicator-_fetching true
               :data-attr-disabled "$_fetching"
               :data-on-click (d*/sse-put "/")}
      "Save"]
     [:button {:class ["bg-red-500 p-2 cursor-pointer"]
               :data-indicator-_fetching true
               :data-attr-disabled "$_fetching"
               :data-on-click (d*/sse-get "/cancel")}
      "Cancel"]]]))

(defn page
  [state]
  (cc/compile
   [c/doctype-html5
    [:html {:class "bg-slate-900 text-white text-lg" :lang "en"}
     [:head
      [:meta {:charset "UTF-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
      [:title "Datastar! Wow!"]
      [:script {:src d*/CDN-url :type "module"}]
      [:script {:src "https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4"}]]
     [:body {:class ["p-8"]}
      [:div {:class ["mx-auto max-w-7xl sm:px-6 lg:px-8"]}
       [:fieldset
        [:legend "Demo"]
        [:div
         [::user#demo state]]]]]]]))

(defn index
  [{:keys [request-method signals]
    {{:keys [state]} :data} ::r/match}]
  (case request-method
    :get {:body (page @state)}
    :put {:🚀 [[::d*/patch-elements [::user#demo (reset! state signals)]]]}))

(defn cancel
  [{{{:keys [state]} :data} ::r/match}]
  {::d*/with-open-sse? true ;;; indicators won't work unless the connection closes
   :🚀 [[::d*/patch-elements [::user#demo @state]]
        [::d*/patch-signals  @state]]})

(defn edit
  [{{{:keys [state]} :data} ::r/match}]
  {::d*/with-open-sse? true ;;; indicators won't work unless the connection closes
   :🚀 [[::d*/patch-elements [::user-form#demo @state]]]})

(defn reset
  [{{{:keys [state]} :data} ::r/match}]
  {::d*/with-open-sse? true
   :🚀 [[::d*/patch-elements [::user#demo (reset! state initial-state)]]
        [::d*/patch-signals  initial-state]]})

(def routes
  ["" {:state (ig/ref ::state)}
   ["/" {:name ::index
         :get  index
         :put  index}]
   ["/cancel" {:name ::cancel
               :get cancel}]
   ["/edit" {:name ::edit
             :get edit}]
   ["/reset" {:name ::reset
              :patch reset}]])
