(ns demo.cli-trade
  (:require
   [clojure.edn :as edn]
   [missionary.core :as m]
   [tick.core :as t]
   [fix-engine.impl.log-flow :refer [flow-sender]]
   [fix-engine.impl.tcp.boot :refer [boot-with-retry]]
   [fix-engine.impl.interactor.trade :refer [create-trade-interactor]]
   [fix-engine.logger :refer [log]]))

(defn- load-account [kw]
  (get (edn/read-string (slurp "fix-accounts.edn")) kw))

(defn- account-log-fn [account-config send subscriber-ready?]
  (fn [event]
    (let [e (merge {:account/id (:account/id account-config)
                    :account/name (:account/name account-config)}
                   event)]
      (when @subscriber-ready?
        (send e)))))

(defn- log-event-printer [log-f]
  (m/reduce
   (fn [_ event]
     (log "EVENT" event)
     nil)
   nil
   log-f))

(def demo-symbols ["EURUSD" "GBPUSD" "EURJPY" "USDJPY"])
(def demo-sides [:buy :sell])
(def demo-ord-types [:market])

(defn- random-order []
  (let [ord-type (rand-nth demo-ord-types)]
    {:action :new-order
     :ts (t/instant)
     :symbol (rand-nth demo-symbols)
     :side (rand-nth demo-sides)
     :ord-type ord-type
     :price (when (= :limit ord-type) (double (+ 1.0 (rand 2))))}))

(defn trade-update-consumer
  "Takes execution-report updates from res-rdv."
  [res-rdv]
  (m/sp
   (loop []
     (let [update (m/? res-rdv)]
       (log "TRADE-UPDATE" update)
       (recur)))))

(defn order-sender-loop
  "Every 5s gives one order request on req-rdv."
  [req-rdv]
  (m/sp
   (loop []
     (m/? (m/sleep 5000))
     (m/? (req-rdv (random-order)))
     (recur))))

(defn start
  [account-kw]
  (let [account-config (load-account account-kw)
        _ (when-not account-config
            (throw (ex-info "missing account in fix-accounts.edn" {:account account-kw})))
        {:keys [flow send]} (flow-sender)
        subscriber-ready? (atom false)
        log-fn (account-log-fn account-config send subscriber-ready?)
        req-rdv (m/rdv)
        res-rdv (m/rdv)
        interactor (create-trade-interactor req-rdv res-rdv)
        update-consumer-t (trade-update-consumer res-rdv)
        dispose-updates (update-consumer-t #(log "update-consumer done" %)
                                             #(log "update-consumer CRASH" %))
        order-sender-t (order-sender-loop req-rdv)
        dispose-sender (order-sender-t #(log "order-sender done" %)
                                         #(log "order-sender CRASH" %))
        log-consumer (log-event-printer flow)
        dispose-log (do
                      (reset! subscriber-ready? true)
                      (log-consumer #(log "log-consumer done" %)
                                    #(log "log-consumer CRASH" %)))
        boot-t (boot-with-retry account-config log-fn interactor)
        dispose-boot (boot-t #(log "boot completed" %)
                             #(log "boot CRASH" %))]
    {:log-f flow
     :req-rdv req-rdv
     :res-rdv res-rdv
     :dispose-boot dispose-boot
     :dispose-log dispose-log
     :dispose-updates dispose-updates
     :dispose-sender dispose-sender}))

(defn start-cli
  "Usage: clojure -X:cli-trade :account :ctrader-pepperstone-trade-ssl"
  [{:keys [account] :or {account :ctrader-fxpro-trade}}]
  (start account)
  @(promise))
