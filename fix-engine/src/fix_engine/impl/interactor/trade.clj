(ns fix-engine.impl.interactor.trade
  (:require
   [missionary.core :as m]
   [nano-id.core :refer [nano-id]]
   [tick.core :as t]
   [fix-translator.ctrader :refer [get-asset-id get-asset-name]]
   [fix-engine.blotter.trade-mapping :refer [fix-payload->blotter-update]])
  (:import missionary.Cancelled))

(def ^:private order-qty 1000)
(def ^:private max-req-age-ms 5000)

(defn- fresh-request? [req]
  (let [ts (:ts req)]
    (and ts (<= (- (System/currentTimeMillis) (.toEpochMilli ^java.time.Instant ts))
                max-req-age-ms))))

(defn- legacy-req->order-payload
  [req asset-converter]
  (let [{:keys [action symbol side ord-type price orig-cl-ord-id]} req
        symbol-id (get-asset-id asset-converter symbol)]
    (case action
      :new-order
      (cond-> [:new-order-single
               {:cl-ord-id (nano-id 8)
                :symbol symbol-id
                :side side
                :transact-time (t/instant)
                :order-qty order-qty
                :ord-type ord-type}]
        price (update 1 assoc :price price))
      :cancel-order
      [:order-cancel-request {:orig-cl-ord-id orig-cl-ord-id
                              :cl-ord-id (nano-id 8)
                              :symbol symbol-id
                              :side side
                              :transact-time (t/instant)}]
      :get-positions
      [:request-for-positions {:pos-req-id (nano-id 5)}])))

(defn- req->order-payload
  [req asset-converter]
  (if (vector? req)
    req
    (legacy-req->order-payload req asset-converter)))

(defn- legacy-fix-payload->update
  [asset-converter [msg-type payload]]
  (case msg-type
    :execution-report
    (update payload :symbol #(get-asset-name asset-converter %))
    :business-message-reject
    {:type :business-message-reject :data payload}
    nil))

(defn- fix-payload->update
  [{:keys [blotter-mode account/id asset-converter-ref]} asset-converter fix-payload]
  (if blotter-mode
    (fix-payload->blotter-update id asset-converter fix-payload)
    (legacy-fix-payload->update asset-converter fix-payload)))

(defn- request-loop
  [req-rdv opts asset-converter push log]
  (m/sp
   (loop []
     (let [req (m/? req-rdv)]
       (when (or (:blotter-mode opts) (fresh-request? req))
         (try
           (m/? (push (req->order-payload req asset-converter)))
           (catch Exception ex
             (log {:type :order-failure
                   :direction :out
                   :data {:request req :error (ex-message ex)}}))))
       (recur)))))

(defn- message-loop
  [pull log opts asset-converter res-rdv]
  (m/sp
   (try
     (loop []
       (when-let [fix-payload (m/? (pull))]
         (let [[msg-type _] fix-payload]
           (when (= msg-type :logout)
             (throw (ex-info "session-reset" {:msg "logout message received"})))
           (when-let [update (fix-payload->update opts asset-converter fix-payload)]
             (m/? (res-rdv update))))
         (recur)))
     (catch Cancelled _
       true))))

(defn create-trade-interactor
  ([req-rdv res-rdv]
   (create-trade-interactor req-rdv res-rdv {}))
  ([req-rdv res-rdv opts]
   (fn [_fix-account-config _connection-id push pull log asset-converter]
     (m/sp
      (m/? (m/join vector
                   (request-loop req-rdv opts asset-converter push log)
                   (message-loop pull log opts asset-converter res-rdv)))))))
