(ns fix-engine.impl.connect
  (:require
   [missionary.core :as m]
   [quanta.util.session :as p]
   [fix-engine.impl.socket :as socket]
   [fix-engine.impl.fix-session :as fix-session]))

(defn- dbg [& args]
  (apply println "[connect]" args)
  (flush))

(defmethod p/connect-and-run :fix
  [account log interactor]
  (m/sp
   (try
     (log (-> (select-keys (:tcp (:account/settings account)) [:host :port :ssl])
              (assoc :account/id (:account/id account)
                     :type :tcp/connect)))
     (let [tcp-socket (m/? (socket/connect account))
           _ (log {:type :tcp/connected :account/id (:account/id account)})
           {:keys [run connection-id]} (fix-session/create-fix-session-task account tcp-socket log interactor)]
       (log {:type :fix-session/starting :account/id (:account/id account)})
       (m/? run)
       (log {:type :fix-session/stopped :account/id (:account/id account)})
       :run-finally)
     (catch java.net.UnknownHostException _e
       (log {:type :tcp/connect-ex :account/id (:account/id account) :message "Unknown Host"})
       :host-unknown)
     (catch Exception e
       (dbg "connect-and-run: Exception" (ex-message e))
       (.printStackTrace e)
       (log {:type :fix-session-run-ex :account/id (:account/id account)})
       :connect-ex))))
