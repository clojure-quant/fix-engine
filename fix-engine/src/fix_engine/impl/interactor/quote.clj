(ns fix-engine.impl.interactor.quote
  (:require
   [clojure.set :refer [rename-keys]]
   [missionary.core :as m]
   [nano-id.core :refer [nano-id]]
   [quanta.quote.subscription :refer [sub-unsub-sets]]
   [fix-translator.ctrader :refer [get-asset-id get-asset-name]])
  (:import missionary.Cancelled))


(defn- dbg [& args]
  (apply println "[interactor.quote]" args)
  (flush))

(defn- eventually-add-last-volume [{:keys [bid ask] :as quote}]
  (if (and bid ask)
    (assoc quote :price (/ (+ bid ask) 2.0M)
           :volume 1.0M
           :spread (- ask bid))
    (assoc quote :volume 0.0M)))

(defn- payload->quote
  [[msg-type {:keys [symbol no-mdentries]}]]
  (when (= msg-type :market-data-snapshot-full-refresh)
    (let [quote (reduce (fn [s {:keys [mdentry-type mdentry-px]}]
                          (assoc s mdentry-type mdentry-px))
                        {} no-mdentries)]
      (-> quote
          (rename-keys {:offer :ask})
          (assoc :asset symbol)
          (eventually-add-last-volume)))))

(defn- normalize-quote [asset-converter quote]
  (update quote :asset #(get-asset-name asset-converter %)))

(defn- subscribe-payload [asset-ids]
  [:market-data-request
   {:mdreq-id (nano-id 5)
    :subscription-request-type :snapshot-plus-updates
    :market-depth 1
    :mdupdate-type :incremental-refresh
    :no-mdentry-types [{:mdentry-type :bid} {:mdentry-type :offer}]
    :no-related-sym (mapv (fn [asset-id] {:symbol asset-id}) asset-ids)}])

(defn- unsubscribe-payload [asset-ids]
  [:market-data-request
   {:mdreq-id (nano-id 5)
    :subscription-request-type :disable-previous-snapshot-plus-update-request
    :market-depth 1
    :mdupdate-type :incremental-refresh
    :no-mdentry-types [{:mdentry-type :bid} {:mdentry-type :offer}]
    :no-related-sym (mapv (fn [asset-id] {:symbol asset-id}) asset-ids)}])


(defn process-subscription-changes [subscription-f asset-converter push session-log]
  (m/ap
   (let [assets-old (atom #{})
         assets-new (m/?> 1 subscription-f)
         _  (session-log {:type :subscriptions :assets assets-new})
         {:keys [sub unsub]} (sub-unsub-sets @assets-old assets-new)]
       (reset! assets-old assets-new)
       ; subscribe
       (when (seq sub)
         (let [asset-ids (mapv #(get-asset-id asset-converter %) sub)
               msg (subscribe-payload asset-ids)]
           (session-log {:type :subscribe :assets sub :broker-assets asset-ids})
           (m/? (push msg))))
       ; unsubscribe
       (when (seq unsub)
         (let [asset-ids (mapv #(get-asset-id asset-converter %) unsub)
               msg (unsubscribe-payload asset-ids)]
           (session-log {:type :unsubscribe :assets unsub :broker-assets asset-ids})
           (m/? (push msg))))
       )))


(defn- subscription-watcher
  [subscription-a asset-converter push session-log]
  (let [sub-f (m/watch subscription-a)
        sub-f (m/relieve sub-f)
        sub-process-f (process-subscription-changes sub-f asset-converter push session-log)]
    (m/reduce (fn [_ _] nil) nil sub-process-f)))


(defn- message-loop
  [pull session-log asset-converter send-quote]
  (m/sp
   (try
     (loop []
       (when-let [fix-payload (m/? (pull))]
         (let [[msg-type payload] fix-payload]
           (when (= msg-type :logout)
             (throw (ex-info "session-reset" {:msg "logout message received" :text (str payload)})))
           (when (= msg-type :market-data-request-reject)
             (session-log {:type :subscription-failure :direction :in :data payload}))
           (when-let [q (payload->quote fix-payload)]
             (let [normalized (normalize-quote asset-converter q)]
               (send-quote normalized))))
         (recur)))
     (catch Cancelled _
       true))))

(defn create-quote-interactor
  "quote-rdv: missionary rendezvous from (m/rdv); consumer takes with (m/? quote-rdv),
   producer gives with (m/? (quote-rdv value))."
  [subscription-a send-quote]
  (fn [_fix-account-config _connection-id push pull session-log asset-converter]
    (m/sp
     (dbg "interactor started")
     (m/? (m/join vector
                  (subscription-watcher subscription-a asset-converter push session-log)
                  (message-loop pull session-log asset-converter send-quote))))))
