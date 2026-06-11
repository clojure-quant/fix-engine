(ns fix-engine.quote.fix-quote-test
  (:require [clojure.test :refer [deftest is testing]]
            [quanta.quote.protocol :as p]
            [fix-engine.quote.fix-quote]))

(def asset-converter
  {:dict-by-name {"EURUSD" "1" "USDJPY" "2"}
   :dict-by-id {"1" "EURUSD" "2" "USDJPY"}})

(def account-config {:account/api :fix-quote})

(defn- capture-log []
  (let [calls (atom [])]
    [(fn [entry] (swap! calls conj entry)) calls]))

(defn- messaging [log]
  (p/create-quote-messaging account-config asset-converter log))

(deftest create-quote-messaging-test
  (is (satisfies? p/quote-messaging (messaging (fn [_])))))

(deftest subscribe-msg-test
  (let [[log-fn log-calls] (capture-log)
        [msg-type payload] (p/subscribe-msg (messaging log-fn) ["EURUSD" "USDJPY"])]
    (is (= :market-data-request msg-type))
    (is (= :snapshot-plus-updates (:subscription-request-type payload)))
    (is (= 1 (:market-depth payload)))
    (is (= :incremental-refresh (:mdupdate-type payload)))
    (is (= [{:mdentry-type :bid} {:mdentry-type :offer}]
           (:no-mdentry-types payload)))
    (is (= [{:symbol "1"} {:symbol "2"}] (:no-related-sym payload)))
    (is (= 5 (count (:mdreq-id payload))))
    (is (string? (:mdreq-id payload)))
    (is (= [{:type :subscribe
             :assets ["EURUSD" "USDJPY"]
             :broker-assets ["1" "2"]}]
           @log-calls))))

(deftest unsubscribe-msg-test
  (let [[log-fn log-calls] (capture-log)
        [msg-type payload] (p/unsubscribe-msg (messaging log-fn) ["EURUSD" "USDJPY"])]
    (is (= :market-data-request msg-type))
    (is (= :disable-previous-snapshot-plus-update-request
           (:subscription-request-type payload)))
    (is (= 1 (:market-depth payload)))
    (is (= :incremental-refresh (:mdupdate-type payload)))
    (is (= [{:mdentry-type :bid} {:mdentry-type :offer}]
           (:no-mdentry-types payload)))
    (is (= [{:symbol "1"} {:symbol "2"}] (:no-related-sym payload)))
    (is (= 5 (count (:mdreq-id payload))))
    (is (string? (:mdreq-id payload)))
    (is (= [{:type :unsubscribe
             :assets ["EURUSD" "USDJPY"]
             :broker-assets ["1" "2"]}]
           @log-calls))))

(deftest read-quote-snapshot-test
  (let [fix-vec [:market-data-snapshot-full-refresh
                 {:symbol "1"
                  :mdreq-id "sBj4R"
                  :no-mdentries [{:mdentry-type :bid :mdentry-px 1.15769M}
                                 {:mdentry-type :offer :mdentry-px 1.15773M}]}]
        quote (p/read-quote (messaging (fn [_])) fix-vec)]
    (is (= {:bid 1.15769M
            :ask 1.15773M
            :asset "EURUSD"
            :price 1.15771M
            :volume 1.0M
            :spread 0.00004M}
           quote))))
