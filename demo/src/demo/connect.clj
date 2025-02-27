(ns demo.connect
  (:require
   [fix-engine.core :as fix]
   [fix-engine.api.core :as fix-api]
     ;[clj-fix.connection.protocol]
   )
  (:use fix-engine.connection.protocol))
 

;(fix-engine/initialize ".data/")

(def client (fix/load-client :ctrader-tradeviewmarkets-quote))

client
(:id client )

;; needed to restart a session:
(fix/end-session client)


; reset seq num can be :yes or :no
(logon client my-handler 60 :yes true)


(keys @fix/sessions)
;; => (:live.fusionmarkets.9000147-cServer)

(:live.fusionmarkets.9000147-cServer @fix/sessions)

(fix/get-session {:id :live.fusionmarkets.9000147-cServer})
;; => {:label :ctrader,
;;     :venue :test-market,
;;     :host "h44.p.ctrader.com",
;;     :port 5201,
;;     :sender "live.fusionmarkets.9000147",
;;     :target "cServer",
;;     :channel #<Atom@203b89d: #<ResultChannel@75abff3b: <== []>>,
;;     :in-seq-num #<Atom@36239ee2: 1>,
;;     :out-seq-num #<Atom@2866fa42: 3>,
;;     :next-msg #<Agent@503d1e8d: {}>,
;;     :msg-fragment #<Atom@9d446e9: "">,
;;     :translate? #<Atom@49eb935d: true>}

(fix/get-session client)
;; same output as above

(def session (fix/get-session client))

(fix/get-channel session)
;; => <== []

(def channel (fix/get-channel session))

channel


(fix/open-channel? channel)

