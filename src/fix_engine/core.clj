(ns fix-engine.core
  (:require
   [clojure.edn :as edn]
   [missionary.core :as m]
   [fix-engine.impl.tcp.boot :refer [boot-with-retry]]
   [fix-engine.impl.interactor.quote :refer [create-quote-interactor]]
   [fix-engine.impl.interactor.trade :refer [create-trade-interactor]]
   [fix-engine.impl.mutil :refer [rlock with-lock]]))

(defn load-accounts [accounts-edn-file]
  (-> accounts-edn-file slurp edn/read-string))

(defn create-fix-engine
  "Creates a fix-engine instance from an EDN file of account-key -> fix-account-config."
  [fix-config-file]
  {:accounts (load-accounts fix-config-file)
   :account-lock (rlock)
   :sessions (atom {})})

(defn configured-accounts [fix-engine]
  (-> fix-engine :accounts keys))

(defn- start-session!
  [{:keys [accounts sessions account-lock] :as _fix-engine} account-kw log interactor]
  (with-lock account-lock
    (when-not (contains? @sessions account-kw)
      (let [account-config (get accounts account-kw)
            boot-t (boot-with-retry account-config log interactor)]
        (swap! sessions assoc account-kw {:boot-t boot-t
                                          :account-config account-config})))
    (get @sessions account-kw)))

(defn start-quote-session
  "Starts quote boot for account-kw. Caller provides `log` (from flow-sender).
   Returns {:quote-rdv :subscription-a :session ...}."
  [fix-engine account-kw subscription-a log]
  (let [quote-rdv (m/rdv)
        interactor (create-quote-interactor subscription-a quote-rdv)
        session (start-session! fix-engine account-kw log interactor)]
    (assoc session :quote-rdv quote-rdv :subscription-a subscription-a)))

(defn start-trade-session
  "Starts trade boot for account-kw. Caller provides req-rdv, res-rdv, and `log`."
  [fix-engine account-kw req-rdv res-rdv log]
  (let [interactor (create-trade-interactor req-rdv res-rdv)
        session (start-session! fix-engine account-kw log interactor)]
    (assoc session :req-rdv req-rdv :res-rdv res-rdv)))

(defn get-session
  [{:keys [sessions] :as fix-engine} account-kw]
  (or (get @sessions account-kw)
      (throw (ex-info "session not started" {:account account-kw
                                             :available (keys @sessions)}))))
