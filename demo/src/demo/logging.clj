(ns demo.logging
  (:require
   [missionary.core :as m]
   [quanta.util.logger :refer [create-logger start-logging-flow]]))

(defn data-producer [max-delay-ms]
  (m/ap
   (loop [i 0]
     (m/amb
      (m/? (m/sleep (rand-int max-delay-ms) i))
      (recur (inc i))))))


(def log (create-logger "test3.log"))

(log "hi")
(log "ho")


(def dispose!
  (start-logging-flow "test4.log" (data-producer 200)))

(dispose!)


