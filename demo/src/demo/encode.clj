(ns demo.encode
  (:require
   [fix-translator.session :refer [load-accounts create-session
                                   encode-msg]]))


(def fix-config "fix-accounts.edn")
;(def account :ctrader-tradeviewmarkets-quote)
(def account :ctrader-tradeviewmarkets2-quote)


(def s (-> (load-accounts fix-config)
           (create-session account)))

s

(encode-msg s ["x"
               {:security-req-id "125"
                :security-list-request-type :symbol}])

; market data subscribe
(encode-msg s ["V"
               {:mdreq-id  "123"
                :subscription-request-type :snapshot-plus-updates,
                :market-depth 1,
                :mdupdate-type :incremental-refresh,
                :no-mdentry-types [{:mdentry-type :bid} {:mdentry-type :offer}],
                :no-related-sym [{:symbol "4"} ; eurjpy
                                 {:symbol "1"} ; eurusd
                                 ]}])