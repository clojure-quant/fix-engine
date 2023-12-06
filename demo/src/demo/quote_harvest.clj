(ns demo.quote-harvest
  (:require 
    [clojure.pprint :refer [print-table]]
    [fix-engine.api.core :as fix-api]
    [fix-engine.connection.protocol :as p]
   ))

(defn set-interval [callback ms]
  (future (while true (do (Thread/sleep ms) (callback)))))

;(def job (set-interval #(println "hello") 1000))

; (future-cancel job)

(defn print-quotes [client] 
  (fn []
    (let [t (fix-api/snapshot client)]
      (println "quote table:")
      (print-table t))))


(defn start-harvesting [& _]
  (let [client (fix-api/connect :ctrader-tradeviewmarkets-quote)] 
     (p/subscribe client {:symbol "1"})
     (p/subscribe client {:symbol "2"})
     (p/subscribe client {:symbol "3"})
     (p/subscribe client {:symbol "4"})
     (p/subscribe client {:symbol "5"})
     (p/subscribe client {:symbol "6"})
     ;(subscribe client {:symbol "EURUSD"})

    ;(fix-api/snapshot client)    
    (println "will print current quote table every 5 seconds..")
    (set-interval (print-quotes client) 5000)

   )  
  
  )





