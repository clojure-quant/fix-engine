(ns demo.missionary2
  (:require
   [missionary.core :as m])
  (:import missionary.Cancelled))

(defn data-generator [c]
  (m/ap
   (loop [[x & r] (range c)]
     (m/amb
      (m/? (m/sleep x x))
      (if (seq r)
        (recur r)
        (m/amb))))))

(defn start-flow-printer [f]
  (let [t (m/reduce (fn [_ v]
                      (println "data: " v)) nil f)]
    (t #(println "printer completed" %)
       #(println "printer crash " %))))

(start-flow-printer (data-generator 20))

(def conn-f
  (m/ap
   (loop [[flow-id & r] [:a :b :c]]
     (m/? (m/sleep 500))
     (m/amb
      [flow-id (data-generator 20)]
      (if (seq r)
        (recur r)
        (m/amb))))))

(def curr-conn-f
  (m/ap
   (let [[conn-id data-f] (m/?> conn-f)
         data (m/?> data-f)]
     [conn-id data])))

(start-flow-printer conn-f)

(start-flow-printer curr-conn-f)

