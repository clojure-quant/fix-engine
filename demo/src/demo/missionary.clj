(ns demo.missionary
  (:require
   [missionary.core :as m])
  (:import missionary.Cancelled))


(def a-f (m/seed (range 10)))

(defn start-flow-printer [f]
  (let [t (m/reduce (fn [_ v]
                      (println "data: " v)) nil f)]
    (t #(println "printer completed" %)
       #(println "printer crash " %))))


(start-flow-printer a-f)

(def b-f
  (m/ap
   (let [data (m/?> a-f)]
     (+ 1000 data))))

(start-flow-printer b-f)


(def c-f
  (m/ap
   (let [data (m/?> a-f)]
     (if (> data 4)
        ;(throw (ex-info "c stopped" {:a data}))
       (throw (missionary.Cancelled. "c stopped"))
              ;(reduced data)
       data))))


(start-flow-printer c-f)

(def d-f
  (m/ap
   (loop [data (m/?< a-f)]
     (let [done? (> data 4)]
       (m/amb
         data
        (when-not done?
          (recur (m/?< a-f))))))))
   

(start-flow-printer d-f)

