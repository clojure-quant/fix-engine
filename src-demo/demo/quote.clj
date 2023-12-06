(ns demo.quote
  (:require
   [fix-engine.core :as fix]
   [fix-engine.quotes :refer [snapshot]]
   [fix-engine.connection.protocol :as p]
   ))

(defn my-handler [key-id reference last-msg new-msg]
  ; key-id:  :user-callback 
  ; reference:  #object[clojure.lang.Agent
  (println "my-handler: msg: " new-msg)
  (case (:msg-type new-msg)
    :logon (println "Logon accepted by" (:sender-comp-id new-msg) "full: " new-msg)
    :execution-report (println "Execution Report: " new-msg)
    :logout (println "Logged out from" (:sender-comp-id new-msg))
    (println "fix message received: " new-msg)))

(def client (fix/load-client :ctrader-tradeviewmarkets-quote))


; reset seq num can be :yes or :no
(p/logon client my-handler 60 :yes true)

(p/subscribe client {:symbol "1"})
(p/subscribe client {:symbol "2"})
(p/subscribe client {:symbol "3"})
(p/subscribe client {:symbol "4"})
(p/subscribe client {:symbol "5"})
(p/subscribe client {:symbol "6"})
 ;(subscribe client {:symbol "EURUSD"})

(snapshot)
;; => ({:symbol "1", :price 1.07937}
;;     {:symbol "2", :price 1.25951}
;;     {:symbol "3", :price 158.851}
;;     {:symbol "4", :price 147.175}
;;     {:symbol "5", :price 0.65557}
;;     {:symbol "6", :price 0.8751})

(p/securitylist client)

;; on receiving response, it will print out:
;; security list:  {:symbol 107, :symbol-name XBR/USD}

(comment
   ;; needed to restart a session:
  (fix/end-session client)

   ;
  )

