(ns fix-engine.impl.interactor.trade
  (:require
   [missionary.core :as m]
   [nano-id.core :refer [nano-id]]
   [tick.core :as t]
   [fix-engine.blotter.trade-mapping :refer [blotter-order->fix-payload fix-payload->blotter-update]])
  (:import missionary.Cancelled))

(def ^:private max-req-age-ms 5000)

(defn- fresh-request? [req]
  (let [ts (:ts req)]
    (and ts (<= (- (System/currentTimeMillis) (.toEpochMilli ^java.time.Instant ts))
                max-req-age-ms))))

(defn- request-loop
  [req-rdv opts asset-converter push log]
  (m/sp
   (loop []
     (let [req (m/? req-rdv)
           _ (println "request: " req)
           fix-payload (blotter-order->fix-payload req asset-converter)]
       (when fix-payload   ;(fresh-request? req)
         (try
           (m/? (push fix-payload))
           (catch Exception ex
             (log {:type :order-failure
                   :direction :out
                   :data {:request req :error (ex-message ex)}}))))
       (recur)))))

(defn- message-loop
  [pull log {:keys [account/id] :as opts} asset-converter res-rdv]
  (m/sp
   (try
     (loop []
       (when-let [fix-payload (m/? (pull))]
         (let [[msg-type _] fix-payload]
           (when (= msg-type :logout)
             (throw (ex-info "session-reset" {:msg "logout message received"})))
           (when-let [update (fix-payload->blotter-update id asset-converter fix-payload)]
             (m/? (res-rdv update))))
         (recur)))
     (catch Cancelled _
       true))))

(defn create-trade-interactor
  [req-rdv res-rdv]
  (fn [account-config _connection-id push pull log asset-converter]
    (m/sp
     (m/? (m/join vector
                  (request-loop req-rdv account-config asset-converter push log)
                  (message-loop pull log account-config asset-converter res-rdv))))))
