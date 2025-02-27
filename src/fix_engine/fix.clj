(ns fix-engine.fix
  (:require
   [missionary.core :as m]
   [fix-engine.client :refer [boot-with-retry connector]])
  (:import missionary.Cancelled))

(def out-mbx (m/mbx))
(def in-mbx (m/mbx))

(defn conn-interactor [write read]
  ; this fn gets called whenever the connection is established.
  (println "conn-interactor ws established")
  (let [msg-in (m/stream
                (m/observe read))
        send-msg (m/sp
                  (loop [v (m/? out-mbx)]
                    (println "interactor send: " v)
                    (m/? (write v))
                    (recur (m/? out-mbx))))
        process-msg (m/reduce
                     (fn [_ msg]
                       (println "interactor rcvd: " msg)
                       (in-mbx msg))
                     nil msg-in)]
    (m/sp
     (try
       (m/? (write {:op :message :val "browser-ws-connected"}))
       (m/? (m/join vector process-msg send-msg))
       (println "wsconninteractor DONE! success!")
       (catch Exception ex
         (println "wsconninteractor crashed: " ex))
       (catch Cancelled _
         (println "wsconninteractor was cancelled.")
             ;(m/? shutdown!)
         true)))))


(defn create-multiplexer []
  (let [req-id (atom 0)
        dispose! ((boot-with-retry conn-interactor connector)
                  #(println "multiplexer finished success:" %)
                  #(println "multiplexer finished error:" %))
        msg-flow (m/stream
                  (m/ap
                   (loop [msg (m/? in-mbx)]
                      ;(println "multiplexer rcvd: " msg)
                     (m/amb msg (recur (m/? in-mbx))))))]
    {:req-id req-id
     :dispose-fn dispose!
     :msg-flow msg-flow}))
