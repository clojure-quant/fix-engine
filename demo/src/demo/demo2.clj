(ns demo.demo2
  (:require
   [missionary.core :as m]
   [fix-engine.quotes :refer [account-session]]))

(def account-in-f
  (account-session "fix-accounts.edn" :ctrader-tradeviewmarkets2-quote))


(def account-in-printer
  (m/reduce (fn [_ v] (println "account-in:" v)) nil account-in-f))


(account-in-printer prn prn)
