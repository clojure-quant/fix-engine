(ns fix-engine.blotter.trade-mapping-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [tick.core :as t]
   [quanta.blotter.oms.validation.schema :as schema]
   [fix-translator.session :refer [create-session fix-msg-vec->payload]]
   [fix-engine.blotter.trade-mapping :as tm]))

(def asset-converter
  {:dict-by-name {"EURUSD" "1" "GBPUSD" "2"}
   :dict-by-id {"1" "EURUSD" "2" "GBPUSD"}})

(defn- valid-broker? [msg]
  (is (schema/validate-message msg)
      (str "invalid broker message: " (schema/human-error-message msg))))

(deftest blotter-new-order->fix-payload-test
  (let [[msg-type payload] (tm/blotter-order->fix-payload
                            {:type :trader/new-order
                             :account/id 5292473
                             :order-id "ord-1"
                             :asset "EURUSD"
                             :side :buy
                             :order-type :limit
                             :qty 1000M
                             :limit 1.05M}
                            asset-converter)]
    (is (= :new-order-single msg-type))
    (is (= "ord-1" (:cl-ord-id payload)))
    (is (= "1" (:symbol payload)))
    (is (= :limit (:ord-type payload)))
    (is (= 1.05M (:price payload)))))

(deftest blotter-new-market-order->fix-payload-test
  (let [[msg-type payload] (tm/blotter-order->fix-payload
                            {:type :trader/new-order
                             :account/id 5292473
                             :order-id "ord-mkt"
                             :asset "EURUSD"
                             :side :buy
                             :order-type :market
                             :qty 1000M}
                            asset-converter)]
    (is (= :new-order-single msg-type))
    (is (= :market (:ord-type payload)))
    (is (nil? (:price payload)))))

(deftest blotter-limit-order-requires-limit-test
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"limit orders require :limit"
                        (tm/blotter-order->fix-payload
                         {:type :trader/new-order
                          :account/id 1
                          :order-id 1
                          :asset "EURUSD"
                          :side :buy
                          :order-type :limit
                          :qty 1000M}
                         asset-converter))))

(deftest blotter-market-order-rejects-limit-test
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"market orders must not include :limit"
                        (tm/blotter-order->fix-payload
                         {:type :trader/new-order
                          :account/id 1
                          :order-id 1
                          :asset "EURUSD"
                          :side :buy
                          :order-type :market
                          :limit 1.05M
                          :qty 1000M}
                         asset-converter))))

(deftest blotter-cancel-order->fix-payload-test
  (let [[msg-type payload] (tm/blotter-order->fix-payload
                            {:type :trader/cancel-order
                             :account/id 5292473
                             :order-id "ord-1"}
                            asset-converter)]
    (is (= :order-cancel-request msg-type))
    (is (= "ord-1" (:orig-cl-ord-id payload)))
    (is (string? (:cl-ord-id payload)))))

(deftest execution-report-confirmed-test
  (let [msg (tm/fix-payload->blotter-update
             5292473 asset-converter
             [:execution-report {:cl-ord-id "ord-1"
                                 :symbol "1"
                                 :side :buy
                                 :order-qty 1000M
                                 :ord-type :limit
                                 :price 1.05M
                                 :exec-type :new
                                 :ord-status :new
                                 :transact-time (t/instant)}])]
    (is (= :broker/order-confirmed (:type msg)))
    (is (= :limit (:order-type msg)))
    (is (= 1.05M (:limit msg)))
    (valid-broker? msg)))

(deftest execution-report-confirmed-market-test
  (let [msg (tm/fix-payload->blotter-update
             5292473 asset-converter
             [:execution-report {:cl-ord-id "ord-mkt"
                                 :symbol "1"
                                 :side :buy
                                 :order-qty 1000M
                                 :ord-type :market
                                 :exec-type :new
                                 :ord-status :new
                                 :transact-time (t/instant)}])]
    (is (= :broker/order-confirmed (:type msg)))
    (is (= :market (:order-type msg)))
    (is (nil? (:limit msg)))
    (valid-broker? msg)))

