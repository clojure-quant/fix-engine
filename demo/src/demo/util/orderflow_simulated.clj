(ns demo.util.orderflow-simulated
  (:require
   [missionary.core :as m]
   [demo.util.time-flow :refer [create-time-flow]]))

(def demo-order-action-flow
  (create-time-flow
   [1 {:type :trader/open-positions
       :account/id 1000
       :req-id "open-positions-req-1"
       }
    1 {:type :trader/working-orders
       :account/id 1000
       :req-id "working-orders-req-1"}
    
    5 {:type :trader/new-order
       :account/id 1000
       :order-id "fix-1"
       :asset "EURUSD"
       :side :buy
       :order-type :limit
       :limit 1.0500M
       :qty 1000M}
    15 {:type :trader/new-order
        :account/id 1000
        :order-id "fix-2"
        :asset "GBPUSD"
        :side :sell
        :order-type :limit
        :limit 1.2500M
        :qty 1000M}
    25 {:type :trader/new-order
        :account/id 1000
        :order-id "fix-3"
        :asset "EURUSD"
        :side :sell
        :order-type :market
        :qty 1000M}
    35 {:type :trader/new-order
        :account/id 1000
        :order-id "fix-4"
        :asset "GBPUSD"
        :side :buy
        :order-type :market
        :qty 1000M}]))

(comment
  (m/? (m/reduce println nil demo-order-action-flow)))
