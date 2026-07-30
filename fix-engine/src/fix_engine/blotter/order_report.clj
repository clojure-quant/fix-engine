(ns fix-engine.blotter.order-report
  (:require
   [quanta.asset.mapper :as am])
  (:import [java.math BigDecimal]
           [java.time Instant]
           [java.util Date]))

(defn- ->decimal [x]
  (cond
    (nil? x) nil
    (instance? BigDecimal x) x
    (number? x) (bigdec x)
    (string? x) (bigdec x)
    :else (bigdec (str x))))

(defn- ->date [x]
  (cond
    (nil? x) (Date.)
    (instance? Date x) x
    (instance? Instant x) (Date/from x)
    (string? x) (Date/from (Instant/parse x))
    :else (Date.)))

(defn- ->order-id [x]
  (if (string? x) x (str x)))

(defn- fix-ord-type->order-type [ord-type price]
  (or ord-type (if price :limit :market)))

(defn- ord-status->order-status [ord-status]
  (case ord-status
    (:new :partially-filled :replaced) :working
    :filled :filled
    :canceled :cancelled
    :rejected :rejected
    :expired :expired
    (throw (ex-info "unsupported order status for working-order snapshot"
                    {:ord-status ord-status}))))

(defn create-report-consolidator
  [mass-status-req-id]
  {:mass-status-req-id (str mass-status-req-id)
   :responses (atom [])})

(defn add-response
  [{:keys [mass-status-req-id responses]} response]
  (when (= mass-status-req-id (some-> (:req-id response) str))
    (let [total (:total response)]
      (cond
        (= 0 total) []
        (and (number? total) (pos? total))
        (let [responses (swap! responses conj response)]
          (when (= total (count responses))
            responses))
        :else
        (throw (ex-info "order report requires a non-negative :total"
                        {:response response}))))))

(defn execution-report->order
  "Map a mass-status Execution Report payload into a canonical :order/* map
   plus consolidator metadata (:req-id, :total)."
  [account-id asset-converter payload]
  (let [{:keys [cl-ord-id order-id symbol side ord-type price order-qty
                cum-qty leaves-qty avg-px ord-status transact-time text
                pos-maint-rpt-id mass-status-req-id tot-num-reports]} payload
        asset (when symbol (am/from-api asset-converter symbol))
        client-id (or cl-ord-id order-id)
        order-type (fix-ord-type->order-type ord-type price)
        qty (->decimal order-qty)
        filled (or (->decimal cum-qty) 0M)
        working (or (->decimal leaves-qty)
                    (when qty (max 0M (- qty filled))))
        status (ord-status->order-status ord-status)]
    (when-not mass-status-req-id
      (throw (ex-info "order report requires :mass-status-req-id" {:payload payload})))
    (when-not asset
      (throw (ex-info "order report requires a mapped asset" {:payload payload})))
    (when-not client-id
      (throw (ex-info "order report requires a client or broker order id" {:payload payload})))
    (cond-> {:req-id (->order-id mass-status-req-id)
             :total tot-num-reports
             :order/id (->order-id client-id)
             :order/account-id account-id
             :order/asset asset
             :order/side side
             :order/type order-type
             :order/status status
             :order/qty qty
             :order/qty-filled filled
             :order/qty-working (if (#{:cancelled :rejected :expired :filled} status)
                                  0M
                                  working)
             :order/avg-price (->decimal avg-px)
             :order/date (->date transact-time)
             :order/history []}
      (and (= :limit order-type) price) (assoc :order/limit (->decimal price))
      (seq text) (assoc :order/text text)
      pos-maint-rpt-id (assoc :order/position-id (->order-id pos-maint-rpt-id)))))
