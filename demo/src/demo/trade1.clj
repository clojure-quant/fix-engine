(ns demo.trade1
  (:require
   [missionary.core :as m]
   [fix-engine.impl.trade :refer [create-fix-broker-trade]]

   [fix-engine.logger :refer [create-logger start-logging-flow]]))
(def log (create-logger "test-trade.log"))
(log "hi")
(log "ho")


(def req-f (m/seed [1 2 3 4 5 6 7 8 9 10]))


(def broker (create-fix-broker-trade nil :ctrader-fxpro-trade req-f))


broker


(def reducer
  (m/sp
   (loop []
     (let [x (m/? (:broker-trade-res-f broker))]
       (log (str "data: " x))
       (recur)))))



(reducer
 #(println "fix-broker-trade-processor completed" %)
 #(println "fix-broker-trade-processor crash " %))