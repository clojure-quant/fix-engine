(ns demo.account-status
  (:require
   [missionary.core :as m]
   [nano-id.core :refer [nano-id]]
   [quanta.blotter.oms.core :as oms]))

(defn run
  "Request account positions and working orders, wait ten seconds, then exit."
  [{:keys [account-id running-system]
    :or {account-id 1000}}]
  (let [oms (:oms (:oms-server running-system))]
    (m/? (oms/send-message oms
                           {:type :trader/open-positions
                            :account/id account-id
                            :req-id (nano-id 8)}))
    (m/? (oms/send-message oms
                           {:type :trader/working-orders
                            :account/id account-id
                            :req-id (nano-id 8)}))
    (m/? (m/sleep 10000))))
