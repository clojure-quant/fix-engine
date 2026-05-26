(ns fix-engine.core
  (:require
   [clojure.edn :as edn]
   [fix-engine.impl.quotes :refer [create-quote-interactor only-quotes]]
   [fix-engine.impl.trade :refer [create-trade-interactor]]
   [fix-engine.impl.account :refer [create-account-session]]
   [fix-engine.impl.mutil :refer [rlock with-lock]]))

(defn load-accounts [accounts-edn-file]
  (-> accounts-edn-file slurp edn/read-string))

(defn create-fix-engine
  "creates a fix-engine instance"
  [fix-config-file]
  {:accounts (load-accounts fix-config-file)
   :account-lock (rlock)
   :sessions (atom {})})

(defn configured-accounts [fix-engine]
  (-> fix-engine :accounts keys))

(defn get-session
  "gets a session for an account
     this is the fix-engine state (use create-fix-engine)"
  [{:keys [accounts account-lock sessions] :as fix-engine} account-kw interactor]
  (if-let [session (get @sessions account-kw)]
    session
    (if (contains? accounts account-kw)
      (with-lock account-lock
        (println "creating fix session " account-kw)
        (let [account-config (get accounts account-kw)
              session (create-account-session account-config interactor)]
          (println "storing session")
          (swap! sessions assoc account-kw session)
          session))
      (throw (ex-info "unknown account" {:account account-kw :available-accounts (keys accounts)})))))



(defn get-quote-session [fix-engine  account-kw]
  (let [{:keys [session out-f]} (get-session fix-engine account-kw create-quote-interactor)
        ;decoder (create-session (:accounts fix-engine) account-kw)
        ]
    ;session
    (only-quotes session out-f)))


(defn get-trade-session [fix-engine  account-kw]
  (let [{:keys [session out-f]} (get-session fix-engine account-kw create-trade-interactor)
        ;decoder (create-session (:accounts fix-engine) account-kw)
        ]
    ;session
    (only-quotes session out-f)))