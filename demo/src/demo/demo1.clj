(ns demo.demo1
  (:require 
   [missionary.core :as m]
   [nano-id.core :refer [nano-id]]
   [fix-translator.session :refer [load-accounts create-session]] 
   [fix-engine.socket :refer [create-client]]
   [fix-engine.logger :refer [log]]
   ))

(defn login-payload [{:keys [decoder]}]
  {:fix-type "A"
   :fix-payload {:encrypt-method :none-other,
                 :heart-bt-int 60,
                 :reset-seq-num-flag "Y",
                 :username (str (get-in decoder [:config :username]))
                 :password (str (get-in decoder [:config :password]))}})


(defn heartbeat-payload []
  {:fix-type "0"
   :fix-payload {:test-request-id  (nano-id 5)}})


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

(defn create-decoder []
  (-> (load-accounts "fix-accounts.edn")
      (create-session :ctrader-tradeviewmarkets2-quote)))

(defn send-msg [{:keys [decoder send-fix-msg]} {:keys [fix-type fix-payload] :as fix-msg}]
  (log "send-data" fix-payload)
  (m/? (send-fix-msg fix-msg))) 

(defn consume-incoming [{:keys [in-flow]}]
  (let [log-msg (fn [_ v]
                  (log "IN-FIX" v))
        t (m/reduce log-msg nil in-flow)]
    (log "consumer-start" "")
    (t #(log "consumer-success" %)
       #(log "consumer-crash " %))))
    
(defn start []
  (let [decoder (create-decoder)
        {:keys [send-fix-msg in-flow]} (m/? (create-client decoder))
        this {:decoder decoder 
              :send-fix-msg send-fix-msg
              :in-flow in-flow}
        ;_ (log "THIS" this)
        login-msg (login-payload this)
        consumer-t (consume-incoming this)
        this (assoc this :consumer-t consumer-t)
        ]
    
    (send-msg this login-msg)
    (send-msg this (security-list-request))
    (send-msg this (subscribe-payload))
    this
    ))
 

; (def this (start))


(defn start-cli [& _]
  (start)
  @(promise))




