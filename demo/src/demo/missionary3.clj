(ns demo.missionary3
  (:require
   [missionary.core :as m])
  (:import missionary.Cancelled))



(m/? (->> (m/seed (range 5))
        (m/eduction (filter odd?) 
                    ;(map inc)
                    ;(mapcat range) 
                    ;(partition-all 4)
                    )
        (m/reduce conj)))