(ns fix-engine.blotter.messaging
  (:require
   [nano-id.core :refer [nano-id]]
   [tick.core :as t]
   [quanta.blotter.protocol2 :as p]
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
  (let [{:keys [type order-id asset side qty limit order-type account/id req-id]} order]
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

      :trader/open-positions
      [:request-for-positions {:pos-req-id req-id}]

      :trader/working-orders
      [:order-mass-status-request {:mass-status-req-id req-id
                                   :mass-status-req-type :status-for-all-orders}]

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
                   ;:message (or (:text payload) "")
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
      (when order-id
        {:type :broker/order-canceled
         :account/id account-id
         :order-id order-id
         :date date})

      :expired
      (when order-id
        {:type :broker/order-expired
         :account/id account-id
         :order-id order-id
         :date date})

      :replace ;; what does this mean??
      nil

          ; else 
      nil)))

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


(defn- position-report->blotter [account-id asset-converter payload]
  (let [{:keys [symbol pos-req-id no-positions settl-price total-num-pos-reports pos-maint-rpt-id]} payload
        asset (when symbol
                (get-asset-name asset-converter symbol))]
    {:type :broker/positions-item
     :account/id account-id
     :req-id pos-req-id
     :asset asset
     :position no-positions
     :position-id pos-maint-rpt-id
     :settl-price settl-price
     :total total-num-pos-reports}))

#_[:position-report
   {:symbol "1"
    :pos-req-id "GQgHl"
    :pos-maint-rpt-id "221436915"
    :total-num-pos-reports 4
    :pos-req-result :valid-request
    :settl-price 1.16395M
    :no-positions [{:long-qty 1000M :short-qty 0M}]}]

(defn fix-payload->blotter-update
  "fix-translator [msg-type payload] -> blotter broker message or nil."
  [account-id asset-converter [msg-type payload]]
  (case msg-type
    :execution-report (execution-report->blotter account-id asset-converter payload)
    :business-message-reject (business-message-reject->blotter account-id payload)
    :order-cancel-reject (order-cancel-reject->blotter account-id payload)
    :position-report (position-report->blotter account-id asset-converter payload)
    nil))

(defrecord trade-messaging-fix [account asset-converter log]
  p/trade-messaging
  (api-order [_ blotter-msg-in]
    (blotter-order->fix-payload blotter-msg-in asset-converter))
  (blotter-order-update [_ api-msg-in]
    (fix-payload->blotter-update (:account/id account) asset-converter api-msg-in)))

(defmethod p/create-trade-messaging :fix-trade
  [account asset-converter log]
  (trade-messaging-fix. account asset-converter log))
