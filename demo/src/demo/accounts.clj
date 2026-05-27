(ns demo.accounts)



(def fxpro-quote-plain
  {:spec "fix-specs/ctrader.edn"
   :header {:begin-string "FIX.4.4"
            :target-comp-id "CSERVER"
            :sender-comp-id "live.fxpro.8284171"
            :target-sub-id "QUOTE"
            :sender-sub-id "QUOTE"}
   :host "live-uk-eqx-01.p.c-trader.com"
   :port 5201 ; plain text
   :username "8284171"
   :password "Regenschirm13!"
   :log-out-payload false
   :log-out-fix false
   :log-in-fix true})

(def fxpro-quote-ssl
 {:spec "fix-specs/ctrader.edn"
 :header {:begin-string "FIX.4.4"
          :target-comp-id "CSERVER"
          :sender-comp-id "live.fxpro.8284171"
          :target-sub-id "QUOTE"
          :sender-sub-id "QUOTE"}
 :host "live-uk-eqx-01.p.c-trader.com"
 :port 5211 ; quote SSL/TLS
 :username "8284171"
 :password "Regenschirm13!"
 :log-out-payload false
 :log-out-fix false
 :log-in-fix true})

(def fxpro-trade-plain
{:spec "fix-specs/ctrader.edn"
 :header {:begin-string "FIX.4.4"
          :target-comp-id "CSERVER"
          :sender-comp-id "live.fxpro.8284171"
          :target-sub-id "TRADE"
          :sender-sub-id "TRADE"}
 :host "live-uk-eqx-01.p.c-trader.com"
 :port 5202 ; plain text (trade); quote uses 5201
 :username "8284171"
 :password "Regenschirm13!"
 :log-out-payload false
 :log-out-fix false
 :log-in-fix true})

(def pepperstone-trade-plain
 {:spec "fix-specs/ctrader.edn"
 :header {:begin-string "FIX.4.4"
          :target-comp-id "CSERVER"
          :sender-comp-id "demo.pepperstone.5292473"
          :target-sub-id "TRADE"
          :sender-sub-id "TRADE"}
 :host "demo-us-eqx-01.p.c-trader.com"
 :port 5202 ; plain text (trade); quote uses 5201
                      ;ssl-port 5211
 :username "5292473"
 :password "Regenschirm13!"
 :log-out-payload false
 :log-out-fix false
 :log-in-fix true})