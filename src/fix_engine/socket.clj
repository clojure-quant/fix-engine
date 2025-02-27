(ns fix-engine.socket
  (:require
   [missionary.core :as m]
   [aleph.tcp :as tcp]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [gloss.io :as io]
   [fix-translator.gloss :refer [fix-protocol xf-fix-message without-header]]
   [fix-translator.session :refer [encode-msg2]]
   [fix-engine.logger :refer [log]]
   ))

;; manifold stuff

(defn wrap-duplex-stream
  [s]
  (let [out (s/stream)]
    ; 
    (s/connect
     ;source
     (s/map #(io/encode-all fix-protocol %) out)
     ; sink
     s
     {:upstream? true
      :downstream? true})
    (s/splice
     out
     (io/decode-stream s fix-protocol))))

(defn deferred->task
  "Returns a missionary task completing with the result of given manifold-deferred."
  [df]
  ; see: https://github.com/leonoel/missionary/wiki/Task-interop#futures-promises
  (let [v (m/dfv)]
    (d/on-realized df
                   (fn [r]
                     ;(println "deferred success: " r)
                     (v (fn [] r))
                     ;(println "deferred success delivered!")
                     )
                   (fn [e]
                     (println "deferred error: " e)
                     (v (fn [] (throw e)))
                     ;(println "deferred error delivered!")
                     ))
    (m/absolve v)))

(defn encode-fix-msg [this {:keys [fix-type fix-payload]}]
  (let [out-msg (->> (encode-msg2 this fix-type fix-payload)
                     :wire)
        data (without-header out-msg)]
    (log "OUT-PAYLOAD" data)
    out-msg))

(defn connected? [stream]
  (when stream
    (let [desc (s/description stream)]
      (and
       (not (-> desc :sink :closed?))
       (not (-> desc :source :closed?))))))


(defn create-msg-writer [this stream]
  (fn [fix-msg]
    (when-not (connected? stream)
       (throw (ex-info "send-msg failed (not connected)" {:text "not connected"})))
    (let [data (encode-fix-msg this fix-msg)
          result-d (s/put! stream data) 
          result-t (deferred->task result-d)]
      (m/sp
       (log "OUT-FIX" data)
       (let [r (m/? result-t)]
         (log "send-result " r)
         (if r
           r
           (throw (ex-info "send-msg failed" {:msg fix-msg}))))))))

(defn read-msg-t [this stream]
  (let [data-d (s/take! stream)]
   (deferred->task data-d)))

(defn create-read-f [this stream]
  (m/ap
   (loop [data (m/? (read-msg-t this stream))]
     ;(log "IN" data)
     (m/amb 
      data
      (if data 
        (recur (m/? (read-msg-t this stream)))
        (throw (ex-info "stream disconnected" {:where :in}))))
     )))


(defn create-client
  [this]
  (let [tcp-config (select-keys (:config this) [:host :port])
        _ (log "CONNECTING" tcp-config)
        c (tcp/client tcp-config)
        r (d/chain c #(wrap-duplex-stream %))
        connect-t (deferred->task r)
        ]
    (m/sp 
     (let [stream (m/? connect-t)
           _ (log "CONNECTED" "")]
       {:send-fix-msg (create-msg-writer this stream)
        :in-flow (->> (create-read-f this stream)
                      (m/eduction xf-fix-message))}))))


