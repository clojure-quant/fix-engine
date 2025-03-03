(ns demo.demo1
  (:require 
   [missionary.core :as m]
   [nano-id.core :refer [nano-id]]
   [fix-translator.session :refer [load-accounts create-session decode-msg]] 
   [fix-translator.ctrader :refer [seclist->assets write-assets]]
   [fix-engine.impl.socket :refer [create-client]]
   [fix-engine.logger :refer [log]]
   ))


(defn login-payload [{:keys [decoder]}]
  ["A" {:encrypt-method :none-other,
                 :heart-bt-int 60,
                  :reset-seq-num-flag "Y",
                  :username (str (get-in decoder [:config :username]))
                  :password (str (get-in decoder [:config :password]))}])


(defn heartbeat-payload []
  ["0" {:test-request-id  (nano-id 5)}])


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

(defn create-decoder []
  (-> (load-accounts "fix-accounts.edn")
      (create-session :ctrader-tradeviewmarkets2-quote)))

(defn send-msg [{:keys [decoder send-fix-msg]} fix-type-payload-vec]
  (log "send-data" fix-type-payload-vec)
  (m/? (send-fix-msg fix-type-payload-vec))) 

(defn consume-msg [this fix-msg]
  (log "IN-FIX" (pr-str fix-msg))
  (let [msg-type-payload (decode-msg this fix-msg)
        [msg-type payload] msg-type-payload]
    (when (= msg-type "y")
      (-> msg-type-payload seclist->assets write-assets)
      )))

(defn consume-incoming [{:keys [decoder in-flow] :as this}]
  (let [log-msg (fn [_ v]
                  (consume-msg decoder v))
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
 
(comment 
  (def this (start))

  (def this2 (create-decoder))
  (decode-msg this2 [["8" "FIX.4.4"] ["9" "115"] ["35" "A"] ["34" "1"] ["49" "cServer"] ["50" "QUOTE"] ["52" "20250302-02:17:30.606"] ["56" "demo.tradeviewmarkets.3193335"] ["57" "QUOTE"] ["98" "0"] ["108" "60"] ["141" "Y"] ["10" "197"]]
                    )
  
  (-> (create-decoder) keys)
  (-> (create-decoder) :config)

  ;
  )

; DEPS.EDN ENTRY POINT

(defn start-cli [& _]
  (start)
  @(promise))
