(ns fix-engine.impl.quotes
  (:require
   [missionary.core :as m]
   [nano-id.core :refer [nano-id]]
   [fix-engine.logger :refer [log]]
   [fix-translator.session :refer [decode-msg]]
   [fix-translator.ctrader :refer [->quote subscribe-payload]])
  (:import missionary.Cancelled))

(defn login-payload [this]
  ["A"
   {:encrypt-method :none-other,
    :heart-bt-int 60,
    :reset-seq-num-flag "Y",
    :username (str (get-in this [:config :username]))
    :password (str (get-in this [:config :password]))}])

(defn security-list-request []
  ["x" {:security-req-id (nano-id 5) ; req id
        :security-list-request-type :symbol}])

(def heartbeat-request 
  ["0" {}])

(defn timeout
  "Throw if `mailbox` haven't got any message after given `time` ms"
  [mailbox time]
  (m/sp
   (log "qt" "timeout watchdog has been started")
   (loop []
     (when (= :timeout (m/? (m/timeout mailbox time :timeout)))
       (log "qt" "timeout watchdog - timeout detected")
       (throw (ex-info "No message received after specified time" {::type ::timeout, ::time-seconds (int (/ time 1000))})))
     (recur))))



(defn create-quote-interactor []
  (let [interactor-state (atom {})]
    (fn [this conn]
  ; this fn gets called whenever the connection is established.
      (let [{:keys [send-fix-msg in-flow]} conn
            ;config (:config this)
            ;_ (log "QI CONFIG: " config)
            log-in-fix (get-in this [:config :log-in-fix])
            keepalive-mailbox (m/mbx)
            process-msg (m/reduce
                         (fn [_ msg]
                           (keepalive-mailbox nil)
                           (when log-in-fix 
                             (log "FIX-IN" (pr-str msg)))
                           nil)
                         nil in-flow)
            login-msg (login-payload this)
            ;assets ["1" "2" "3"]
            assets (->> (range 30)
                        (map inc)
                        (map str))
            subscribe-msg (subscribe-payload assets)
            sec-list-msg2 (security-list-request)
            send-heartbeat-t (m/sp 
                              (m/? (send-fix-msg heartbeat-request)))
            heartbeat-t (m/sp 
                         (log "QI" "heartbeat sender started")
                         (loop []
                           (m/? (m/sleep 25000)) 
                           (log "QI" "send heartbeat")
                           (m/? send-heartbeat-t)
                           (log "QI" "send heartbeat success")
                           (recur)))]
        (m/sp
         (try
           (log "qi" "will send login-msg")
           (m/? (send-fix-msg login-msg))
           (log "qi" "will send subscribe msgs")
           (m/? (send-fix-msg subscribe-msg))
           (log "qi" "will send security-list msg")
           (m/? (send-fix-msg sec-list-msg2))
           ;(m/? process-msg)
           (m/? (m/join vector 
                        process-msg 
                        heartbeat-t
                         (timeout keepalive-mailbox 90000)
                        ))
           (log "qi" "process-msg finished. (session disconnect)")
           (catch Cancelled _
             (log "qi" "got cancelled")
             ;(m/? shutdown!)
             true)
           (catch Exception ex
             (log "qi" (str "crash: " {:msg (ex-message ex) :data (ex-data ex)}))
                ;(println "quote interactor crashed: " ex)
             )))))))


  (defn only-quotes [s fix-in-f]
    (m/eduction
     (remove nil?) ; in the end a nil message is received, dont parse this 
     (map (partial decode-msg s))
     (map ->quote) ; returns a quote or nil (if message is not a quote)
    (remove nil?)
     fix-in-f)
    ;fix-in-f
    )
  
