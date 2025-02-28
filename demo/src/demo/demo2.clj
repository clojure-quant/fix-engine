(ns demo.demo2
  (:require
   [missionary.core :as m]
   [fix-engine.quotes :refer [account-quotes]]
   [fix-engine.logger :refer [log]]
   ))

(def account-in-f
  (account-quotes "fix-accounts.edn" :ctrader-tradeviewmarkets2-quote))


(def account-in-printer
  (m/reduce (fn [_ v] 
              (log "demo in" v)) nil account-in-f))


(def dispose! 
(account-in-printer #(log "demo-task completed" %)
                    #(log "demo-task crash " %))
)


(dispose!)


;; bad ip

(def account-in-f2
  (account-quotes "fix-accounts.edn" :account-invalid-ip))


(def account-in-printer2
  (m/reduce (fn [_ v] (println "demo in:" v)) nil account-in-f2))


(def dispose!2
  (account-in-printer2 prn prn))

(dispose!2)

; 