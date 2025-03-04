(ns fix-engine.impl.quotes
  (:require
   [missionary.core :as m]
   [nano-id.core :refer [nano-id]]
   [fix-engine.logger :refer [log log-time]]
   [fix-translator.session :refer [decode-msg]]
   [fix-translator.ctrader :refer [subscribe-payload
                                   ->quote incoming-quote-id-convert
                                   seclist->assets write-assets create-asset-converter]])
  (:import missionary.Cancelled))

(defn login-payload [fix-session]
  ["A"
   {:encrypt-method :none-other,
    :heart-bt-int 60,
    :reset-seq-num-flag "Y",
    :username (str (get-in fix-session [:config :username]))
    :password (str (get-in fix-session [:config :password]))}])

(defn security-list-request []
  ["x" {:security-req-id (nano-id 5) ; req id
        :security-list-request-type :symbol}])

(def heartbeat-request
  ["0" {}])

(defn timeout
  "Throw if `mailbox` haven't got any message after given `time` ms"
  [mailbox time]
  (m/sp
   (log-time "qt" "timeout watchdog has been started")
   (loop []
     (when (= :timeout (m/? (m/timeout mailbox time :timeout)))
       (log-time "qt" "timeout watchdog - timeout detected")
       (throw (ex-info "No message received after specified time" {::type ::timeout, ::time-seconds (int (/ time 1000))})))
     (recur))))

(defn create-quote-interactor []
  (let [interactor-state (atom {})]
    (fn [fix-session conn]
  ; this fn gets called whenever the connection is established.
      (let [{:keys [send-fix-msg in-flow]} conn
            ;config (:config fix-session)
            ;_ (log "QI CONFIG: " config)
            log-in-fix (get-in fix-session [:config :log-in-fix])
            keepalive-mailbox (m/mbx)

            process-msg (m/reduce
                         (fn [_ msg]
                           (keepalive-mailbox nil)
                           (when log-in-fix
                             (log-time "FIX-IN" (pr-str msg)))

                           (let [fix-type-payload (decode-msg fix-session msg)
                                 [msg-type payload] fix-type-payload]
                             (when (= msg-type "y")
                               (log-time "SEC-LIST" "RCVD")
                               (let [assets (seclist->assets fix-type-payload)
                                     converter (create-asset-converter assets)]
                                 (write-assets assets)
                                 ;(log-time "KEYS:" (keys fix-session))
                                 (reset! (:converter fix-session) converter)
                                 (log-time "asset-id-converter" (str "created with " (count assets) "assets"))
                                 (log-time "converter new: " @(:converter fix-session))
                                 (log-time "fix-session keys: " (keys fix-session)))))

                           nil)
                         nil in-flow)
            login-msg (login-payload fix-session)
            ;assets ["1" "2" "3"]
            assets (->> (range 30)
                        (map inc)
                        (map str))
            subscribe-msg (subscribe-payload assets)
            sec-list-msg2 (security-list-request)
            send-heartbeat-t (m/sp
                              (m/? (send-fix-msg heartbeat-request)))
            heartbeat-t (m/sp
                         (log-time "QI" "heartbeat sender started")
                         (loop []
                           (m/? (m/sleep 25000))
                           (log-time "QI" "send heartbeat")
                           (m/? send-heartbeat-t)
                           (log-time "QI" "send heartbeat success")
                           (recur)))]
        (m/sp
         (try
           (log-time "qi" "will send login-msg")
           (m/? (send-fix-msg login-msg))
           (log-time "qi" "will send subscribe msgs")
           (m/? (send-fix-msg subscribe-msg))
           (log-time "qi" "will send security-list msg")
           (m/? (send-fix-msg sec-list-msg2))
           ;(m/? process-msg)
           (m/? (m/join vector
                        process-msg
                        heartbeat-t
                        (timeout keepalive-mailbox 90000)))
           (log-time "qi" "process-msg finished. (session disconnect)")
           (catch Cancelled _
             (log-time "qi" "got cancelled")
             ;(m/? shutdown!)
             true)
           (catch Exception ex
             (log-time "qi" (str "crash: " {:msg (ex-message ex) :data (ex-data ex)}))
                ;(println "quote interactor crashed: " ex)
             )))))))

(defn only-quotes [fix-session fix-in-f]
  (log-time "only-quotes s:" (keys fix-session))
  (m/eduction
   (remove nil?) ; in the end a nil message is received, dont parse this 
   (map (partial decode-msg fix-session))
   (map ->quote) ; returns a quote or nil (if message is not a quote)
   (remove nil?)
   (map (partial incoming-quote-id-convert fix-session))
   fix-in-f)
    ;fix-in-f
  )

