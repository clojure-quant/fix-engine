
;; limit orders are accepted when market is closed.
;; but market orders are rejected when market is closed (because the market is closed)

{:type :fix-payload, 
:fix-msg-type :execution-report, 
:data {:ord-type :limit, 
:time-in-force :good-till-cancel,
 :symbol "2", 
 :leaves-qty 1000M, 
 :cl-ord-id "fix-2", 
 :ord-status :new, 
 :order-qty 1000M, 
 :exec-type :new, 
 :order-id "342796029", 
 :cum-qty 0M, 
 :transact-time #time/instant "2026-06-07T00:47:17.793Z", 
 :side :sell,
 :price 1.25M, 
 :pos-maint-rpt-id "223948576"},
 :direction :in, 
 :connection-id "NwKRE-2TO1apl0fc", 
 :account/id 1000}


 {:type :fix-payload, :fix-msg-type :execution-report, :data {:ord-type :limit, :time-in-force :good-till-cancel, :symbol "1", :leaves-qty 1000M, :cl-ord-id "fix-1", :ord-status :new, :order-qty 1000M, :exec-type :new, 
 :order-id "342796024", :cum-qty 0M, :transact-time #time/instant "2026-06-07T00:47:02.795Z", 
 :side :buy, :price 1.05M, 
 :pos-maint-rpt-id "223948512"},
  :direction :in, 
  :connection-id "NwKRE-2TO1apl0fc", 
  :account/id 1000}


  On cTrader, this is effectively the position ID — cTrader’s internal identifier for the position tied to the order.

From the cTrader FIX spec:

On NewOrderSingle (outbound):

Optional tag 721 tells cTrader which existing position the order belongs to (e.g. close or reduce a hedged position).
If omitted, cTrader creates a new position and returns its ID in the ExecutionReport.
Only meaningful on hedged accounts.
On ExecutionReport (inbound):

Tag 721 is the position ID assigned to (or referenced by) that order.
So in your todo/execution-report.md examples:

:cl-ord-id "fix-1"  →  :pos-maint-rpt-id "223948512"   ;; position opened for this buy
:cl-ord-id "fix-2"  →  :pos-maint-rpt-id "223948576"   ;; position opened for this sell