(ns fix-engine.api.core
  (:require
   [fix-engine.core :as fix]
   [fix-engine.connection.protocol :as p]
   [fix-engine.api.quotes :as quotes]
   ))

(defn make-api-handler [on-quote]
  (fn [key-id reference last-msg new-msg]
  ; key-id:  :user-callback 
  ; reference:  #object[clojure.lang.Agent
  ;(println "api-handler: msg: " new-msg)
  (case (:msg-type new-msg)
    :logon (println "Logon accepted by" (:sender-comp-id new-msg) "full: " new-msg)
    :execution-report (println "Execution Report: " new-msg)
    :logout (println "Logged out from" (:sender-comp-id new-msg))
    :quote-data-full (quotes/quote-data-full new-msg @on-quote)
    :quote-security-list (println "security-list: " new-msg)
    (println "fix message received: " new-msg))))


(defn connect [fix-api-kw]
   (let [client (fix/load-client :ctrader-tradeviewmarkets-quote)
         on-quote (atom nil)]
     (p/logon client (make-api-handler on-quote) 60 :yes true)
     (p/securitylist client)
     {:client client
      :on-quote on-quote
      }
   ))

(defn subscribe [{:keys [client] :as state} instrument]
  (let [id (quotes/symbol->ctrader-id (:symbol instrument))]
     (p/subscribe client {:symbol id})  
    ))


(defn snapshot [client]
  (quotes/quote-snapshot)
  )

(defn on-quote [{:keys [on-quote] :as state} on-quote-fn]
  (reset! on-quote on-quote-fn))