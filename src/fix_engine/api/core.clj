(ns fix-engine.api.core
  (:require
   [fix-engine.core :as fix]
   [fix-engine.connection.protocol :as p]
   [fix-engine.api.quotes :as quotes]
   ))

(defn api-handler [key-id reference last-msg new-msg]
  ; key-id:  :user-callback 
  ; reference:  #object[clojure.lang.Agent
  ;(println "api-handler: msg: " new-msg)
  (case (:msg-type new-msg)
    :logon (println "Logon accepted by" (:sender-comp-id new-msg) "full: " new-msg)
    :execution-report (println "Execution Report: " new-msg)
    :logout (println "Logged out from" (:sender-comp-id new-msg))
    :quote-data-full (quotes/quote-data-full new-msg)
    :quote-security-list (println "security-list: " new-msg)
    (println "fix message received: " new-msg)))


(defn connect [fix-api-kw]
   (let [client (fix/load-client :ctrader-tradeviewmarkets-quote)]
     (p/logon client api-handler 60 :yes true)
     (p/securitylist client)
     client
   ))

(defn subscribe [client instrument]
  (let [id (quotes/symbol->ctrader-id (:symbol instrument))]
     (p/subscribe client {:symbol id})  
    ))


(defn snapshot [client]
  (quotes/quote-snapshot)
  )
