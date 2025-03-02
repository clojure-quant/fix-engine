(ns demo.quote-harvest
  (:require 
    [clojure.pprint :refer [print-table]]
    [fix-engine.core :as fix-engine]
    [fix-engine.api.api-core :as fix-api]
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

(def symbols ["EUR/USD" "GBP/USD" "EUR/JPY"
              "USD/JPY" "AUD/USD" "USD/CHF"
              "GBP/JPY" "USD/CAD" "EUR/GBP"
              "EUR/CHF"  "NZD/USD" "USD/NOK" 
              "USD/ZAR" "USD/SEK" "USD/MXN"])

(defn on-quote [msg]
  (println "on-quote: " msg)
  )

(defn start-harvesting [& _]
  (fix-engine/initialize ".data")
  (let [client (fix-api/connect :ctrader-tradeviewmarkets-quote)] 
    (fix-api/on-quote client on-quote)
    
    ;(p/subscribe client {:symbol "1"})
    ;(p/subscribe client {:symbol "2"})
    (println "subscribing to quotes for symbols: " symbols)
    (doall (map #(fix-api/subscribe client {:symbol %}) symbols))
    
    ;(fix-api/snapshot client)    
    (println "will print current quote table every 5 seconds..")
    (set-interval (print-quotes client) 5000)

    @(promise) ;; application run from the command line, no arguments, keep app running.
    )
  )

(comment 
   (start-harvesting {})

 ; 
  )





