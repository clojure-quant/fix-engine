(ns demo.print
  (:require
   [missionary.core :as m]
   [ta.db.bars.protocol :as b]
   [quanta.bar.db.duck :as duck]))




(defn print-cli [& _]
  (let [db-duck (duck/start-bardb-duck "ctrader-quotes.ddb")
        bars (m/? (b/get-bars db-duck
                              {:asset "EURUSD"
                               :calendar [:forex :m]}
                     ; unlimited window
                              {}))]
    (println bars)))





