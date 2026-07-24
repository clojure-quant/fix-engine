(ns fix-engine.blotter.messaging
  (:require
   [nano-id.core :refer [nano-id]]
   [tick.core :as t]
   [quanta.blotter.protocol :as p]
   [quanta.asset.mapper :as am])
  (:import [java.math BigDecimal]
           [java.time Instant]))

(defn- ->decimal [x]
  (cond
    (nil? x) nil
    (instance? BigDecimal x) x
    (number? x) (bigdec x)
    (string? x) (bigdec x)
    :else (bigdec (str x))))

(defn- ->instant [x]
  (cond
    (nil? x) (t/instant)
    (instance? Instant x) x
    (inst? x) (.toInstant ^java.util.Date x)
    (string? x) (Instant/parse x)
    :else (t/instant)))

(defn- ->order-id [x]
  (if (string? x) x (str x)))

(defn- synthetic-fill-id []
  (str "__" (nano-id 6)))

(defn- reject-message [msg]
  (ex-info msg {}))

(defn- validate-order-type [order-type limit]
  (case order-type
    :limit (when-not limit
             (throw (reject-message "limit orders require :limit")))
    :market (when limit
              (throw (reject-message "market orders must not include :limit")))
    (throw (reject-message "order-type must be :limit or :market"))))

(defn- fix-ord-type->order-type [ord-type price]
  (or ord-type (if price :limit :market)))

(defn- take-pending!
  "Lookup `req-id` in atom and remove it. Returns value or nil."
  [req-atom req-id]
  (when (and req-atom req-id)
    (let [req-id (->order-id req-id)
          v (get @req-atom req-id)]
      (when (some? v)
        (swap! req-atom dissoc req-id)
        v))))

(defn- store-pending! [req-atom req-id value]
  (when (and req-atom req-id)
    (swap! req-atom assoc (->order-id req-id) value)))

(defn blotter-order->fix-payload
  "Blotter trader order -> fix-translator [msg-type payload].
   Optional `cancel-req-atom` / `modify-req-atom` store client request ids
   (FIX ClOrdID) mapped to blotter order-ids for inbound reject correlation."
  ([order asset-converter]
   (blotter-order->fix-payload order asset-converter nil nil))
  ([order asset-converter cancel-req-atom modify-req-atom]
   (let [{:keys [type order-id asset side qty limit order-type account/id req-id]} order]
     (case type
       :trader/new-order
       (let [_ (validate-order-type order-type limit)
             symbol-id (am/to-api asset-converter asset)]
         (cond-> [:new-order-single {:cl-ord-id (->order-id order-id)
                                     :symbol symbol-id
                                     :side side
                                     :transact-time (t/instant)
                                     :order-qty (->decimal qty)
                                     :ord-type order-type}]
           (= :limit order-type) (update 1 assoc :price (->decimal limit))))

       :trader/cancel-order
       (let [oid (->order-id order-id)
             cancel-req-id (nano-id 8)]
         (store-pending! cancel-req-atom cancel-req-id oid)
         [:order-cancel-request {:orig-cl-ord-id oid
                                 :cl-ord-id cancel-req-id}])

       :trader/modify-order
       (let [oid (->order-id order-id)
             modify-req-id (nano-id 8)
             _ (when-not qty
                 (throw (reject-message "modify orders require :qty")))
             payload (cond-> {:orig-cl-ord-id oid
                              :cl-ord-id modify-req-id
                              :order-qty (->decimal qty)}
                       limit (assoc :price (->decimal limit)))]
         (store-pending! modify-req-atom modify-req-id
                         {:order-id oid
                          :asset asset
                          :qty (->decimal qty)
                          :limit (some-> limit ->decimal)})
         [:order-cancel-replace-request payload])

       :trader/open-positions
       [:request-for-positions {:pos-req-id req-id}]

       :trader/working-orders
       [:order-mass-status-request {:mass-status-req-id req-id
                                    :mass-status-req-type :status-for-all-orders}]

       (throw (ex-info "unsupported blotter order type"
                       {:type type :account/id id}))))))

