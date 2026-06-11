(ns fix-engine.quote.fix-quote
  (:require
   [clojure.set :refer [rename-keys]]
   [nano-id.core :refer [nano-id]]
   [quanta.quote.protocol :as p]
   [fix-translator.ctrader :refer [get-asset-id get-asset-name]]
   [fix-engine.impl.tcp.boot :refer [boot-with-retry]]
   [fix-engine.impl.interactor.quote :refer [create-quote-interactor]]))

(defmethod p/create-quote-account :fix-quote
  [{:keys [account/id] :as account-config} subscription-a send-quote log]
  (let [interactor (create-quote-interactor subscription-a send-quote)]
    (boot-with-retry account-config log interactor)))

;; subscription management

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

;; process incoming quote

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


;; protocol implementation

(defrecord quote-feed-fix [account-config asset-converter log]
  p/quote-messaging
  (subscribe-msg [_ sub]
    (let [asset-ids (mapv #(get-asset-id asset-converter %) sub)
          msg (subscribe-payload asset-ids)]
      (log {:type :subscribe :assets sub :broker-assets asset-ids})
      msg))
  (unsubscribe-msg [_ unsub]
    (let [asset-ids (mapv #(get-asset-id asset-converter %) unsub)
          msg (unsubscribe-payload asset-ids)]
      (log {:type :unsubscribe :assets unsub :broker-assets asset-ids})
      msg))
  (read-quote [_ fix-vec-in]
    (let [[msg-type payload] fix-vec-in]
      (case msg-type

        :market-data-snapshot-full-refresh ; full refresh for top-of-book
        (when-let [q (payload->quote fix-vec-in)]
          (normalize-quote asset-converter q))

        :market-data-incremental-refresh ; incremental refresh for orderbook
        (when-let [q (payload->quote fix-vec-in)]
          (println "incremental refresh: " q)
          (normalize-quote asset-converter q))

        :market-data-request-reject
        (log {:type :subscription-failure :direction :in :data payload})

        :logout
        (throw (ex-info "session-reset" {:msg "logout message received" :text (str payload)}))

        nil))))


(defmethod p/create-quote-messaging :fix-quote
  [account-config asset-converter log]
  (quote-feed-fix. account-config asset-converter log))
