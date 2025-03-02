(ns fix-engine.impl.account
  (:require
   [missionary.core :as m]
   [fix-translator.session :refer [load-accounts create-session]]
   [fix-engine.boot :refer [boot-with-retry]]
   [fix-engine.logger :refer [log]]
   [fix-engine.impl.mutil :refer [rlock with-lock]])
  (:import missionary.Cancelled))

(defn create-account-session [{:keys [accounts] :as this} account-kw interactor]
  (log "acc" (str "loading fix account " account-kw))
  (let [this (create-session accounts account-kw) 
        _ (println "session created")
        get-in-t (m/dfv) ; single assignment variable
        boot-t (boot-with-retry this interactor get-in-t)]
    (m/stream
     (m/ap
      (let [dispose! (boot-t #(log "boot-task completed" %)
                             #(log "boot-task crash " %))
            _ (log "acc" "waiting to get input flow..")
            in-f (m/? get-in-t)]
        (if in-f
          (try
            (log "acc" "got the in-flow!")
            (let [msg (m/?> 100 in-f)]
              (log "acc in" msg)
              msg)
            (catch Cancelled _
              (log "acc" "got cancelled")
              (dispose!))
            (catch Exception _
              (log "acc" "got exception"))
            ;(finally
                  ;  (log "acc" "finally!")
                  ;  (dispose!)
                  ;  (log "acc" "disposed success")
              ;)
            )
          (log "acc" "flow is nil.")))))))


(defn create-fix-engine [fix-config-file]
  {:accounts (load-accounts fix-config-file)
   :account-lock (rlock)
   :sessions (atom {})})

(defn get-session [{:keys [accounts account-lock sessions] :as this} account-kw interactor]
  (if-let [session (get @sessions account-kw)]
    session 
    (if (contains? accounts account-kw)
      (with-lock account-lock
        (println "creating fix session " account-kw)
        (let [session (create-account-session this account-kw interactor)]
          (println "storing session")
          (swap! sessions assoc account-kw session)
          session))
      (throw (ex-info "unknown account" {:account account-kw :available-accounts (keys accounts)})))))