(defn- execution-report->blotter
  [account-id asset-converter cancel-req-atom modify-req-atom payload]
  (let [asset (when-let [s (:symbol payload)]
                (am/from-api asset-converter s))
        order-id (or (:cl-ord-id payload) (:order-id payload))
        date (->instant (:transact-time payload))
        exec-type (:exec-type payload)]
    (case exec-type
      :new ;; new order confirmed
      (when (and asset order-id)
        (let [order-type (fix-ord-type->order-type (:ord-type payload) (:price payload))]
          (cond-> {:type :broker/order-confirmed
                   :account/id account-id
                   :order-id order-id
                   :asset asset
                   :side (:side payload)
                   :qty (->decimal (:order-qty payload))
                   :order-type order-type
                   :date date
                   :message (or (:text payload) "")}
            (= :limit order-type) (assoc :limit (->decimal (:price payload))))))

      :order-status ;; partial response to a position-report request
      (when (and asset order-id)
        (let [order-type (fix-ord-type->order-type (:ord-type payload) (:price payload))]
          (cond-> {:type :broker/order-status
                   :account/id account-id
                   :order-id order-id
                   :asset asset
                   :side (:side payload)
                   :qty (->decimal (:order-qty payload))
                   :order-type order-type
                   :date date
                   :time-in-force (:time-in-force payload)
                   :leaves-qty (->decimal (:leaves-qty payload))
                   :cum-qty (->decimal (:cum-qty payload))
                   :broker-order-id (:order-id payload)}
            (= :limit order-type) (assoc :limit (->decimal (:price payload)))
            (:message payload) (assoc :message (:message payload)))))

      ;; execution
      :trade
      (when (and asset order-id)
        {:type :broker/order-filled
         :account/id account-id
         :order-id order-id
         :fill-id (or (:exec-id payload) (synthetic-fill-id))
         :date date
         :asset asset
         :qty (->decimal (or (:last-qty payload) (:order-qty payload)))
         :side (:side payload)
         :price (->decimal (or (:last-px payload) (:avg-px payload) (:price payload)))})

      ;; rejection
      :rejected
      (when order-id
        {:type :broker/order-rejected
         :account/id account-id
         :order-id order-id
         :date date
         :message (or (:text payload) (some-> (:ord-rej-reason payload) str))})

      ;; cancellation
      :canceled
      (when-let [canceled-order-id (or (:orig-cl-ord-id payload) order-id)]
        ;; cancel request id may be in cl-ord-id — drop pending if present
        (take-pending! cancel-req-atom (:cl-ord-id payload))
        {:type :broker/order-canceled
         :account/id account-id
         :order-id (->order-id canceled-order-id)
         :date date})

      :expired
      (when order-id
        {:type :broker/order-expired
         :account/id account-id
         :order-id order-id
         :date date})

      :replace
      (let [req-id (:cl-ord-id payload)
            pending (take-pending! modify-req-atom req-id)
            blotter-order-id (or (:orig-cl-ord-id payload)
                                 (:order-id pending)
                                 order-id)
            asset* (or asset (:asset pending))
            qty* (or (->decimal (:order-qty payload)) (:qty pending))
            limit* (or (->decimal (:price payload)) (:limit pending))]
        (when (and blotter-order-id asset*)
          (cond-> {:type :broker/order-modified
                   :account/id account-id
                   :order-id (->order-id blotter-order-id)
                   :asset asset*
                   :message (or (:text payload) "order modified")}
            qty* (assoc :qty qty*)
            limit* (assoc :limit limit*))))

      nil)))

(defn- reject-message-text [payload fallback]
  (or (:text payload)
      (some-> (:business-reject-reason payload) name)
      fallback))

