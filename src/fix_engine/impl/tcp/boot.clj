(ns fix-engine.impl.tcp.boot
  (:require
   [missionary.core :as m]
   [fix-engine.impl.tcp.socket :as socket]
   [fix-engine.impl.fix-session :as fix-session])
  (:import missionary.Cancelled))

(defn- dbg [& args]
  (apply println "[boot]" args)
  (flush))

(defn forever
  "Runs `task` repeatedly (one at a time; restarts after each completion)."
  [task]
  (m/ap (m/? (m/?> 1 (m/seed (repeat task))))))

(defn- fib-iter [[a b]]
  (case b
    0 [1 1]
    [b (+ a b)]))

(def ^:private fib (map first (iterate fib-iter [1 1])))
(def ^:private retry-delays (map (partial * 100) (next fib)))

(defn- connect-and-run [fix-account-config log interactor]
  (m/sp
   (dbg "connect-and-run: begin" (select-keys (:tcp fix-account-config) [:host :port :ssl]))
   (try
     (dbg "connect-and-run: tcp connect ...")
     (let [tcp-socket (m/? (socket/connect fix-account-config))
           _ (dbg "connect-and-run: tcp connected, creating session")
           {:keys [run connection-id]} (fix-session/create-fix-session-task fix-account-config tcp-socket log interactor)]
       (dbg "connect-and-run: session connection-id=" connection-id "running ...")
       (m/? run)
       (dbg "connect-and-run: session run finished")
       :run-finally)
     (catch java.net.UnknownHostException e
       (dbg "connect-and-run: UnknownHostException" (.getMessage e))
       :host-unknown)
     (catch Exception e
       (dbg "connect-and-run: Exception" (ex-message e))
       (.printStackTrace e)
       :connect-ex))))

(defn boot-with-retry
  "Connects with fibonacci backoff and runs fix-session until disconnect or failure.
   `log` is a function of one event map (created by the caller with flow-sender)."
  [fix-account-config log interactor]
  (m/sp
   (dbg "boot-with-retry: started")
   (loop [delays retry-delays n 1]
     (dbg "boot-with-retry: attempt" n)
     (if-some [exit (m/? (connect-and-run fix-account-config log interactor))]
       (let [next-delays (case exit
                           :host-unknown nil
                           :connect-ex delays
                           :run-finally (seq retry-delays)
                           :cancelled nil
                           delays)]
         (dbg "boot-with-retry: exit=" exit "will-retry?" (boolean next-delays))
         (if-some [backoff-ms (first next-delays)]
           (do
             (dbg "boot-with-retry: sleeping" backoff-ms "ms before retry")
             (m/? (m/sleep backoff-ms))
             (recur (rest next-delays) (inc n)))
           (dbg "boot-with-retry: done, no more retries")))
       (dbg "boot-with-retry: connect returned nil")))))
