(ns fix-engine.impl.socket
  (:require
   [missionary.core :as m]
   [aleph.tcp :as tcp]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [gloss.io :as io]
   [fix-translator.gloss :refer [fix-protocol xf-fix-message without-header]]
   [fix-translator.session :refer [encode-msg]]
   [fix-engine.logger :refer [log]]))

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
                     ;(println "deferred error: " e)
                     (v (fn [] (throw e)))
                     ;(println "deferred error delivered!")
                     ))
    (m/absolve v)))

(defn encode-fix-msg-log [this fix-type-payload-vec]
  (log "OUT-PAYLOAD" fix-type-payload-vec)
  (encode-msg this fix-type-payload-vec))

(defn encode-fix-msg-no-log [this fix-type-payload-vec]
  (encode-msg this fix-type-payload-vec))

(defn connected? [stream]
  (when stream
    (let [desc (s/description stream)]
      (and
       (not (-> desc :sink :closed?))
       (not (-> desc :source :closed?))))))

(defn create-msg-writer [this stream]
  (let [log-out-payload (get-in this [:config :log-out-payload])
        log-out-fix (get-in this [:config :log-out-fix])
        encode-fix-msg (if log-out-fix
                         encode-fix-msg-log
                         encode-fix-msg-no-log)]
    (fn [fix-type-payload-vec]
      (when-not (connected? stream)
        (throw (ex-info "send-msg failed (not connected)" {:text "not connected"})))
      (let [data (encode-fix-msg this fix-type-payload-vec)
            result-d (s/put! stream data)
            result-t (deferred->task result-d)]
        (m/sp
         (when log-out-fix
           (log "OUT-FIX" (pr-str data)))
         (let [r (m/? result-t)]
         ;(log "send-result " r)
           (if r
             r
             (throw (ex-info "send-msg failed" {:msg fix-type-payload-vec})))))))))

(defn read-msg-t [this stream]
  (let [data-d (s/take! stream)]
    (deferred->task data-d)))

(defn create-read-f [this stream]
  (m/ap
   (loop [data (m/? (read-msg-t this stream))]
     ;(log "IN" data) ; this would log each tag=value tuple
     (m/amb
      data
      (if data
        (recur (m/? (read-msg-t this stream)))
        (do (log "fix-conn" "got disconnected")
            nil ; (throw (ex-info "fix-connection disconnected" {:where :in}))
            ))))))

(defn create-client
  [this]
  (let [tcp-config (select-keys (:config this) [:host :port])
        _ (log "CONNECTING" tcp-config)
        c (tcp/client tcp-config)
        r (d/chain c #(wrap-duplex-stream %))
        connect-t (deferred->task r)]
    (m/sp
     (let [stream (m/? connect-t)
           _ (log "CONNECTED" tcp-config)]
       {:send-fix-msg (create-msg-writer this stream)
        :in-flow (->> (create-read-f this stream) ; flow of fields
                      (m/eduction xf-fix-message) ; flow of field-vecs
                      (m/stream))})))) ; publisher


