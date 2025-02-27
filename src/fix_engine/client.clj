(ns fix-engine.client
  (:require
   [missionary.core :as m]
   [fix-translator.gloss :refer [fix-protocol xf-fix-message without-header]]
   [aleph.tcp :as tcp]
   [gloss.io :as io]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [nano-id.core :refer [nano-id]]))







(defn transform-message [s]
  (let [msg-s (s/transform xf-fix-message s)]
    (s/consume
     (fn [raw-data]
       (let [data (without-header raw-data)]
         (println "IN:" data)
         (spit "msg.log" (str "\nIN: " data) :append true)
         ;(println "IN-EDN: " (decode-msg s text))
         )) msg-s)
    ; this will not trigger, as consume is async
    (println "Connection closed 3")))


(defn create-client
  [s]
  (let [tcp-config (select-keys (:config s) [:host :port])
        _ (println "connecting fix to: " tcp-config)
        _ (spit "msg.log" "\nCONNECTING" :append true)
        c (tcp/client tcp-config)
        r (d/chain c #(wrap-duplex-stream %))]
    r))

(defn- websocket-client-task [url]
  (m/sp
   (let [client-deferred (http/websocket-client url)]
       ;(info "connecting to bybit websocket url: " url)
     (let [r (m/? (deferred->task client-deferred))]
         ;(info "bybit websocket connected successfully!")
       r))))


(defn connect [this]
  (fn [s f]
    (try
      (let [c (create-client this)
            
            ]
        (set! (.-binaryType ws) "arraybuffer")
        (set! (.-onopen ws)
              (fn [_]
                (remove-listeners ws)
                (s ws)))
        (set! (.-onclose ws)
              (fn [_]
                (remove-listeners ws)
                (s nil)))
        #(when (= (.-CONNECTING js/WebSocket) (.-readyState ws))
           (.close ws)))
      (catch :default e
        (f e) #()))))


(defn connector "
server : the server part of the program
cb : the callback for incoming messages.
msgs : the discrete flow of messages to send, spawned when websocket is connected, cancelled on websocket close.
Returns a task producing nil or failing if the websocket was closed before end of reduction. "
  [cb msgs]
  (m/sp
   (if-some [ws (m/? (connect *ws-server-url*))]
     (try
       (set! (.-onmessage ws) (comp (handle-hf-heartbeat ws cb) payload))
       (m/? (m/race 
             (send-all ws msgs) 
             (wait-for-close ws)))
       (finally
         (when-not (= (.-CLOSED js/WebSocket) (.-readyState ws))
           (.close ws) 
           (m/? (m/compel wait-for-close)))))
     {})))



(defn fib-iter [[a b]]
  (case b
    0 [1 1]
    [b (+ a b)]))

(def fib (map first (iterate fib-iter [1 1])))

(comment (take 5 fib) := [1 1 2 3 5])

(def retry-delays (map (partial * 100) (next fib)))
;; Browsers throttle websocket connects after too many attempts in a short time.
;; To prevent using browsers as port scanners.
;; Symptom: WS takes a long time to establish a connection for no apparent reason.
;; Sometimes happens in dev after multiple page refreshes in a short time.

(defn r-subject-at [^objects arr slot]
  (fn [!]
    (aset arr slot !)
    #(aset arr slot nil)))

(defn boot-with-retry [this client conn]
  (m/sp
   (loop [delays retry-delays]
     (let [s (object-array 1)]
       (println "Connecting...")
       (when-some [[delay & delays]
                   (when-some [info
                               (m/? (conn this
                                          (fn [x] ((aget s 0) x))
                                          (m/ap
                                           (println "Connected.")
                                           (let [r (m/rdv)]
                                             (m/amb=
                                              (do (m/? (client r (r-subject-at s 0)))
                                                  (m/amb))
                                              (loop []
                                                (if-some [x (m/? r)]
                                                  (m/amb x (recur))
                                                  (m/amb))))))))]
                     (if-some [code (:code info)]
                       (let [retry? (case code
                                      (1008)
                                      (throw (ex-info "Stale Electric client" {:hyperfiddle.electric/type ::stale-client}))

                                      (1013) ; server timeout - The WS spec defines 1011 - arbitrary server error,
                                      (do (println "Electric server timed out, considering this Electric client inactive.")
                                          true)
                                        ; else
                                      (do (println (str "Electric Websocket disconnected for an unexpected reason - " (pr-str info)))
                                          true))]
                         (when retry?
                           (seq retry-delays)))
                       (do (println "Electric client failed to connect to Electric server.")
                           delays)))]
         (println (str "Next attempt in " (/ delay 1000) " seconds."))
         (recur (m/? (m/sleep delay delays))))))))