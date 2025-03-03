(ns demo.bargenerator.randomfeed
  (:require [missionary.core :as m]))

(defn new-price [price]
  (let [chg (- (rand 1) 0.5)
        chg-prct (/ chg 100.0)
        ]
    (* price (- 1.0 chg-prct))
    ))

(defn quote-producer [asset start-price max-delay-ms]
  (m/ap
   (loop [price start-price]
     (m/? (m/sleep (rand-int max-delay-ms)))
     (let [price (new-price price)]
     (m/amb
      {:asset asset 
       :price price 
       :volume (rand-int 100)}
      (recur price))))))

(comment 
  (def a (quote-producer "A" 100.0 10)) 
  
  (m/? 
   (m/reduce conj [] (m/eduction (take 1000) a)))
   
;  
  )




