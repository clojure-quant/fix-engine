(ns fix-engine.impl.interactor.trade
  (:require
   [missionary.core :as m]
   [nano-id.core :refer [nano-id]]
   [tick.core :as t]
   [fix-translator.ctrader :refer [get-asset-id get-asset-name]])
  (:import missionary.Cancelled))

(def ^:private order-qty 1000)
(def ^:private max-req-age-ms 5000)

(defn- fresh-request? [req]
  (let [ts (:ts req)]
    (and ts (<= (- (System/currentTimeMillis) (.toEpochMilli ^java.time.Instant ts))
                max-req-age-ms))))

(defn- req->order-payload
  [req asset-converter]
  (let [{:keys [action symbol side ord-type price orig-cl-ord-id]} req
        symbol-id (get-asset-id asset-converter symbol)]
    (case action
      :new-order
      (cond-> ["D"
               {:cl-ord-id (nano-id 8)
                :symbol symbol-id
                :side side
                :transact-time (t/instant)
                :order-qty order-qty
                :ord-type ord-type}]
        price (update 1 assoc :price price))
      :cancel-order
      ["F" {:orig-cl-ord-id orig-cl-ord-id
            :symbol symbol-id
            :side side
            :transact-time (t/instant)}]
      :get-positions
      ["AN" {:pos-req-id (nano-id 5)}])))

(defn- fix-payload->update
  [asset-converter [msg-type payload]]
  (case msg-type
    :execution-report
    (update payload :symbol #(get-asset-name asset-converter %))
    :business-message-reject
    {:type :business-message-reject :data payload}
    nil))

(defn- request-loop
  [req-rdv asset-converter push log]
  (m/sp
   (loop []
     (let [req (m/? req-rdv)]
       (when (fresh-request? req)
         (try
           (m/? (push (req->order-payload req asset-converter)))
           (catch Exception ex
             (log {:type :order-failure
                   :direction :out
                   :data {:request req :error (ex-message ex)}}))))
       (recur)))))

(defn- message-loop
  [pull log asset-converter res-rdv]
  (m/sp
   (try
     (loop []
       (when-let [fix-payload (m/? (pull))]
         (let [[msg-type _] fix-payload]
           (when (= msg-type :logout)
             (throw (ex-info "session-reset" {:msg "logout message received"})))
           (when-let [update (fix-payload->update asset-converter fix-payload)]
             (m/? (res-rdv update))))
         (recur)))
     (catch Cancelled _
       true))))

(defn create-trade-interactor
  [req-rdv res-rdv]
  (fn [_fix-account-config _connection-id push pull log asset-converter]
    (m/sp
     (m/? (m/join vector
                  (request-loop req-rdv asset-converter push log)
                  (message-loop pull log asset-converter res-rdv))))))
