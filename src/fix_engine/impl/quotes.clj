(ns fix-engine.impl.quotes
  (:require
   [missionary.core :as m]
   [nano-id.core :refer [nano-id]]
   [fix-engine.logger :refer [log]]
   [fix-translator.session :refer [decode-msg]]
   [fix-translator.ctrader :refer [->quote]])
  (:import missionary.Cancelled))

(defn login-payload [this]
  ["A"
   {:encrypt-method :none-other,
    :heart-bt-int 60,
    :reset-seq-num-flag "Y",
    :username (str (get-in this [:config :username]))
    :password (str (get-in this [:config :password]))}])

(defn subscribe-payload []
  ["V" {:mdreq-id  (nano-id 5)
        :subscription-request-type :snapshot-plus-updates,
        :market-depth 1,
        :mdupdate-type :incremental-refresh,
        :no-mdentry-types [{:mdentry-type :bid} {:mdentry-type :offer}],
        :no-related-sym [{:symbol "4"} ; eurjpy
                         {:symbol "1"} ; eurusd
                         ]}])

(defn security-list-request []
  ["x" {:security-req-id (nano-id 5) ; req id
        :security-list-request-type :symbol}])


(defn create-quote-interactor []
  (let [interactor-state (atom {})]
    (fn [this conn]
  ; this fn gets called whenever the connection is established.
      (let [{:keys [send-fix-msg in-flow]} conn
            process-msg (m/reduce
                         (fn [_ msg]
                           (log "qi-in" (pr-str msg))
                           nil)
                         nil in-flow)
            login-msg (login-payload this)
            subscribe-msg (subscribe-payload)
            sec-list-msg2 (security-list-request)]
        (m/sp
         (try
           (log "qi" "will send login-msg")
           (m/? (send-fix-msg login-msg))
           (log "qi" "will send subscribe msgs")
           (m/? (send-fix-msg subscribe-msg))
           (log "qi" "will send security-list msg")
       ;(log "qi" sec-list-msg2)
           (m/? (send-fix-msg sec-list-msg2))
       ;(m/? (m/join vector process-msg send-msg))
           (m/? process-msg)
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
  
