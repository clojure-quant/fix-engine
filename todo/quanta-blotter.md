

add as dependency of fix-engine 
io.github.clojure-quant/quanta-blotter {:mvn/version "0.1.32"}

demo.cli-trade was written before quanta-blotter was written.

you write demo.blotter-trade

start by copying this source. 
https://github.com/clojure-quant/quanta-blotter/blob/main/demo/src/demo/account_manager_paper.clj
(plus the other demo namespaces that
it references).

create demo.cli-trade-blotter that uses the same source, but instead of using the paper broker,
the account config will be of the :fix-trade

you need to implement the quanta.blotter.protocol/create-trade-account interface.

fix-engine.impl.interactor.trade/req->order-payload
needs to receive orders in the format specified in quanta.blotter.oms.validation.schema
in regards to orders.


fix-accounts.edn needs to be adapted, so it works with the quanta-blotter account manager.


fix-engine.impl.interactor.trade/fix-payload->update needs to emit 
messages that are compliant with quanta.blotter.oms.validation.schema

so to summarize:

1. trading-account creating needs to happen via the multimethod 

2. the new demo should use blotter account manager, so fix-accounts.edn needs ot be adapted.

3. the input message (order/cancel) will be fix-blotter spec compiant

4. the output message (orderupdate / fill / order-confirm / new-order-confirm / order-cancel / order-reject)
needs to compliant with fix-blotter spec.









