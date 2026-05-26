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


[["8" "FIX.4.4"] ["9" "144"] ["35" "W"] ["34" "13"] ["49" "CSERVER"] ["50" "QUOTE"] ["52" "20260525-20:00:24.857"] ["56" "live.fxpro.8284171"] ["57" "QUOTE"] ["55" "7"] ["262" "3mVjA"] ["268" "2"] ["269" "0"] ["270" "214.659"] ["269" "1"] ["270" "214.672"] ["10" "144"]]
