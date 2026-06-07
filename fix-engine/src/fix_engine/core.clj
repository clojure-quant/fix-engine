(ns fix-engine.core
  (:require
   [missionary.core :as m]
   [fix-engine.account :as account]
   [fix-engine.impl.tcp.boot :refer [boot-with-retry]]
   [fix-engine.impl.interactor.quote :refer [create-quote-interactor]]
   [fix-engine.impl.interactor.trade :refer [create-trade-interactor]]
   [fix-engine.impl.mutil :refer [rlock with-lock]]))

(defn load-accounts [accounts-edn-file]
  (account/load-accounts-file accounts-edn-file))

(defn create-fix-engine
  "Creates a fix-engine instance from an EDN file (vector of accounts indexed by `:account/name`)."
  [fix-config-file]
  {:accounts (load-accounts fix-config-file)
   :account-lock (rlock)
   :sessions (atom {})})

(defn configured-accounts [fix-engine]
  (account/configured-names (:accounts fix-engine)))

(defn- start-session!
  [{:keys [accounts sessions account-lock] :as _fix-engine} account-ref log interactor]
  (let [account-name (account/as-account-name account-ref)]
    (with-lock account-lock
      (when-not (contains? @sessions account-name)
        (let [account-config (get accounts account-name)
              boot-t (boot-with-retry account-config log interactor)]
          (swap! sessions assoc account-name {:boot-t boot-t
                                              :account-config account-config})))
      (get @sessions account-name))))

(defn start-quote-session
  "Starts quote boot for `account-name` (`:account/name` string). Caller provides `log` (from flow-sender).
   Returns {:quote-rdv :subscription-a :session ...}."
  [fix-engine account-name subscription-a log]
  (let [quote-rdv (m/rdv)
        interactor (create-quote-interactor subscription-a quote-rdv)
        session (start-session! fix-engine account-name log interactor)]
    (assoc session :quote-rdv quote-rdv :subscription-a subscription-a)))

(defn start-trade-session
  "Starts trade boot for `account-name`. Caller provides req-rdv, res-rdv, and `log`."
  [fix-engine account-name req-rdv res-rdv log]
  (let [interactor (create-trade-interactor req-rdv res-rdv)
        session (start-session! fix-engine account-name log interactor)]
    (assoc session :req-rdv req-rdv :res-rdv res-rdv)))

(defn get-session
  [{:keys [sessions] :as fix-engine} account-ref]
  (let [account-name (account/as-account-name account-ref)]
    (or (get @sessions account-name)
        (throw (ex-info "session not started" {:account/name account-name
                                               :available (keys @sessions)})))))
