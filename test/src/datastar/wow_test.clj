(ns datastar.wow-test
  (:require [clojure.string :as string]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [datastar.wow :as d* :refer [with-datastar]]
            [dev.onionpancakes.chassis.core :as c]
            [hiccup2.core :as hiccup]
            [ring.mock.request :as mock]
            [starfederation.datastar.clojure.protocols :as p]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Testing utilities
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype SSEGenerator [*event-log]
  p/SSEGenerator
  (send-event! [_ event-type data-lines opts]
    (let [result [event-type data-lines opts]]
      (swap! *event-log conj result)
      result)) ;;; return the data passed to sse generator for inspection in dispatch results
  (get-lock [_] nil)
  (close-sse! [_]
    (swap! *event-log conj [::close])
    true)
  (sse-gen? [_] true))

(defn test-generator
  ([]
   (test-generator (atom [])))
  ([*event-log]
   (SSEGenerator. *event-log)))

(defn ->sse-response
  [*ref]
  (fn [request opts]
    (reset! *ref {:request request :opts opts})))

(defn handle
  "Given a request, a datastar.wow response, and optional opts, returns a map with the following keys:
  | key       | description |
  |-----------|--------------
  | :request  | The request passed to ->sse-response
  | :opts     | The sse options passed to ->sse-response, including :d*.sse/on-open and :d*.sse/on-close
  | :response | A ring response - only used for testing html responses (as opposed to sse responses)"
  ([req res]
   (handle req res {}))
  ([req res opts]
   (let [*ref     (atom {})
         wd       (with-datastar (->sse-response *ref) opts)
         handler  (wd (constantly res))
         res      (handler req)]
     (assoc  @*ref :response res))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test SSE responses + effects
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest html-response
  (testing "default 200 ok"
    (let [request (mock/request :get "/")
          {:keys [response]} (handle request {:body [:h1 "hello"]})]
      (is (= response {:status  200
                       :body    "<h1>hello</h1>"
                       :headers {"Content-Type" "text/html; charset=utf-8"}}))))
  (testing "user provided status code"
    (let [request (mock/request :get "/")
          {:keys [response]} (handle request {:body [:h1 "Bad Times"] :status 404})]
      (is (= response {:status  404
                       :body    "<h1>Bad Times</h1>"
                       :headers {"Content-Type" "text/html; charset=utf-8"}}))))
  (testing "user provided headers"
    (let [request (mock/request :get "/")
          {:keys [response]} (handle request {:body [:h1 "Bad Times"]
                                              :status 404
                                              :headers {"X-Rad-Header" "verycool"}})]
      (is (= response {:status  404
                       :body    "<h1>Bad Times</h1>"
                       :headers {"Content-Type" "text/html; charset=utf-8"
                                 "X-Rad-Header" "verycool"}})))))

(deftest signal-inclusion
  (let [request (-> (mock/request :post "/")
                    (mock/header "datastar-request" "true")
                    (mock/json-body {:ok true}))
        result  (handle request {::d*/fx [[::d*/patch-elements [:h1#test "hello"]]]})
        {{:keys [signals]} :request} result]
    (is (= {:ok true} signals))))

(deftest patch-elements-effect
  (testing "without any options"
    (let [request  (-> (mock/request :post "/")
                       (mock/header "datastar-request" "true"))
          result    (handle request {::d*/fx [[::d*/patch-elements [:h1#test "hello"]]]})
          {:d*.sse/keys [on-open]} (:opts result)
          {[{:keys [res]}] :results} (on-open (test-generator))]
      (is (= ["datastar-patch-elements" ["elements <h1 id=\"test\">hello</h1>"] #:d*.sse{:id false :retry-duration false}] res))))
  (testing "with options"
    (let [request  (-> (mock/request :post "/")
                       (mock/header "datastar-request" "true"))
          result    (handle request {::d*/fx [[::d*/patch-elements [:h1#test "hello"] {d*/patch-mode d*/pm-append}]]})
          {:d*.sse/keys [on-open]} (:opts result)
          {[{:keys [res]}] :results} (on-open (test-generator))]
      (is (= ["datastar-patch-elements"
              ["mode append" "elements <h1 id=\"test\">hello</h1>"]
              #:d*.sse{:id false :retry-duration false}] res)))))

(deftest patch-elements-seq-effect
  (testing "without any options"
    (let [request  (-> (mock/request :post "/")
                       (mock/header "datastar-request" "true"))
          result    (handle request {::d*/fx [[::d*/patch-elements-seq [[:h1#a "hello"] [:h2#b "goodbye"]]]]})
          {:d*.sse/keys [on-open]} (:opts result)
          {[{:keys [res]}] :results} (on-open (test-generator))]
      (is (= ["datastar-patch-elements"
              ["elements <h1 id=\"a\">hello</h1>"
               "elements <h2 id=\"b\">goodbye</h2>"]
              #:d*.sse{:id false :retry-duration false}] res))))
  (testing "with options"
    (let [request  (-> (mock/request :post "/")
                       (mock/header "datastar-request" "true"))
          result    (handle request {::d*/fx [[::d*/patch-elements-seq [[:h1#a "hello"] [:h2#b "goodbye"]] {d*/patch-mode d*/pm-append}]]})
          {:d*.sse/keys [on-open]} (:opts result)
          {[{:keys [res]}] :results} (on-open (test-generator))]
      (is (= ["datastar-patch-elements"
              ["mode append"
               "elements <h1 id=\"a\">hello</h1>"
               "elements <h2 id=\"b\">goodbye</h2>"]
              #:d*.sse{:id false :retry-duration false}] res)))))

(deftest patch-signals-effect
  (testing "without any options"
    (let [request  (-> (mock/request :post "/")
                       (mock/header "datastar-request" "true"))
          result    (handle request {::d*/fx [[::d*/patch-signals {:fun true}]]})
          {:d*.sse/keys [on-open]} (:opts result)
          {[{:keys [res]}] :results} (on-open (test-generator))]
      (is (= ["datastar-patch-signals"
              ["signals {\"fun\":true}"]
              #:d*.sse{:id false :retry-duration false}] res))))
  (testing "with options"
    (let [request  (-> (mock/request :post "/")
                       (mock/header "datastar-request" "true"))
          result    (handle request {::d*/fx [[::d*/patch-signals {:fun true} {d*/only-if-missing true}]]})
          {:d*.sse/keys [on-open]} (:opts result)
          {[{:keys [res]}] :results} (on-open (test-generator))]
      (is (= ["datastar-patch-signals"
              ["onlyIfMissing true" "signals {\"fun\":true}"]
              #:d*.sse{:id false :retry-duration false}] res)))))

(deftest execute-script-effect
  (testing "without any options"
    (let [request  (-> (mock/request :post "/")
                       (mock/header "datastar-request" "true"))
          result   (handle request {::d*/fx [[::d*/execute-script "alert('Datastar! Wow!')"]]})
          {:d*.sse/keys [on-open]} (:opts result)
          {[{:keys [res]}] :results} (on-open (test-generator))]
      (is (= ["datastar-patch-elements"
              ["selector body"
               "mode append"
               "elements <script data-effect=\"el.remove()\">alert('Datastar! Wow!')</script>"]
              #:d*.sse{:id false :retry-duration false}] res))))
  (testing "with options"
    (let [request  (-> (mock/request :post "/")
                       (mock/header "datastar-request" "true"))
          result   (handle request {::d*/fx [[::d*/execute-script "alert('Datastar! Wow!')" {d*/auto-remove false}]]})
          {:d*.sse/keys [on-open]} (:opts result)
          {[{:keys [res]}] :results} (on-open (test-generator))]
      (is (= ["datastar-patch-elements"
              ["selector body"
               "mode append"
               "elements <script>alert('Datastar! Wow!')</script>"]
              #:d*.sse{:id false :retry-duration false}] res)))))

(deftest multiple-effects
  (let [request  (-> (mock/request :post "/")
                     (mock/header "datastar-request" "true"))
        result   (handle request {::d*/fx [[::d*/patch-elements [:h1#a "hello"]]
                                           [::d*/patch-elements-seq [[:h2#b "goodbye"] [:h3#c "for now"]] {d*/patch-mode d*/pm-replace}]
                                           [::d*/patch-signals {:test true}]
                                           [::d*/execute-script "alert('Datastar! Wow!')"]]})
        {:d*.sse/keys [on-open]} (:opts result)
        *event-log                 (atom [])
        _ (on-open (test-generator *event-log))]
    (is (= 4 (count @*event-log)))
    (is (= [["datastar-patch-elements"
             ["elements <h1 id=\"a\">hello</h1>"]
             #:d*.sse{:id false :retry-duration false}]
            ["datastar-patch-elements"
             ["mode replace"
              "elements <h2 id=\"b\">goodbye</h2>"
              "elements <h3 id=\"c\">for now</h3>"]
             #:d*.sse{:id false :retry-duration false}]
            ["datastar-patch-signals"
             ["signals {\"test\":true}"]
             #:d*.sse{:id false :retry-duration false}]
            ["datastar-patch-elements"
             ["selector body"
              "mode append"
              "elements <script data-effect=\"el.remove()\">alert('Datastar! Wow!')</script>"]
             #:d*.sse{:id false :retry-duration false}]] @*event-log))))

(deftest fun-enhancement
  (let [request  (-> (mock/request :post "/")
                     (mock/header "datastar-request" "true"))
        result    (handle request {:🚀 [[::d*/patch-signals {:fun true}]]})
        {:d*.sse/keys [on-open]} (:opts result)
        {[{:keys [res]}] :results} (on-open (test-generator))]
    (is (= ["datastar-patch-signals"
            ["signals {\"fun\":true}"]
            #:d*.sse{:id false :retry-duration false}] res))))

(deftest close-sse-effect
  (let [*event-log  (atom [])
        request (-> (mock/request :post "/")
                    (mock/header "datastar-request" "true"))
        result  (handle request {::d*/fx [[::d*/patch-elements [:h1#test "hello"]]
                                          [::d*/close-sse]]})
        {:d*.sse/keys [on-open]} (:opts result)
        _ (on-open (test-generator *event-log))]
    (is (= [::close] (last @*event-log)))))

(deftest status-and-headers-in-sse-response
  (let [request (-> (mock/request :post "/")
                    (mock/header "datastar-request" "true"))
        result  (handle request {::d*/fx [[::d*/patch-elements [:h1#test "hello"]]]
                                 :status 204
                                 :headers {"X-Rad-Header" "verycool"}})
        opts    (:opts result)]
    (is (= (select-keys opts [:status :headers])
           {:status 204
            :headers {"X-Rad-Header" "verycool"}}))))

(deftest using-an-existing-connection
  (let [*event-log  (atom [])
        request (-> (mock/request :post "/")
                    (mock/header "datastar-request" "true"))
        gen     (test-generator *event-log)
        {:keys [response]} (handle request {::d*/connection gen
                                            ::d*/fx         [[::d*/patch-elements [:h1#test "hello"]]]})]
    (is (= {:status 204} response))
    (is (= [["datastar-patch-elements"
             ["elements <h1 id=\"test\">hello</h1>"]
             #:d*.sse{:id false :retry-duration false}]] @*event-log))))

(deftest with-open-sse
  (testing "configured in middleware options"
    (let [*event-log  (atom [])
          request (-> (mock/request :post "/")
                      (mock/header "datastar-request" "true"))
          result  (handle request {::d*/fx [[::d*/patch-elements [:h1#test "hello"]]]} {::d*/with-open-sse? true})
          {:d*.sse/keys [on-open]} (:opts result)
          _ (on-open (test-generator *event-log))]
      (is (= [::close] (last @*event-log)))))
  (testing "configured in response"
    (let [*event-log  (atom [])
          request (-> (mock/request :post "/")
                      (mock/header "datastar-request" "true"))
          result  (handle request {::d*/fx [[::d*/patch-elements [:h1#test "hello"]]]
                                   ::d*/with-open-sse? true})
          {:d*.sse/keys [on-open]} (:opts result)
          _ (on-open (test-generator *event-log))]
      (is (= [::close] (last @*event-log)))))
  (testing "ignored when given an existing connection"
    (let [*event-log (atom [])
          request    (-> (mock/request :post "/")
                         (mock/header "datastar-request" "true"))
          _  (handle request {::d*/connection (test-generator *event-log)
                              ::d*/fx [[::d*/patch-elements [:h1#test "hello"]]]
                              ::d*/with-open-sse? true})]
      (is (not= [::close] (last @*event-log))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Middleware options
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest injected-html-attributes
  (testing "hiccup form with existing attributes"
    (let [request (mock/request :get "/")
          {:keys [response]} (handle request {:body [:h1 {:class "cool"} "hello"]} {::d*/html-attrs {:data-cool "very"}})]
      (is (= response {:status  200
                       :body    "<h1 class=\"cool\" data-cool=\"very\">hello</h1>"
                       :headers {"Content-Type" "text/html; charset=utf-8"}}))))
  (testing "hiccup form with no attributes"
    (let [request (mock/request :get "/")
          {:keys [response]} (handle request {:body [:h1 "hello"]} {::d*/html-attrs {:data-cool "very"}})]
      (is (= response {:status  200
                       :body    "<h1 data-cool=\"very\">hello</h1>"
                       :headers {"Content-Type" "text/html; charset=utf-8"}}))))
  (testing "alternative write-html function"
    (let [request (mock/request :get "/")
          {:keys [response]} (handle request {:body [:h1 {:class "cool"} "hello"]} {::d*/html-attrs {:data-cool "very"}
                                                                                     ::d*/write-html #(str (hiccup/html %))})]
      (is (= response {:status  200
                       :body    "<h1 class=\"cool\" data-cool=\"very\">hello</h1>"
                       :headers {"Content-Type" "text/html; charset=utf-8"}}))))
  (testing "raw values are ignored"
    (let [request (mock/request :get "/")
          {:keys [response]} (handle request {:body (c/raw "<h1>hello</h1>")} {::d*/html-attrs {:data-cool "very"}})]
      (is (= response {:status  200
                       :body    "<h1>hello</h1>"
                       :headers {"Content-Type" "text/html; charset=utf-8"}})))))

(deftest using-custom-read-json-function
  (let [read-json   (fn [& _]
                      (throw (Exception. "READ JSON")))
        request (-> (mock/request :post "/")
                    (mock/header "datastar-request" "true")
                    (mock/json-body {:ok true}))]
    (is (thrown-with-msg? Exception #"READ JSON"
                          (handle request {::d*/fx [[::d*/patch-elements [:h1#test "hello"]]]} {::d*/read-json read-json})))))

(deftest using-custom-write-json-function
  (let [request  (-> (mock/request :post "/")
                     (mock/header "datastar-request" "true"))
        result   (handle request {::d*/fx [[::d*/patch-signals {:fun true}]]} {::d*/write-json (constantly "{\"bigfun\":true}")})
        {:d*.sse/keys [on-open]} (:opts result)
        {[{:keys [res]}] :results} (on-open (test-generator))]
    (is (= ["datastar-patch-signals"
            ["signals {\"bigfun\":true}"]
            #:d*.sse{:id false :retry-duration false}] res))))

(deftest using-custom-write-html-function
  (let [request (mock/request :get "/")
        {:keys [response]} (handle request {:body [:h1 "whoa"]} {::d*/write-html (constantly "<h1>hello</h1>")})]
    (is (= response {:status  200
                     :body    "<h1>hello</h1>"
                     :headers {"Content-Type" "text/html; charset=utf-8"}}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Extending via nexus
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def *conn-store (atom {}))

(def *errors (atom []))

(defn reset-refs
  [f]
  (reset! *conn-store {})
  (reset! *errors [])
  (f))

(use-fixtures :each reset-refs)

(def connection-storage-interceptor
  "An interceptor supporting userland connection storage"
  {:id :connection-storage
   ;;; store the given sse connection for later
   :before-dispatch
   (fn [{:keys [system dispatch-data] :as ctx}]
     (let [{:keys  [sse]} system
           store?  (not (::d*/with-open-sse? dispatch-data))
           conn-id (get-in dispatch-data [::d*/response ::conn-id])]
       (when (and store? conn-id)
         (swap! *conn-store assoc conn-id sse))
       ctx))
   :after-dispatch
   (fn [{:keys [errors] :as ctx}]
     (when (seq errors)
       (reset! *errors errors))
     ctx)})

(defn cowsay
  "Xtreme cowsay"
  [_ _ post-moo-text]
  (str "Moo! " post-moo-text))

(defn badtimes
  "Throw an error for bad times"
  [_ _ msg]
  (throw (Exception. msg)))

(defn uc-signals
  "Very important signals"
  [signals]
  [[::d*/patch-signals
    (reduce-kv
     (fn [m k v]
       (assoc m k (string/upper-case v))) {} signals)]])

(deftest extending-nexus
  (testing "userland connection storage via interceptors"
    (let [request (-> (mock/request :post "/")
                      (mock/header "datastar-request" "true"))
          result  (handle request {::conn-id :fun-test-conn
                                   ::d*/fx   [[::d*/patch-signals {:ok true}]]}
                          {::d*/update-nexus (fn [n]
                                               (assoc n :nexus/interceptors [connection-storage-interceptor]))})
          {:d*.sse/keys [on-open]} (:opts result)
          sse-gen (test-generator)
          _ (on-open sse-gen)]
      (is (= sse-gen (:fun-test-conn @*conn-store)))))
  (testing "adding effects"
    (let [request (-> (mock/request :post "/")
                      (mock/header "datastar-request" "true"))
          result  (handle request {::d*/fx  [[::d*/patch-signals {:ok true}]
                                             [::cowsay "Said the cow"]]}
                          {::d*/update-nexus (fn [n]
                                               (assoc-in n [:nexus/effects ::cowsay] cowsay))})
          {:d*.sse/keys [on-open]} (:opts result)
          result (on-open (test-generator))
          effect (->> result :results (filterv #(= "Moo! Said the cow" (:res %))) first)]
      (is (= "Moo! Said the cow" (:res effect)))))
  (testing "adding pure actions"
    (let [request (-> (mock/request :post "/")
                      (mock/header "datastar-request" "true")
                      (mock/json-body {:name "turjan"}))
          result  (handle request {::d*/fx  [[::uc-signals]]}
                          {::d*/update-nexus (fn [n]
                                               (assoc-in n [:nexus/actions ::uc-signals] uc-signals))})
          {:d*.sse/keys [on-open]} (:opts result)
          result (on-open (test-generator))
          uc (-> result :results first :effect (nth 2) :name)]
      (is (= uc "TURJAN"))))
  (testing "error capture"
    (let [request (-> (mock/request :post "/")
                      (mock/header "datastar-request" "true"))
          result  (handle request {::d*/fx  [[::d*/patch-signals {:ok true}]
                                             [::badtimes "Error!"]]}
                          {::d*/update-nexus (fn [n]
                                               (-> (assoc-in n [:nexus/effects ::badtimes] badtimes)
                                                   (assoc :nexus/interceptors [connection-storage-interceptor])))})
          {:d*.sse/keys [on-open]} (:opts result)
          _ (on-open (test-generator))
          errors @*errors]
      (is (= 1 (count errors)))
      (is (= "Error!" (-> errors first :err .getMessage)))))
  (testing "providing a connection via dispatch data"
    (let [*used-gen (atom nil)
          test-gen (test-generator)
          request  (-> (mock/request :post "/")
                       (mock/header "datastar-request" "true"))
          result   (handle request {::d*/fx [[::d*/patch-signals {:ok true}]]}
                           {::d*/update-nexus (fn [n]
                                                (assoc n :nexus/interceptors [{:id ::connection-replacement
                                                                               :before-effect
                                                                               (fn [{:keys [effect system] :as ctx}]
                                                                                 (let [id (first effect)]
                                                                                   (cond
                                                                                     (= id :datastar.wow/connection)
                                                                                     (assoc-in ctx [:dispatch-data :datastar.wow/connection] test-gen)

                                                                                     (= id :datastar.wow/send)
                                                                                     (do
                                                                                       (reset! *used-gen (:sse system))
                                                                                       ctx)

                                                                                     :else ctx)))}]))})] ;;; open with different sse-gen to show that the interceptor is used
      (is (= @*used-gen test-gen))
      (is (= 204 (get-in result [:response :status] 204))))))
