(ns demo.old.cli-quote-raw-ssl
  (:require
   [missionary.core :as m]
   [nano-id.core :refer [nano-id]]
   [fix-translator.ctrader :refer [seclist->assets write-assets]]
   [fix-translator.session :refer [create-session fix-msg-vec->payload]]
   [fix-engine.impl.certificate :refer [create-certificate-manager]]
   [fix-engine.impl-old.socket :refer [create-client]]
   [fix-engine.logger :refer [log]]
   [demo.accounts :as accounts]
   ))

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

(defn subscribe-payload []
  [:market-data-request
   {:mdreq-id  (nano-id 5)
    :subscription-request-type :snapshot-plus-updates,
    :market-depth 1,
    :mdupdate-type :incremental-refresh,
    :no-mdentry-types [{:mdentry-type :bid} {:mdentry-type :offer}],
    :no-related-sym [{:symbol "4"} ; eurjpy
                     {:symbol "1"} ; eurusd
                     ]}])

(defn security-list-request []
  [:security-list-request
   {:security-req-id (nano-id 5) ; req id
    :security-list-request-type :symbol}])

(defn create-decoder []
  (create-session (merge accounts/fxpro-quote-ssl  (create-certificate-manager))))

(defn send-msg [{:keys [send-fix-msg]} fix-type-payload-vec]
  (log "send-data" fix-type-payload-vec)
  (m/? (send-fix-msg fix-type-payload-vec)))

(defn consume-msg [decoder fix-msg]
  (log "IN-FIX" (pr-str fix-msg))
  (let [msg-type-payload (fix-msg-vec->payload decoder fix-msg)
        [msg-type payload] msg-type-payload]
    (when (= msg-type "y")
      (-> msg-type-payload seclist->assets write-assets))
    (println "msg-type: " msg-type " payload: " payload)))

(defn consume-incoming [{:keys [decoder in-flow]}]
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
        login-msg (login-payload this)
        consumer-t (consume-incoming this)
        this (assoc this :consumer-t consumer-t)]
    (send-msg this login-msg)
    (send-msg this (security-list-request))
    (send-msg this (subscribe-payload))
    this))

(defn start-cli [& _]
  (start)
  @(promise))
