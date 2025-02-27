
(ns demo.demo2
  (:require
   [clojure.edn :as edn]
   [aleph.tcp :as tcp]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [byte-streams :as bs]))

(defn handle-incoming [stream]
  (s/consume
   (fn [raw-data]
     (let [;text (bs/to-string raw-data)
           text (bs/to-string raw-data "US-ASCII")
           ;(.getBytes "Hello, ASCII!" "US-ASCII")
           ]  ;; Convert bytes to string
       (spit "flubber.txt" text)
       (println "Received:" text)))
   stream))

(defn client
  [host port]
  @(tcp/client {:host host, :port port}))

;; We connect a client to the server, dereferencing the deferred value returned such that `c`
;; is simply a duplex stream that takes and emits Clojure values.
(def c (client "demo-uk-eqx-01.p.c-trader.com" 5201))

;; We `put!` a value into the stream, which is encoded to bytes and sent as a TCP packet.  Since
;; TCP is a streaming protocol, it is not guaranteed to arrive as a single packet, so the server
;; must be robust to split messages.  Since both client and server are using Gloss codecs, this
;; is automatic.
(def login-msg "8=FIX.4.49=13935=A49=demo.tradeviewmarkets.319329956=CSERVER50=QUOTE57=QUOTE34=152=20250226-00:02:26108=60141=Y98=0553=3193299554=2025Florian10=011")


(handle-incoming c)

(println "test")

(def raw-data (.getBytes login-msg "US-ASCII"))

raw-data

@(s/put! c raw-data)



;; The message is parsed by the server, and the response is sent, which again may be split
;; while in transit between the server and client.  The bytes are consumed and parsed by
;; `wrap-duplex-stream`, and the decoded message can be received via `take!`.
@(s/take! c)

c