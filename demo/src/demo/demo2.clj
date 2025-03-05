(ns demo.demo2
  (:require
   [missionary.core :as m]
   [fix-engine.core :refer [create-fix-engine configured-accounts
                            get-quote-session]]
   [fix-engine.logger :refer [log]]
   [quanta.bar.generator :refer [start-generating]]
   [quanta.bar.db.duck :as duck]
   ))

;; CREATE FIX ENGINE

(def fix-engine
  (create-fix-engine "fix-accounts.edn"))

(configured-accounts fix-engine)

fix-engine

;; QUOTES FLOW

(def account-in-f
  (get-quote-session fix-engine :ctrader-tradeviewmarkets2-quote))

(def account-in-printer
  (m/reduce (fn [_ v]
              (log "QUOTE IN" v)) nil account-in-f))

(defn start []

  (println "starting ctrader quote printer..")
  (def dispose!
    (account-in-printer #(log "demo-task completed" %)
                        #(log "demo-task crash " %)))

  (println "starting generator..")
  (def db-duck (duck/start-bardb-duck "ctrader-quotes.ddb"))
  
  (start-generating
    {:db db-duck}
    account-in-f
    [:forex :m])
;
)



(comment

  (dispose!)
  (stop-generating [:crypto :m])

  (def dispose!2
    (account-in-printer2 prn prn))
  (dispose!2)

;
  )

(defn start-cli [& _]
  (start)
  @(promise))

;; bad ip

(def account-in-f2
  (get-quote-session fix-engine :account-invalid-ip))

(def account-in-printer2
  (m/reduce (fn [_ v] (println "demo in:" v)) nil account-in-f2))

(def assets ["EUR/USD" "GBP/USD" "EUR/JPY"
             "USD/JPY" "AUD/USD" "USD/CHF"
             "GBP/JPY" "USD/CAD" "EUR/GBP"
             "EUR/CHF"  "NZD/USD" "USD/NOK"
             "USD/ZAR" "USD/SEK" "USD/MXN"])

