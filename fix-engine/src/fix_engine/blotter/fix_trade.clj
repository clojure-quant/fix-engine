(ns fix-engine.blotter.fix-trade
  (:require
   [missionary.core :as m]
   [quanta.blotter.protocol :as p]
   [quanta.blotter.oms.validation.schema :as schema]
   [fix-engine.blotter.trade-mapping :refer [blotter-order->fix-payload]]
   [fix-engine.impl.tcp.boot :refer [boot-with-retry]]
   [fix-engine.impl.interactor.trade :refer [create-trade-interactor]]))

(defn- fix-account-config [account-config]
  (assoc account-config :account/api :fix))

(defn- account-log-fn [account-id log]
  (fn [event]
    (log (assoc event :account/id account-id))))

(defn- wrap-interactor [interactor converter-rdv]
  (fn [account cid push pull log asset-converter]
    (m/sp
     (m/? (converter-rdv asset-converter))
     (m/? (interactor account cid push pull log asset-converter)))))

(defn- order-bridge [order-rdv req-rdv converter-rdv log]
  (m/sp
   (let [asset-converter (m/? converter-rdv)]
     (loop []
       (let [order (m/? order-rdv)]
         (try
           (let [fix-payload (blotter-order->fix-payload order asset-converter)]
             (m/? (req-rdv fix-payload)))
           (catch Exception ex
             (log {:type :order-failure
                   :direction :in
                   :data {:order order :error (ex-message ex)}}))))
       (recur)))))

(defn- update-bridge [res-rdv push log]
  (m/sp
   (loop []
     (let [update (m/? res-rdv)]
       (when update
         (if (schema/validate-message update)
           (m/? (push update))
           (log {:type :orderupdate-validation-failure
                 :data {:message update
                        :errors (schema/human-error-message update)}})))
       (recur)))))

(defmethod p/create-trade-account :fix-trade
  [{:keys [account/id] :as account-config} order-rdv update-rdv log]
  (let [req-rdv (m/rdv)
        res-rdv (m/rdv)
        converter-rdv (m/rdv)
        interactor (create-trade-interactor req-rdv res-rdv
                                            {:account/id id :blotter-mode true})
        boot-account (fix-account-config account-config)
        boot-log (account-log-fn id log)]
    (m/sp
     (m/? (m/join vector
                  (boot-with-retry boot-account boot-log
                                   (wrap-interactor interactor converter-rdv))
                  (order-bridge order-rdv req-rdv converter-rdv log)
                  (update-bridge res-rdv update-rdv log))))))
