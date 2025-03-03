(ns fix-engine.logger
  (:require 
   [tick.core :as t]))


(defn log [t data]
  (println t ": " data)
  (spit "msg.log" (str "\n" t  ": " data) :append true))


(defn log-time [t data]
  (println t ": " data)
  (spit "msg.log" (str "\n" (t/instant) " " t  ": " data) :append true))