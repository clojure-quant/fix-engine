(ns demo.bargenerator.demo
  (:require
   [missionary.core :as m]
   [tick.core :as t]
   [bar-generator.bar :refer [create-bars]]
   [demo.bargenerator.scheduler :refer [scheduler]]
   [demo.bargenerator.randomfeed :refer [quote-producer]]
   [bar-generator.generator :refer [time-buffered bar-f]]
   ))

(defn mix
  "Return a flow which is mixed by flows"
  ; will generate (count flows) processes, 
  ; so each mixed flow has its own process
  [& flows]
  (m/ap (m/?> (m/?> (count flows) (m/seed flows)))))

(def quote-feed
  (mix (quote-producer "A" 1.0 300)
       (quote-producer "B" 10.0 300)
       (quote-producer "C" 100.0 300)))

(comment
  (m/?
   (m/reduce conj []
             (m/eduction (take 5) quote-feed)))

  ;[{:volume 34, :asset "B", :price 9.98609337717243}
  ; {:volume 4, :asset "B", :price 9.943237802872938}
  ; {:volume 97, :asset "A", :price 1.0038424574580802}
  ; {:volume 31, :asset "C", :price 100.41281594563398}
  ; {:volume 45, :asset "B", :price 9.928846880638302}]
  ;
  )

; 
((->> 
  (time-buffered 400 quote-feed)
  (m/eduction (take 2))
  ;(m/eduction (map count))
  ;(m/eduction (map create-bars))
  (m/reduce conj))
 prn prn)

[[{:price 0.997403395039221, :asset "A", :volume 51}
  {:price 10.006800707984176, :asset "B", :volume 35}
  {:price 9.986962420092151, :asset "B", :volume 47}
  #time/instant "2025-03-03T14:50:29.087497350Z"]
 [{:price 10.096071970960141, :asset "B", :volume 91}
  {:price 0.995669860312596, :asset "A", :volume 87}
  #time/instant "2025-03-03T14:50:29.494585260Z"]]

((->>
  (bar-f 400 quote-feed)
  (m/eduction (take 2))
  (m/reduce conj))
 prn prn)

[({:date #time/instant "2025-03-03T15:04:43.244003164Z", :asset "A", :open 1.0016767928925834, :high 1.0026038401386101,
   :low 1.0002319391938983, :close 1.0023027142089445, :volume 169, :ticks 4} 
  {:date #time/instant "2025-03-03T15:04:43.244003164Z", :asset "B", :open 9.961726478467186, :high 9.985880771652763, 
   :low 9.92700407026854, :close 9.964568210448853, :volume 268, :ticks 5} 
  {:date #time/instant "2025-03-03T15:04:43.244003164Z", :asset "C", :open 99.91283496763965, :high 99.91283496763965,
   :low 99.71151524614571, :close 99.71151524614571, :volume 97, :ticks 2}) 
 ({:date #time/instant "2025-03-03T15:04:43.686749403Z", :asset "A", :open 1.0054214378378081, :high 1.0094429375741656, 
   :low 1.0054214378378081, :close 1.0094429375741656, :volume 132, :ticks 2} 
  {:date #time/instant "2025-03-03T15:04:43.686749403Z", :asset "B", :open 9.923115062510547, :high 9.923115062510547, 
   :low 9.880311882532379, :close 9.880311882532379, :volume 151, :ticks 2}
  {:date #time/instant "2025-03-03T15:04:43.686749403Z", :asset "C", :open 100.14107405468624, :high 100.14107405468624,
   :low 100.14107405468624, :close 100.14107405468624, :volume 76, :ticks 1})]
                                                                                                                                                                                                                                                                                       {:asset "B", :open 9.97283954627333, :high 10.012884979302775, :low 9.887987949353631, :close 10.007831185340638, :volume 608, :ticks 12})]