(deftest execution-report-filled-test
  (let [msg (tm/fix-payload->blotter-update
             5292473 asset-converter
             [:execution-report {:cl-ord-id "ord-1"
                                :symbol "1"
                                :side :buy
                                :exec-type :trade
                                :ord-status :filled
                                :last-qty 1000M
                                :last-px 1.051M
                                :transact-time (t/instant)}])]
    (is (= :broker/order-filled (:type msg)))
    (is (re-find #"^__" (:fill-id msg)))
    (valid-broker? msg)))

(deftest execution-report-rejected-test
  (let [msg (tm/fix-payload->blotter-update
             5292473 asset-converter
             [:execution-report {:cl-ord-id "ord-1"
                                :symbol "1"
                                :exec-type :rejected
                                :ord-status :rejected
                                :text "bad order"
                                :transact-time (t/instant)}])]
    (is (= :broker/order-rejected (:type msg)))
    (valid-broker? msg)))

(deftest execution-report-canceled-test
  (let [msg (tm/fix-payload->blotter-update
             5292473 asset-converter
             [:execution-report {:cl-ord-id "ord-1"
                                :symbol "1"
                                :exec-type :canceled
                                :ord-status :canceled
                                :transact-time (t/instant)}])]
    (is (= :broker/order-canceled (:type msg)))
    (valid-broker? msg)))

(deftest order-cancel-reject-test
  (let [msg (tm/fix-payload->blotter-update
             5292473 asset-converter
             [:order-cancel-reject {:orig-cl-ord-id "ord-1"
                                    :cl-ord-id "cxl-1"
                                    :text "unknown order"}])]
    (is (= :broker/cancel-rejected (:type msg)))
    (valid-broker? msg)))

(deftest business-message-reject-with-ref-id-test
  (let [msg (tm/fix-payload->blotter-update
             5292473 asset-converter
             [:business-message-reject {:business-reject-ref-id "ord-1"
                                       :business-reject-reason :unknown-security
                                       :text "bad symbol"}])]
    (is (= :broker/order-rejected (:type msg)))
    (is (= "ord-1" (:order-id msg)))
    (is (= "bad symbol" (:message msg)))
    (valid-broker? msg)))

(deftest business-message-reject-market-closed-test
  (let [reject-text "MARKET_CLOSED:Trading is not available: Market is closed."
        msg (tm/fix-payload->blotter-update
             1000 asset-converter
             [:business-message-reject {:text reject-text
                                       :business-reject-ref-id "fix-4"
                                       :business-reject-reason :other}])]
    (is (= :broker/order-rejected (:type msg)))
    (is (= 1000 (:account/id msg)))
    (is (= "fix-4" (:order-id msg)))
    (is (= reject-text (:message msg)))
    (valid-broker? msg)))

(deftest business-message-reject-without-ref-id-test
  (is (nil? (tm/fix-payload->blotter-update
             5292473 asset-converter
             [:business-message-reject {:business-reject-reason :other}]))))

(def business-reject-wire-msg
  [["8" "FIX.4.4"] ["9" "169"] ["35" "j"] ["34" "6"] ["49" "CSERVER"] ["50" "TRADE"]
   ["52" "20260606-23:33:39.595"] ["56" "demo.pepperstone.5292473"] ["57" "TRADE"]
   ["58" "MARKET_CLOSED:Trading is not available: Market is closed."]
   ["379" "fix-4"] ["380" "0"] ["10" "149"]])

(def trade-session
  (create-session {:spec "fix-specs/ctrader.edn"
                   :header {:begin-string "FIX.4.4"
                            :target-comp-id "CSERVER"
                            :sender-comp-id "demo.pepperstone.5292473"
                            :target-sub-id "TRADE"
                            :sender-sub-id "TRADE"}}))

(deftest business-message-reject-wire->blotter-test
  (let [fix-payload (fix-msg-vec->payload trade-session business-reject-wire-msg)
        msg (tm/fix-payload->blotter-update 1000 asset-converter fix-payload)]
    (is (= :broker/order-rejected (:type msg)))
    (is (= "fix-4" (:order-id msg)))
    (is (= "MARKET_CLOSED:Trading is not available: Market is closed." (:message msg)))
    (valid-broker? msg)))
