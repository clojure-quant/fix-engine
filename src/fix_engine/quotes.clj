(ns fix-engine.quotes
  (:require
   [missionary.core :as m]
   [nano-id.core :refer [nano-id]]
   [fix-engine.logger :refer [log]]
   [fix-translator.session :refer [load-accounts create-session]]
   [fix-engine.boot :refer [boot-with-retry]])
  (:import missionary.Cancelled))

(defn login-payload [this]
  {:fix-type "A"
   :fix-payload {:encrypt-method :none-other,
                 :heart-bt-int 60,
                 :reset-seq-num-flag "Y",
                 :username (str (get-in this [:config :username]))
                 :password (str (get-in this [:config :password]))}})

(defn subscribe-payload []
  {:fix-type "V"
   :fix-payload {:mdreq-id  (nano-id 5)
                 :subscription-request-type :snapshot-plus-updates,
                 :market-depth 1,
                 :mdupdate-type :incremental-refresh,
                 :no-mdentry-types [{:mdentry-type :bid} {:mdentry-type :offer}],
                 :no-related-sym [{:symbol "4"} ; eurjpy
                                  {:symbol "1"} ; eurusd
                                  ]}})

(defn security-list-request []
  {:fix-type "x"
   :fix-payload {:security-req-id (nano-id 5) ; req id
                 :security-list-request-type :symbol}})


(defn quote-interactor [this conn]
  ; this fn gets called whenever the connection is established.
  (let [{:keys [send-fix-msg in-flow]} conn
        process-msg (m/reduce
                     (fn [_ msg]
                       (log "qi-in" msg))
                     nil in-flow)
        login-msg (login-payload this)
        subscribe-msg (subscribe-payload)
        sec-list-msg2 (security-list-request)
        ]
    (m/sp
     (try
       (log "qi" "will send login-msg")
       (m/? (send-fix-msg login-msg))
       ;(log "qi" "will send subscribe msg")
       ;(m/? (send-fix-msg subscribe-msg))
       (log "qi" "will send security-list msg")
       ;(log "qi" sec-list-msg2)
       (m/? (send-fix-msg sec-list-msg2))
       ;(m/? (m/join vector process-msg send-msg))
       (m/? process-msg)
       ;(println "wsconninteractor DONE! success!")
       (catch Exception ex
         (log "qi" (str "crash: " {:msg (ex-message ex) :data (ex-data ex)}))
         ;(println "quote interactor crashed: " ex)
         )
       (catch Cancelled _
         (log "qi" "got cancelled")
             ;(m/? shutdown!)
         true)))))

(defn create-decoder [fix-config-file account-kw]
  (-> (load-accounts fix-config-file)
      (create-session account-kw)))


(defn account-session [config-file account-kw]
  (log "acc" (str "loading fix account " account-kw))
  (let [this (create-decoder config-file account-kw)
        get-in-t (m/dfv) ; single assignment variable
        boot-t (boot-with-retry this quote-interactor get-in-t)]
    (m/stream
     (m/ap
      (let [dispose! (boot-t #(log "boot" "finished success.")
                             #(log "boot" "finished crash"))
            _ (log "acc" "waiting to get input flow..")
            in-f (m/? get-in-t)]
        (if in-f
          (try
            (log "acc" "got a new in-flow!")
            (loop [msg (m/?> in-f)]
              (log "acc in" msg)
              (m/amb msg (recur (m/? in-f))))
            (catch Cancelled _
              (log "acc" "got cancelled")
              (dispose!))
                  ;(finally
                  ;  (log "acc" "finally!")
                  ;  (dispose!)
                  ;  (log "acc" "disposed success")
                ;  )
            )
          (log "acc" "flow is nil.")))))))

