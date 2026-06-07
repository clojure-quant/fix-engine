(ns fix-engine.impl.interactor.quote
  (:require
   [clojure.set :refer [rename-keys]]
   [missionary.core :as m]
   [nano-id.core :refer [nano-id]]
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

(defn- subscription-watcher
  [subscription-a asset-converter push session-log]
  (m/sp
   (m/? (m/reduce
         (fn [_ symbols]
           (when (seq symbols)
             (let [asset-ids (mapv #(get-asset-id asset-converter %) symbols)
                   msg (subscribe-payload asset-ids)]
               (dbg "subscription-watcher: subscribe" symbols "->" asset-ids)
               (try
                 (m/? (push msg))
                 (dbg "subscription-watcher: subscribe ok")
                 (catch Exception ex
                   (dbg "subscription-watcher: subscribe failed" (ex-message ex))
             (session-log {:type :subscription-failure
                            :direction :out
                            :data {:symbols symbols :error (ex-message ex)}})))))
           nil)
         nil
         (m/ap (m/?> ##Inf (m/watch subscription-a)))))))

(defn- message-loop
  [pull session-log asset-converter quote-rdv]
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
               (dbg "quote-rdv give" (:asset normalized))
               (m/? (quote-rdv normalized)))))
         (recur)))
     (catch Cancelled _
       true))))

(defn create-quote-interactor
  "quote-rdv: missionary rendezvous from (m/rdv); consumer takes with (m/? quote-rdv),
   producer gives with (m/? (quote-rdv value))."
  [subscription-a quote-rdv]
  (fn [_fix-account-config _connection-id push pull session-log asset-converter]
    (m/sp
     (dbg "interactor started")
     (m/? (m/join vector
                  (subscription-watcher subscription-a asset-converter push session-log)
                  (message-loop pull session-log asset-converter quote-rdv))))))
