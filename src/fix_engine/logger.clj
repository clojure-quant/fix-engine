(ns fix-engine.logger)


(defn log [t data]
  (println t ": " data)
  (spit "msg.log" (str "\n" t  ": " data) :append true))