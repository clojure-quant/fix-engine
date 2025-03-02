(ns fix-engine.account
  (:require
   [missionary.core :as m]
   [fix-translator.session :refer [load-accounts create-session]]
   [fix-engine.boot :refer [boot-with-retry]]
   [fix-engine.logger :refer [log]])
  (:import missionary.Cancelled))

(defn create-decoder [fix-config-file account-kw]
  (-> (load-accounts fix-config-file)
      (create-session account-kw)))

(defn account-session [config-file account-kw interactor]
  (log "acc" (str "loading fix account " account-kw))
  (let [this (create-decoder config-file account-kw)
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