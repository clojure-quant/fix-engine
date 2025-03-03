(ns fix-engine.boot
  (:require
   [missionary.core :as m]
   [fix-engine.impl.socket :refer [create-client]]
   [fix-engine.logger :refer [log log-time]])
  (:import missionary.Cancelled))

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
  [this interactor current-conn]
  (m/sp
   (log "connector" "start")
   (let [[exit conn] (try [nil (m/? (create-client this))]
                          (catch java.net.UnknownHostException _
                            (log-time "connector" "host unknown - cannot connect")
                            [:host-unknown nil])
                          ;   (catch java.net.ConnectException e
                          (catch Exception ex
                            (log-time "connector" (str "connect exception " (ex-message ex) (ex-data ex)))
                            [:connect-ex nil]))]
     (if conn
         ; connection established
       (try
         (log-time "connector" "connection created, now setting conn-rdv")
         ;(m/? (conn-rdv conn))
         (reset! current-conn conn)
         (log-time "connector" "conn-rdv set, now starting interactor")
         (m/? (interactor this conn))
         :run-finally
         (catch Exception ex
           (log-time "connector" (str "exception " (ex-message ex) (ex-data ex)))
           :run-ex))
         ; connnect err
       exit))))

(defn forever [task]
  (m/ap (m/? (m/?> 100 (m/seed (repeat task))))))

(defn boot-with-retry [this interactor set-in-t]
  (m/sp
   (log-time "boot" "started")
   (let [current-conn (atom nil)
                    ;conn-rdv (m/rdv) ; sync rendevouz
                    ;conn-f (forever conn-rdv)
         conn-f (m/watch current-conn)
         in-f (m/stream
               (m/ap
                (let [conn (m/?> 100 conn-f)
                      in-f (:in-flow conn)]
                  (if in-f
                    (do (log-time "flow-forwarder" "new in-f")
                        (try
                          (let [data (m/?> 100 in-f)]
                            ;(log "flow-forwarder" data)
                            data)
                          (catch Cancelled _
                            (log-time "flow-forwarder" "got cancelled")
                                                 ;(m/? shutdown!)
                            :flow-forwarder-cancelled)
                          (catch Exception ex
                            (log-time "flow-forwarder ex" {:data (ex-data ex) :msg (ex-message ex)})
                            (m/amb))))
                    (do
                      (log-time "flow-forwarder" "received nil conn.")
                      (m/amb))))))]
     (log-time "boot" "set-in-f ..")
     (set-in-t in-f)
     (loop [delays retry-delays]
       (log-time "boot" "connecting..")
       (when-some [[delay & delays]
                   (when-some [exit (m/? (connect-and-run this interactor current-conn))]
                     (log-time "boot" (str "exit code: " exit))
                     (case exit
                       :host-unknown nil ; no reconnects
                       :connect-ex delays ; increasing delays
                       :run-ex (seq retry-delays)
                       :run-finally (seq retry-delays)
                       true nil ; no reconnects on cancel
                       delays))]
         (log-time "boot" (str "Next attempt in " (/ delay 1000) " seconds."))
         (recur (m/? (m/sleep delay delays))))))))



