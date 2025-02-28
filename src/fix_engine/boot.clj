(ns fix-engine.boot
  (:require
   [missionary.core :as m]
   [fix-engine.socket :refer [create-client]]
   [fix-engine.logger :refer [log]]))

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
   (try
     (let [[exit conn] (try [nil (m/? (create-client this))]
                            (catch java.net.UnknownHostException _
                              (log "connector" "host unknown - cannot connect")
                              [:host-unknown nil])
                            (catch Exception ex
                              (log "connector" (str "connect exception " (ex-message ex) (ex-data ex)))
                              [:connect-ex nil]))]
       (if conn 
         ; connection established
         (try 
           (log "connector" "connection created, now setting conn-rdv")
           (m/? (conn-rdv conn))
           (log "connector" "conn-rdv set, now starting interactor")
           (m/? (interactor this conn))
           (catch Exception ex
             (log "connector" (str "exception " (ex-message ex) (ex-data ex)))
             :run-ex)
           (finally
             (log "connector" "has finished")
                                    ;(when-not (= (.-CLOSED js/WebSocket) (.-readyState ws))
                                    ;  (.close ws)
                                    ;  (m/? (m/compel wait-for-close))
             :run-finally))
         ; connnect err
         exit)))))

(defn boot-with-retry [this interactor set-in-t]
  (m/sp
   (log "boot" "started")
   (loop [delays retry-delays]
     (let [conn-rdv (m/rdv)
           in-f (m/ap
                 (let [in-f (:in-flow (m/? conn-rdv))]
                   (loop []
                     (m/amb (m/?> in-f) (recur)))))]
       (log "boot" "set-in-f ..")
       (set-in-t in-f)
       (log "boot" "connecting..")
       (when-some [[delay & delays]
                   (when-some [exit (m/? (connect-and-run this interactor conn-rdv))]
                     (log "boot" (str "exit code: " exit))
                     (case exit
                       :host-unknown nil ; no reconnects
                       :connect-ex delays
                       :run-ex (seq retry-delays)
                       :run-finally (seq retry-delays)
                       delays))]
         (log "boot" (str "Next attempt in " (/ delay 1000) " seconds."))
         (recur (m/? (m/sleep delay delays))))))))



