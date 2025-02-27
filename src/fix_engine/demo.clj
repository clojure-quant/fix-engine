(ns fix-engine.demo
  (:require
   [clojure.string :as str]
   [babashka.fs :as fs]
   [aleph.tcp :as tcp]
   [gloss.core :as gloss]
   [gloss.io :as io]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   ))
  
(def protocol
  (gloss/string :ascii))
  
; :utf-8

(defn wrap-duplex-stream
  [protocol s]
  (let [out (s/stream)]
    (println "wrapping gloss,,")
    (s/connect
     (s/map #(io/encode protocol %) out)
     s)
    (s/splice
     out
     (io/decode-stream s protocol))))

(defn create-log [s]
  (println "S: " s)
  )

 (try (get-channel session)
      (catch java.net.ConnectException e
        (println "create-channel exception: ")
        (println (.getMessage e))))

(defn handle-incoming [stream]
  (s/consume
   (fn [raw-data]
     (spit "bongo.txt" raw-data)
     (println "RAWin:" raw-data)
     (let [decoded (io/decode protocol raw-data)]
       (println "Received:" decoded)))
   stream))

(defn create-client []
  (let [config {:host "demo-uk-eqx-01.p.c-trader.com"
                :port 5201}
        _ (println "creating aleph tcp-client ..")
        client @(tcp/client config) ; deferred to duplex stream
        _ (println "tcp-client created.")]
    client))


(def client (create-client))
client

(def s-out (s/stream))

(defn process-incoming-msg [stream-out msg]
  (println "in: " msg)
  ;(doall (map (partial s/put! stream-out) quotes-converted)))
   )
    

(def processor 
(s/consume (partial process-incoming-msg s-out) client)  
  )
(handle-incoming client)


processor


(def login-msg "8=FIX.4.49=13935=A49=demo.tradeviewmarkets.319329956=CSERVER50=QUOTE57=QUOTE34=152=20250226-00:02:26108=60141=Y98=0553=3193299554=2025Florian10=011")

(let [r (s/put! client login-msg)]
  (println "put result: " @r))

(s/closed? client)


(defn open-channel?
  "Returns whether a session's channel is open."
  [session]
  (and (not= nil @(:channel session))
       (not ))