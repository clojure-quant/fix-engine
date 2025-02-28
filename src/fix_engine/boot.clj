(ns fix-engine.boot
  (:require
   [missionary.core :as m]
   [fix-engine.socket :refer [create-client]]
   [fix-engine.logger :refer [log]]
   ))

(defn fib-iter [[a b]]
  (case b
    0 [1 1]
    [b (+ a b)]))

(def fib (map first (iterate fib-iter [1 1])))

(comment 
  (take 20 fib)
  ;(1 1 2 3 5 8 13 21 34 55 89 144 233 377 610 987 1597 2584 4181 6765) 
;  
  )

(def retry-delays (map (partial * 100) (next fib)))

(defn r-subject-at [^objects arr slot]
  (fn [!]
    (aset arr slot !)
    #(aset arr slot nil)))

(defn connect-and-run "
server : the server part of the program
cb : the callback for incoming messages.
msgs : the discrete flow of messages to send, spawned when websocket is connected, cancelled on websocket close.
Returns a task producing nil or failing if the websocket was closed before end of reduction. "
  [this interactor conn-rdv]
  (m/sp
   (log "connector" "start")
   (if-some [conn (m/? (create-client this))]
     ; then
     (try
       (log "connector" "connection created, now setting conn-rdv")
       (m/? (conn-rdv conn))
       (log "connector" "conn-rdv set, now starting interactor")
       (m/? (interactor this conn))
       (finally
         (log "connector" "has finished")
         ;(when-not (= (.-CLOSED js/WebSocket) (.-readyState ws))
         ;  (.close ws)
         ;  (m/? (m/compel wait-for-close))
         true
         ))
     ; else
     (do 
       (log "connector" "could not create fix session.")
       true
       )
     )))

(defn boot-with-retry [this interactor set-in-t]
  (m/sp
    (log "boot" "started")
   (loop [delays retry-delays]
     (let [s (object-array 1)
           conn-rdv (m/rdv)
           in-f (m/ap
                 (let [in-f (:in-flow (m/? conn-rdv))]
                   (loop []
                       (m/amb (m/?> in-f) (recur))
                       )))]
       (log "boot" "set-in-f ..")
       (set-in-t in-f)
       (log "boot" "connecting..")
       (when-some [[delay & delays]
                   (when-some [info (m/? (connect-and-run this interactor conn-rdv))]
                     (if-some [code (:code info)]
                       (let [retry? (case code
                                      1008
                                      (throw (ex-info "Stale Electric client" {:hyperfiddle.electric/type ::stale-client}))

                                      1013 ; server timeout - The WS spec defines 1011 - arbitrary server error,
                                      (do (println "Electric server timed out, considering this Electric client inactive.")
                                          true)
                                      ; else
                                      (do (println (str "fix socket disconnected for an unexpected reason - " (pr-str info)))
                                          true))]
                         (when retry?
                           (seq retry-delays)))
                       (do (println "FIX client failed to connect")
                           delays)))]
         (log "boot" (str "Next attempt in " (/ delay 1000) " seconds."))
         (recur (m/? (m/sleep delay delays))))))))



