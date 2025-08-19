(ns datastar.wow.schema
  (:require [starfederation.datastar.clojure.adapter.common :as ac]
            [starfederation.datastar.clojure.api :as d*])
  (:import (java.io OutputStream Writer)))

(def Hiccup
  [:schema
   {:registry
    {"hiccup" [:orn
               [:node [:catn
                       [:name keyword?]
                       [:props [:? [:map-of keyword? any?]]]
                       [:children [:* [:schema [:ref "hiccup"]]]]]]
               [:primitive [:orn
                            [:nil nil?]
                            [:boolean boolean?]
                            [:number number?]
                            [:text string?]]]]}}
   "hiccup"])

(def Element Hiccup)

(def Script string?)

(def Signals
  [:map-of keyword? any?])

(def PatchElementsOptions
  [:map {:closed true}
   [d*/id {:optional true} :string]
   [d*/retry-duration {:optional true} number?]
   [d*/selector {:optional true} :string]
   [d*/patch-mode {:optional true}
    [:enum
     d*/pm-inner
     d*/pm-outer
     d*/pm-prepend
     d*/pm-append
     d*/pm-before
     d*/pm-after
     d*/pm-remove
     d*/pm-replace]]
   [d*/use-view-transition {:optional true} :boolean]])

(def PatchSignalsOptions
  [:map {:closed true}
   [d*/id {:optional true} :string]
   [d*/retry-duration {:optional true} number?]
   [d*/only-if-missing {:optional true} :boolean]])

(def ExecuteScriptOptions
  [:map {:closed true}
   [d*/id {:optional true} :string]
   [d*/retry-duration {:optional true} number?]
   [d*/auto-remove {:optional true} :boolean]
   [d*/attributes {:optional true} [:map-of keyword? string?]]])

(def PatchElementsAction
  [:or
   [:tuple [:enum ::patch-elements] Element]
   [:tuple [:enum ::patch-elements] Element PatchElementsOptions]])

(def PatchElementsSeqAction
  [:or
   [:tuple [:enum ::patch-elements-seq] [:sequential Element]]
   [:tuple [:enum ::patch-elements-seq] [:sequential Element] PatchElementsOptions]])

(def PatchSignalsAction
  [:or
   [:tuple [:enum ::patch-signals] Signals]
   [:tuple [:enum ::patch-signals] Signals PatchSignalsOptions]])

(def ExecuteScriptAction
  [:or
   [:tuple [:enum ::execute-script] Script]
   [:tuple [:enum ::execute-script] Script ExecuteScriptOptions]])

(def DatastarAction
  [:orn
   [:elements PatchElementsAction]
   [:elements-seq PatchElementsSeqAction]
   [:signals PatchSignalsAction]
   [:script ExecuteScriptAction]
   [:user [:and vector? [:fn (comp keyword? first)]]]])

(def DatastarResponse
  "datastar"
  [:map
   [:datastar.wow/fx {:description "Nexus effects to dispatch"} [:vector DatastarAction]]
   [:datastar.wow/with-open-sse?
    {:description "response level override for whether or not an sse connection closes automatically when finished" :optional true} :boolean]
   [:datastar.wow/connection {:description "An existing open sse connection. Useful for using a previously opened connection" :optional true} :some]])

(def NexusUpdate
  [:function {:registry {::nexus [:map-of :keyword :any]}}
   [:=> [:cat ::nexus] ::nexus]])

(def ReadJson
  [:=> [:cat :any] :any])

(def WriteJson
  [:=> [:cat :any] :string])

(def WriteHtml
  [:=> [:cat Hiccup] :string])

;;; Write profile schema lifted from starfederation.datastar.clojure.adapter.common-schemas

(defn output-stream? [o]
  (instance? OutputStream o))

(def output-stream-schema
  [:fn {:error/message "should be a java.io.OutputStream"}
   output-stream?])

(defn writer? [x]
  (instance? Writer x))

(def writer-schema
  [:fn {:error/message "should be a java.io.Writer"}
   writer?])

(def wrap-output-stream-schema
  [:-> output-stream-schema writer-schema])

(def WriteProfile
  [:map
   [ac/wrap-output-stream wrap-output-stream-schema]
   [ac/write! fn?]
   [ac/content-encoding :string]])

(def WithDatastarOpts
  [:map
   [:datastar.wow/html-attrs {:optional true :description "A convenience for providing injected attributes to hiccup forms used in :body"} map?]
   [:datastar.wow/write-html {:optional true :description "A function meant to serialize html for Web Browsers"} WriteHtml]
   [:datastar.wow/read-json {:optional true :description "A function meant to deserialize signals into Clojure types"} ReadJson]
   [:datastar.wow/write-json {:optional true :description "A function meant to serialize Clojure types into json strings"} WriteJson]
   [:datastar.wow/write-profile {:optional true} WriteProfile]
   [:datastar.wow/update-nexus {:optional true :description "An update function that supports extending the nexus governing effects"} NexusUpdate]
   [:datastar.wow/with-open-sse? {:optional true :description "If true, wrap dispatch in starfederation.datastar.clojure.api/with-open-sse?"} :boolean]])

(def =>with-datastar
  [:function
   [:=> [:cat ifn?] ifn?]
   [:=> [:cat ifn? WithDatastarOpts] ifn?]])
