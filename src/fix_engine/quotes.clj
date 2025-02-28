(ns fix-engine.quotes
  (:require
   [missionary.core :as m]
    [nano-id.core :refer [nano-id]]
   [fix-translator.session :refer [load-accounts create-session]] 
   [fix-engine.boot :refer [boot-with-retry]]
   )
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

(defn quote-interactor [this conn]
  ; this fn gets called whenever the connection is established.
  (println "conn-interactor created conn:" )
  (let [{:keys [send-fix-msg in-flow]} conn
        process-msg (m/reduce
                     (fn [_ msg]
                       (println "quote interactor rcvd: " msg)
                       )
                     nil in-flow)
        login-msg (login-payload this)             
        subscribe-msg (subscribe-payload)
        ]
    (m/sp
     (try
       (println "quote-session will send login-msg")
       (m/? (send-fix-msg login-msg))
       (println "quote-session will send subscribe msg")
       (m/? (send-fix-msg subscribe-msg))
       ;(m/? (write {:op :message :val "browser-ws-connected"}))
       ;(m/? (m/join vector process-msg send-msg))
       (m/? process-msg)
       ;(println "wsconninteractor DONE! success!")
       (catch Exception ex
         (println "wsconninteractor crashed: " ex))
       (catch Cancelled _
         (println "wsconninteractor was cancelled.")
             ;(m/? shutdown!)
         true)))))

(defn create-decoder [fix-config-file account-kw]
  (-> (load-accounts fix-config-file)
      (create-session account-kw)))


(defn account-session [config-file account-kw]
  (println "getting fix account " account-kw)
  (let [this (create-decoder config-file account-kw)
        get-in-t (m/dfv) ; single assignment variable
        boot-t (boot-with-retry this quote-interactor get-in-t)]
    (m/stream
     (m/ap
      (let [dispose! (boot-t #(println "boot finished success.") 
                             #(println "boot finished crash"))
            _ (println "waiting to get in-task")
            in-f (m/? get-in-t)                 
            ]
        (loop [msg (m/? in-f)]
          (println "multiplexer rcvd: " msg)
          (m/amb msg (recur (m/? in-f)))))))))



(defonce subscription-id (atom 1))

(defn get-subscription-id []
  (swap! subscription-id inc))


(defn subscription [{:keys [symbol]}]
  (let [sub-id (get-subscription-id)]
    [:md-req-id (str sub-id)
     :md-sub-type "1" ; 2=unsubscibe
     :md-update-type "1" ; ctrader only supports 1 type.
     :md-sub-depth "1" ; 0=orderbook 1=best-bid-ask
     :md-sub-number-entries "2" ; send bid and ask together
     :md-sub-entry-type "0" ; 0 = bid
     :md-sub-entry-type "1" ; 1 = ask
     :md-sub-number-instruments "1"
     :symbol symbol]))


(comment

  (get-subscription-id)

  (subscription {:symbol "EURUSD"})


 ;  
  )

(defn security-list-request []
  (let [sub-id (get-subscription-id)]
    [:security-list-request-id (str sub-id)
     :security-list-type "0"]))