(defn- business-message-reject->blotter
  [account-id cancel-req-atom modify-req-atom payload]
  (when-let [ref-id (:business-reject-ref-id payload)]
    (let [msg (reject-message-text payload "business message rejected")
          date (t/instant)]
      (if-let [order-id (take-pending! cancel-req-atom ref-id)]
        {:type :broker/cancel-rejected
         :account/id account-id
         :order-id (->order-id order-id)
         :message msg}
        (if-let [pending (take-pending! modify-req-atom ref-id)]
          {:type :broker/modify-rejected
           :account/id account-id
           :order-id (->order-id (:order-id pending))
           :message msg}
          {:type :broker/order-rejected
           :account/id account-id
           :order-id (->order-id ref-id)
           :date date
           :message msg})))))

(defn- order-cancel-reject->blotter
  [account-id cancel-req-atom modify-req-atom payload]
  (let [req-id (some-> (:cl-ord-id payload) ->order-id)
        response-to (:cxl-rej-response-to payload)
        msg (or (:text payload) "request rejected")
        in-cancel? (boolean (and req-id cancel-req-atom (contains? @cancel-req-atom req-id)))
        in-modify? (boolean (and req-id modify-req-atom (contains? @modify-req-atom req-id)))
        modify? (case response-to
                  :order-cancel-replace-request true
                  :order-cancel-request false
                  ;; Infer from which pending map owns the request id.
                  (and in-modify? (not in-cancel?)))
        pending-order-id (if modify?
                           (:order-id (take-pending! modify-req-atom req-id))
                           (take-pending! cancel-req-atom req-id))
        ;; Cleanup if response-to forced a type that didn't match atom membership.
        pending-order-id (or pending-order-id
                             (when modify? (take-pending! cancel-req-atom req-id))
                             (when-not modify?
                               (:order-id (take-pending! modify-req-atom req-id))))
        order-id (or (:orig-cl-ord-id payload) pending-order-id req-id)]
    (when order-id
      (if modify?
        {:type :broker/modify-rejected
         :account/id account-id
         :order-id (->order-id order-id)
         :message msg}
        {:type :broker/cancel-rejected
         :account/id account-id
         :order-id (->order-id order-id)
         :message msg}))))

(defn- position-report->blotter [account-id asset-converter payload]
  (let [{:keys [symbol pos-req-id no-positions settl-price total-num-pos-reports pos-maint-rpt-id]} payload
        asset (when symbol
                (am/from-api asset-converter symbol))]
    {:type :broker/positions-item
     :account/id account-id
     :req-id pos-req-id
     :asset asset
     :position no-positions
     :position-id pos-maint-rpt-id
     :settl-price settl-price
     :total total-num-pos-reports}))

(defn fix-payload->blotter-update
  "fix-translator [msg-type payload] -> blotter broker message or nil."
  ([account-id asset-converter msg]
   (fix-payload->blotter-update account-id asset-converter nil nil msg))
  ([account-id asset-converter cancel-req-atom modify-req-atom [msg-type payload]]
   (case msg-type
     :execution-report
     (execution-report->blotter account-id asset-converter cancel-req-atom modify-req-atom payload)
     :business-message-reject
     (business-message-reject->blotter account-id cancel-req-atom modify-req-atom payload)
     :order-cancel-reject
     (order-cancel-reject->blotter account-id cancel-req-atom modify-req-atom payload)
     :position-report
     (position-report->blotter account-id asset-converter payload)
     nil)))

(defrecord trade-messaging-fix [account asset-converter log cancel-req-atom modify-req-atom]
  p/trade-messaging
  (api-order [{:keys [asset-converter cancel-req-atom modify-req-atom]} blotter-msg-in]
    (blotter-order->fix-payload blotter-msg-in asset-converter cancel-req-atom modify-req-atom))
  (blotter-order-update [{:keys [account asset-converter cancel-req-atom modify-req-atom]} api-msg-in]
    (fix-payload->blotter-update (:account/id account) asset-converter
                                 cancel-req-atom modify-req-atom api-msg-in)))

(defmethod p/create-trade-messaging :fix-trade
  [account asset-converter log]
  (trade-messaging-fix. account asset-converter log (atom {}) (atom {})))
