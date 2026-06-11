(ns fix-engine.blotter.fix-trade
  (:require
   [missionary.core :as m]
   [quanta.blotter.protocol :as p]
   [quanta.blotter.oms.validation.schema :as schema]
   [fix-engine.blotter.trade-mapping :refer [blotter-order->fix-payload]]
   [quanta.util.boot :refer [boot-with-retry]]
   [fix-engine.impl.connect]
   [fix-engine.impl.interactor.trade :refer [create-trade-interactor]]))

(defn- fix-account-config [account-config]
  (assoc account-config :account/api :fix))

(defn- account-log-fn [account-id log]
  (fn [event]
    (log (assoc event :account/id account-id))))

(defn- order-bridge [order-rdv req-rdv log]
  (m/sp
   (loop []
     (let [order (m/? order-rdv)]
       (try
         (m/? (req-rdv order))
         (catch Exception ex
           (log {:type :order-failure
                 :direction :in
                 :data {:order order :error (ex-message ex)}}))))
     (recur))))

(defn- update-bridge [res-rdv push log]
  (m/sp
   (loop []
     (let [update (m/? res-rdv)]
       (when update
         (m/? (push update))))
     (recur))))

(defmethod p/create-trade-account :fix-trade
  [account order-rdv update-rdv log]
  (let [account (assoc account :account/session :fix)
        {:keys [account/id]} account
        req-rdv (m/rdv)
        res-rdv (m/rdv)
        interactor (create-trade-interactor req-rdv res-rdv)
        boot-account (fix-account-config account)
        boot-log (account-log-fn id log)]
    (m/sp
     (m/? (m/join vector
                  (boot-with-retry boot-account boot-log interactor)
                  (order-bridge order-rdv req-rdv log)
                  (update-bridge res-rdv update-rdv log))))))
