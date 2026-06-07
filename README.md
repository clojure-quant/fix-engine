# fix-engine [![GitHub Actions status |clojure-quant/fix-engine](https://github.com/clojure-quant/fix-engine/workflows/CI/badge.svg)](https://github.com/clojure-quant/fix-engine/actions?workflow=CI)[![Clojars Project](https://img.shields.io/clojars/v/io.github.clojure-quant/fix-engine.svg)](https://clojars.org/io.github.clojure-quant/fix-engine)


## What is it?
clj-fix is a toolkit that makes it easy for you to create your own [FIX](http://www.fixprotocol.org/what-is-fix.shtml) client.

From [fixprotocol.org](http://www.fixprotocol.org/what-is-fix.shtml)
>The Financial Information eXchange ("FIX") Protocol is a series of messaging specifications for the electronic communication of trade-related messages. It has been developed through the collaboration of banks, broker-dealers, exchanges, industry utilities and associations, institutional investors, and information technology providers from around the world.


cd demo
clj -X:cli-quote-print 

clojure -X:cli-trade :account :pepperstone-ctrader-trade-ssl
clojure -X:cli-trade-blotter

