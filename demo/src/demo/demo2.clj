(ns demo.demo2
  (:require
   [missionary.core :as m]
   [fix-engine.core :refer [create-fix-engine configured-accounts 
                            get-quote-session]]
   [fix-engine.logger :refer [log]]
   [fix-engine.bar-generator :refer [create-bargenerator start-processing-feed stop-processing-feed]]
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



(def tickerplant (create-bargenerator))

;; bad ip

(def account-in-f2
  (get-quote-session fix-engine :account-invalid-ip))


(def account-in-printer2
  (m/reduce (fn [_ v] (println "demo in:" v)) nil account-in-f2))






(defn start []

  (def dispose!
    (account-in-printer #(log "demo-task completed" %)
                        #(log "demo-task crash " %)))

  (start-processing-feed tickerplant :ctrader2 account-in-f)
  

  )

(comment 
  
  (dispose!)
  (stop-processing-feed tickerplant :ctrader2)
  
  tickerplant

  (def dispose!2
    (account-in-printer2 prn prn))
  (dispose!2)

;
  )

(defn start-cli [& _]
  (start)
  @(promise))
