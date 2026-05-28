(ns demo.old.cli-engine-quote-db
  (:require
   [clojure.edn :as edn]
   [missionary.core :as m]
   [fix-engine.impl-old.account :refer [create-account-session]]
   [fix-engine.impl-old.quotes :refer [create-quote-interactor only-quotes]]
   [fix-engine.logger :refer [log]]
   [quanta.bar.generator :refer [start-generating]]
   [quanta.bar.db.duck :as duck]))

(def fix-engine
  {:accounts (edn/read-string (slurp "fix-accounts-old.edn"))})

(def account-config
  (:ctrader-fxpro-quote (:accounts fix-engine)))

(def quote-session
  (create-account-session account-config create-quote-interactor))

(def account-in-f
  (only-quotes (:session quote-session) (:out-f quote-session)))

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
   [:forex :m]))

(defn start-cli [& _]
  (start)
  @(promise))
