(ns demo.update-test
  (:require
   [fix-translator.ctrader :refer [create-asset-converter]]
   [fix-engine.blotter.trade-mapping :refer [fix-payload->blotter-update]]))


(def payload
  [:execution-report
   {:ord-type :limit, :time-in-force :good-till-cancel, :symbol "1", :leaves-qty 1000M, :cl-ord-id "fix-1",
    :ord-status :new, :order-qty 1000M, 
    :exec-type :order-status, 
    :order-id "343556096", :mass-status-req-id "working-orders-req-1",
    :cum-qty 0M, :transact-time #time/instant "2026-06-11T00:22:07.158Z", :side :buy, :price 1.05M}])

(def asset-converter (create-asset-converter [{:asset "EURUSD", :digits :5, :ctrader "1"}
                                              {:asset "GBPUSD", :digits :5, :ctrader "2"}
                                              {:asset "EURJPY", :digits :3, :ctrader "3"}
                                              {:asset "USDJPY", :digits :3, :ctrader "4"}
                                              {:asset "AUDUSD", :digits :5, :ctrader "5"}
                                              {:asset "USDCHF", :digits :5, :ctrader "6"}
                                              {:asset "GBPJPY", :digits :3, :ctrader "7"}
                                              {:asset "USDCAD", :digits :5, :ctrader "8"}
                                              {:asset "EURGBP", :digits :5, :ctrader "9"}
                                              {:asset "EURCHF", :digits :5, :ctrader "10"}
                                              {:asset "AUDJPY", :digits :3, :ctrader "11"}
                                              {:asset "NZDUSD", :digits :5, :ctrader "12"}
                                              {:asset "CHFJPY", :digits :3, :ctrader "13"}
                                              {:asset "AUDCHF", :digits :5, :ctrader "1038"}
                                              {:asset "EURAUD", :digits :5, :ctrader "14"}
                                              {:asset "CADJPY", :digits :3, :ctrader "15"}
                                              {:asset "EURNOK", :digits :5, :ctrader "1039"}]))



(fix-payload->blotter-update 1000 asset-converter payload)


