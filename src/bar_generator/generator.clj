(ns bar-generator.generator
  (:require
   [missionary.core :as m]
   [tick.core :as t]
   [bar-generator.bar :refer [create-bars]]))

#_(defn take-until [pred coll]
  (reduce (fn [acc x]
            (let [acc (conj acc x)]
              (if (pred x)
                (reduced acc)
                acc)))
          '()
          coll))

(defn take-until
  ([pred]
   (fn [rf]
     (let [done? (volatile! false)]
       (fn
         ([] (rf))  ;; init
         ([result] (rf result))  ;; complete
         ([result input]  ;; step function
          (let [result (rf result input)]
            (if (pred input)
              (do (vreset! done? true)
                  (reduced result))
              result)))))))
   ([pred coll]
    (sequence (take-until pred) coll)))

(comment 
  (def data [1 2 3 (t/instant) 4 5 6 (t/instant)])
  (take-while #(not (t/instant? %))  data)
  (take-until t/instant? data)
  
  (transduce (take-until t/instant?) conj data)
 ; 
  )

(defn time-buffered [duration-ms flow]
  (m/ap
   (let [restartable (second (m/?> (m/group-by {} flow)))]
     (m/? (->> (m/ap 
                (m/amb= 
                 (m/? (m/sleep duration-ms (t/instant)))
                 (m/?> restartable)))
               ;(m/eduction (take-while #(not (t/instant? %))))
               (m/eduction (take-until t/instant?))
               (m/reduce conj))))))

(defn trade-time-vec->bar [trade-time-vec]
  (let [trades (butlast trade-time-vec)
        dt  (last trade-time-vec)]
    (create-bars dt trades)))

(defn bar-f [duration-ms trade-flow]
  (let [vec-flow (time-buffered duration-ms trade-flow)]
    (m/eduction (map trade-time-vec->bar) vec-flow)
    ;vec-flow
    ))

