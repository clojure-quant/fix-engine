(ns demo.cli-engine-quote-db
  (:require
   [missionary.core :as m]
   [fix-engine.core :refer [create-fix-engine configured-accounts
                            get-quote-session]]
   [fix-engine.logger :refer [log]]
   [quanta.bar.generator :refer [start-generating]]
   [quanta.bar.db.duck :as duck]))

;; CREATE FIX ENGINE

(def fix-engine
  (create-fix-engine "fix-accounts.edn"))

(configured-accounts fix-engine)

fix-engine

;; QUOTES FLOW

(def account-in-f
  (get-quote-session fix-engine  :ctrader-fxpro-quote))

(def account-in-printer
  (m/reduce (fn [_ v]
              (log "QUOTE IN" v)) nil account-in-f))

(defn start []

  (println "starting ctrader quote printer..")
  (def dispose!
    (account-in-printer #(log "demo-task completed" %)
                        #(log "demo-task crash " %)))

  (println "starting generator..")
  (def db-duck (duck/start-bardb-duck "ctrader-quotes-2026.ddb"))

  (start-generating
   {:db db-duck}
   account-in-f
   [:forex :m])
;
  )


(defn start-cli [& _]
  (start)
  @(promise))
