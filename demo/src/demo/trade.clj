(ns demo.trade
  (:require
   [fix-engine.core :as fix]
     ;[fix-engine.connection.protocol]
   )
  (:use fix-engine.connection.protocol))


(defn my-handler [key-id reference last-msg new-msg]
  (println "my-handler: msg: " new-msg)
  (case (:msg-type new-msg)
    :logon (println "Logon accepted by" (:sender-comp-id new-msg))
    :execution-report (println "Execution Report: " new-msg)
    :logout (println "Logged out from" (:sender-comp-id new-msg))
    (println "unhandled message received: " new-msg)))


(def client (fix/load-client :ctrader-tradeviewmarkets-trade))


(def order
  [:id 55
   :side :buy
   :size 100
   :instrument-symbol "EURUSD"
   :price 1.1004])

;(new-order client order)
(new-order client :buy 100 "1" 10.0)