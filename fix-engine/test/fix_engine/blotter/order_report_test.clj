(ns fix-engine.blotter.order-report-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [quanta.asset.mapper :refer [create-asset-mapper]]
   [fix-engine.impl.asset-converter :refer [set-asset-list]]
   [fix-engine.blotter.order-report :as report]))

(def asset-converter
  (let [m (create-asset-mapper {:account/session :fix} (constantly nil))]
    (set-asset-list m [{:asset "EURUSD" :ctrader "1"}
                       {:asset "GBPUSD" :ctrader "2"}])
    m))

(defn- status-payload
  [& {:keys [req-id total cl-ord-id order-id symbol side ord-type price
             order-qty cum-qty leaves-qty avg-px ord-status text position-id]
      :or {req-id "orders-1"
           total 1
           cl-ord-id "fix-1"
           order-id "broker-1"
           symbol "1"
           side :buy
           ord-type :limit
           price 1.1M
           order-qty 1000M
           cum-qty 0M
           leaves-qty 1000M
           avg-px 0M
           ord-status :new}}]
  (cond-> {:mass-status-req-id req-id
           :tot-num-reports total
           :cl-ord-id cl-ord-id
           :order-id order-id
           :symbol symbol
           :side side
           :ord-type ord-type
           :price price
           :order-qty order-qty
           :cum-qty cum-qty
           :leaves-qty leaves-qty
           :avg-px avg-px
           :ord-status ord-status
           :exec-type :order-status
           :transact-time "2024-01-01T00:00:00Z"}
    text (assoc :text text)
    position-id (assoc :pos-maint-rpt-id position-id)))

(deftest map-execution-report-to-canonical-order-test
  (let [order (report/execution-report->order
               1000 asset-converter
               (status-payload :position-id "pos-1" :text "working"))]
    (is (= "fix-1" (:order/id order)))
    (is (= 1000 (:order/account-id order)))
    (is (= "EURUSD" (:order/asset order)))
    (is (= :buy (:order/side order)))
    (is (= :limit (:order/type order)))
    (is (= :working (:order/status order)))
    (is (= 1000M (:order/qty order)))
    (is (= 0M (:order/qty-filled order)))
    (is (= 1000M (:order/qty-working order)))
    (is (= 0M (:order/avg-price order)))
    (is (= 1.1M (:order/limit order)))
    (is (= "working" (:order/text order)))
    (is (= "pos-1" (:order/position-id order)))
    (is (= [] (:order/history order)))
    (is (= "orders-1" (:req-id order)))
    (is (= 1 (:total order)))
    (is (inst? (:order/date order)))))

(deftest falls-back-to-broker-order-id-test
  (let [order (report/execution-report->order
               1000 asset-converter
               (status-payload :cl-ord-id nil :order-id "broker-99"))]
    (is (= "broker-99" (:order/id order)))))

(deftest preserves-duplicate-client-ids-in-consolidation-test
  (let [consolidator (report/create-report-consolidator "orders-1")
        first-order (report/execution-report->order
                     1000 asset-converter
                     (status-payload :total 2 :order-id "broker-1"))
        second-order (report/execution-report->order
                      1000 asset-converter
                      (status-payload :total 2 :order-id "broker-2"))]
    (is (nil? (report/add-response consolidator first-order)))
    (let [orders (report/add-response consolidator second-order)]
      (is (= 2 (count orders)))
      (is (= ["fix-1" "fix-1"] (mapv :order/id orders))))))
(deftest consolidate-order-reports-test
  (let [consolidator (report/create-report-consolidator "orders-1")
        first-response {:req-id "orders-1" :total 2 :order/id "a"}
        second-response {:req-id "orders-1" :total 2 :order/id "b"}]
    (is (nil? (report/add-response consolidator first-response)))
    (is (= [first-response second-response]
           (report/add-response consolidator second-response)))))

(deftest ignores-response-for-another-request-test
  (let [consolidator (report/create-report-consolidator "orders-1")]
    (is (nil? (report/add-response consolidator
                                   {:req-id "orders-2" :total 1})))
    (is (empty? @(:responses consolidator)))))

(deftest zero-order-report-test
  (let [consolidator (report/create-report-consolidator "orders-1")]
    (is (= [] (report/add-response consolidator
                                   {:req-id "orders-1" :total 0})))
    (is (empty? @(:responses consolidator)))))

(deftest invalid-total-test
  (testing "a missing total cannot complete a report"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"non-negative :total"
         (report/add-response
          (report/create-report-consolidator "orders-1")
          {:req-id "orders-1"})))))

(deftest status-normalization-test
  (doseq [[ord-status expected] [[:new :working]
                                 [:partially-filled :working]
                                 [:replaced :working]
                                 [:filled :filled]
                                 [:canceled :cancelled]
                                 [:rejected :rejected]
                                 [:expired :expired]]]
    (is (= expected
           (:order/status
            (report/execution-report->order
             1000 asset-converter
             (status-payload :ord-status ord-status
                             :leaves-qty (if (= expected :working) 1000M 0M))))))))
