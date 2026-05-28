(ns demo.old.cli-trade-raw
  (:require
   [missionary.core :as m]
   [nano-id.core :refer [nano-id]]
   [tick.core :as t]
   [fix-translator.session :refer [create-session fix-msg-vec->payload]]
   [fix-engine.impl-old.socket :refer [create-client]]
   [fix-engine.logger :refer [log]]
   [demo.accounts :as accounts]))

(defn login-payload [{:keys [decoder]}]
  [:logon
   {:encrypt-method :none-other,
    :heart-bt-int 60,
    :reset-seq-num-flag "Y",
    :username (str (get-in decoder [:config :username]))
    :password (str (get-in decoder [:config :password]))}])

(defn heartbeat-payload []
  [:heartbeat
   {:test-request-id  (nano-id 5)}])

(defn position-request []
  [:request-for-positions
   {:pos-req-id  (nano-id 5)}])


(defn create-decoder []
  (create-session accounts/pepperstone-trade-plain))


(defn send-msg [{:keys [decoder send-fix-msg]} fix-type-payload-vec]
  (log "send-data" fix-type-payload-vec)
  (m/? (send-fix-msg fix-type-payload-vec)))

(defn consume-msg [this fix-msg]
  (log "IN-FIX" (pr-str fix-msg))
  (let [msg-type-payload (fix-msg-vec->payload this fix-msg)
        [msg-type payload] msg-type-payload]
    (println "msg-type: " msg-type " payload: " payload)))

(defn consume-incoming [{:keys [decoder in-flow] :as this}]
  (let [log-msg (fn [_ v]
                  (consume-msg decoder v))
        t (m/reduce log-msg nil in-flow)]
    (log "consumer-start" "")
    (t #(log "consumer-success" %)
       #(log "consumer-crash " %))))

(def order-qty 1000)

(defn new-order-payload
  [{:keys [symbol side ord-type price]}]
  (cond-> ["D"
           {:cl-ord-id (nano-id 8)
            :symbol symbol
            :side side
            :transact-time (t/instant)
            :order-qty order-qty
            :ord-type ord-type}]
    price (update 1 assoc :price price)))

;; 4 different cTrader symbol ids: 1=EURUSD, 2=GBPUSD, 3=EURJPY, 4=USDJPY
(def demo-orders
  [{:symbol "1" :side :buy  :ord-type :market}
   {:symbol "2" :side :sell :ord-type :market}
   {:symbol "3" :side :buy  :ord-type :limit :price 150.0}
   {:symbol "4" :side :sell :ord-type :limit :price 160.0}])

(defn send-demo-orders [this]
  (doseq [order demo-orders]
    (send-msg this (new-order-payload order))))

(defn start []
  (let [decoder (create-decoder)
        {:keys [send-fix-msg in-flow]} (m/? (create-client decoder))
        this {:decoder decoder
              :send-fix-msg send-fix-msg
              :in-flow in-flow}
        ;_ (log "THIS" this)
        login-msg (login-payload this)
        consumer-t (consume-incoming this)
        this (assoc this :consumer-t consumer-t)]

    (send-msg this login-msg)
    (send-demo-orders this)
    (Thread/sleep 10000)
    (send-msg this (position-request))


    this))

(comment
  (def this (start))

  (def this2 (create-decoder))
  (decode-msg this2 [["8" "FIX.4.4"] ["9" "115"] ["35" "A"] ["34" "1"] ["49" "cServer"] ["50" "QUOTE"] ["52" "20250302-02:17:30.606"] ["56" "demo.tradeviewmarkets.3193335"] ["57" "QUOTE"] ["98" "0"] ["108" "60"] ["141" "Y"] ["10" "197"]])

  (-> (create-decoder) keys)
  (-> (create-decoder) :config)

  ;
  )

; DEPS.EDN ENTRY POINT

(defn start-cli [& _]
  (start)
  @(promise))
