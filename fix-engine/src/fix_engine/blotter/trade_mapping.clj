(ns fix-engine.blotter.trade-mapping
  (:require
   [nano-id.core :refer [nano-id]]
   [tick.core :as t]
   [fix-translator.ctrader :refer [get-asset-id get-asset-name]])
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

(defn blotter-order->fix-payload
  "Blotter trader order -> fix-translator [msg-type payload]."
  [order asset-converter]
  (let [{:keys [type order-id asset side qty limit order-type account/id]} order]
    (case type
      :trader/new-order
      (let [_ (validate-order-type order-type limit)
            symbol-id (get-asset-id asset-converter asset)]
        (cond-> [:new-order-single {:cl-ord-id (->order-id order-id)
                                    :symbol symbol-id
                                    :side side
                                    :transact-time (t/instant)
                                    :order-qty (->decimal qty)
                                    :ord-type order-type}]
          (= :limit order-type) (update 1 assoc :price (->decimal limit))))

      :trader/cancel-order
      [:order-cancel-request {:orig-cl-ord-id (->order-id order-id)
                              :cl-ord-id (nano-id 8)}]

      (throw (ex-info "unsupported blotter order type"
                      {:type type :account/id id})))))

(defn- execution-report->blotter
  [account-id asset-converter payload]
  (let [asset (when-let [s (:symbol payload)]
                (get-asset-name asset-converter s))
        order-id (or (:cl-ord-id payload) (:order-id payload))
        date (->instant (:transact-time payload))
        exec-type (:exec-type payload)
        ord-status (:ord-status payload)]
    (cond
      (or (= exec-type :new) (= ord-status :new))
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
                   :message (or (:text payload) "order confirmed")}
            (= :limit order-type) (assoc :limit (->decimal (:price payload))))))

      (or (= exec-type :trade)
          (= ord-status :partially-filled)
          (= ord-status :filled))
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

      (or (= exec-type :rejected) (= ord-status :rejected))
      (when order-id
        {:type :broker/order-rejected
         :account/id account-id
         :order-id order-id
         :date date
         :message (or (:text payload) (some-> (:ord-rej-reason payload) str))})

      (or (= exec-type :canceled) (= ord-status :canceled))
      (when order-id
        {:type :broker/order-canceled
         :account/id account-id
         :order-id order-id
         :date date})

      :else nil)))

(defn- business-message-reject->blotter
  [account-id payload]
  (when-let [order-id (:business-reject-ref-id payload)]
    {:type :broker/order-rejected
     :account/id account-id
     :order-id (->order-id order-id)
     :date (t/instant)
     :message (or (:text payload)
                  (some-> (:business-reject-reason payload) name)
                  "business message rejected")}))

(defn- order-cancel-reject->blotter
  [account-id payload]
  (when-let [order-id (or (:orig-cl-ord-id payload) (:cl-ord-id payload))]
    {:type :broker/cancel-rejected
     :account/id account-id
     :order-id (->order-id order-id)
     :message (or (:text payload) "cancel rejected")}))

(defn fix-payload->blotter-update
  "fix-translator [msg-type payload] -> blotter broker message or nil."
  [account-id asset-converter [msg-type payload]]
  (case msg-type
    :execution-report (execution-report->blotter account-id asset-converter payload)
    :business-message-reject (business-message-reject->blotter account-id payload)
    :order-cancel-reject (order-cancel-reject->blotter account-id payload)
    nil))
