(ns demo.cli-trade-blotter
  (:require
   [missionary.core :as m]
   [tick.core :as t]
   [quanta.blotter.account-manager :refer [create-account-manager start-account-manager add-edn-accounts]]
   [quanta.blotter.consolidator :refer [create-consolidator start-consolidator!]]
   [quanta.blotter.logger :refer [create-logger log start-log-flow-to-logger]]
   [quanta.blotter.util :refer [push-flow-to-rdv]]
   [quanta.blotter.paper.broker]
   [fix-engine.blotter.account]
   [demo.util.orderflow-simulated :refer [demo-order-action-flow]]
   [demo.util.update-printer :refer [create-orderupdate-printer]]))

(defn start!
  "Mixed paper + FIX trade accounts via quanta-blotter account manager."
  []
  (let [l (create-logger "log/blotter-account-manager.txt" false)
        _ (log l {:type :blotter/started :date (t/instant)})
        log-fn (partial log l)
        order-rdv (m/rdv)
        orderupdate-rdv (m/rdv)
        dispose-orderupdate-printer (create-orderupdate-printer orderupdate-rdv)
        consolidator (create-consolidator {:order order-rdv
                                           :orderupdate orderupdate-rdv
                                           :log log-fn})
        _ (start-consolidator! consolidator)
        {:keys [combined-flow channel]} consolidator
        {:keys [order orderupdate]} channel
        l-channel (create-logger "log/blotter-order-orderupdate.txt" false)
        _ (log l-channel {:type :consolidator/started :date (t/instant)})
        dispose-flow-logger (start-log-flow-to-logger l-channel combined-flow)
        am (create-account-manager order orderupdate log-fn)
        _ (add-edn-accounts am "blotter-accounts.edn")
        dispose-account (start-account-manager am)
        dispose-orderflow (push-flow-to-rdv order-rdv demo-order-action-flow)]
    {:dispose-orderflow dispose-orderflow
     :dispose-account dispose-account
     :dispose-orderupdate-printer dispose-orderupdate-printer
     :dispose-flow-logger dispose-flow-logger}))

(defn start-cli
  "Usage: cd demo && clojure -X:blotter-trade"
  [_]
  (start!)
  @(promise))

(comment
  (def ta (start!))
  (:dispose-account ta))
