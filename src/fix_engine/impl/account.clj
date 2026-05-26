(ns fix-engine.impl.account
  (:require
   [missionary.core :as m]
   [fix-engine.impl.session :refer [create-session]]
   [fix-engine.boot :refer [boot-with-retry]]
   [fix-engine.logger :refer [log log-time]])
  (:import missionary.Cancelled))

(defn create-account-session [account-config create-interactor]
  ;(log "acc" (str "loading fix account " account-kw))
  (let [session (create-session account-config)
        _ (println "session created")
        get-in-t (m/dfv) ; single assignment variable
        interactor (create-interactor)
        boot-t (boot-with-retry session interactor get-in-t)
        session-out-f (m/stream
                       (m/ap
                        (let [dispose! (boot-t #(log-time "boot-task completed" %)
                                               #(log-time "boot-task crash " %))
                              _ (log-time "acc" "waiting to get input flow..")
                              in-f (m/? get-in-t)]
                          (if in-f
                            (try
                              (log "acc" "got the in-flow!")
                              (let [msg (m/?> 100 in-f)]
                                    ;(log-time "acc in" msg) ; this only tests the quote forwarding really
                                msg)
                              (catch Cancelled _
                                (log-time "acc" "got cancelled")
                                (dispose!))
                              (catch Exception _
                                (log-time "acc" "got exception"))
                                  ;(finally
                                        ;  (log "acc" "finally!")
                                        ;  (dispose!)
                                        ;  (log "acc" "disposed success")
                                    ;)
                              )
                            (log-time "acc" "flow is nil.")))))]
    {:session session
     :out-f session-out-f}))
