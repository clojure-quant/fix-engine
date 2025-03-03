(ns demo.bargenerator.demo
  (:require 
   [missionary.core :as m]
   [tick.core :as t]
   [bar-generator.bar :refer [create-bars]]
   [demo.bargenerator.scheduler :refer [scheduler] ]
   [demo.bargenerator.randomfeed :refer [quote-producer]]))

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

(def scheduler4 
  (m/eduction (interpose nil) scheduler))


(comment

  (let [sched3 (m/eduction (take 4) scheduler4)
        t (m/reduce conj [] sched3)]
    (m/? t))

 ;[#time/instant "2025-03-02T23:48:16.402841162Z" 
 ; nil 
 ; #time/instant "2025-03-02T23:48:18.402841162Z" 
 ; nil]

 ; 
  )


(defn time-buffered [duration-ms flow]
  (m/ap
   (let [restartable (second (m/?> (m/group-by {} flow)))]
     (m/? (->> (m/ap (m/amb= (m/?> restartable)
                             (m/? (m/sleep duration-ms ::end))))
               (m/eduction (take-while #(not= % ::end)))
               (m/reduce conj))))))

; 
((->> ;bar-processor
      (time-buffered 400 quote-feed)
      (m/eduction (take 2))
      ;(m/eduction (map count))
      (m/eduction (map create-bars))
      (m/reduce conj)
       
  )
 prn prn)

[[{:volume 3, :asset "B", :price 9.999495246345742} 
  {:volume 34, :asset "B", :price 9.982399541778701} 
  {:volume 64, :asset "A", :price 0.9951227736065371} 
  {:volume 91, :asset "C", :price 99.70046861583863} 
  {:volume 44, :asset "B", :price 9.947307942516321} 
  {:volume 0, :asset "C", :price 100.06071524588565}
  {:volume 21, :asset "B", :price 9.912512426148753}] 
 [{:volume 36, :asset "A", :price 0.9961795875082428} 
  {:volume 33, :asset "C", :price 99.90639922639659} 
  {:volume 45, :asset "C", :price 99.68685791006868}
  {:volume 67, :asset "B", :price 9.872036274042992} 
  {:volume 57, :asset "A", :price 0.9982666188027334} 
  {:volume 61, :asset "A", :price 0.999783287553457} 
  {:volume 81, :asset "C", :price 99.49812161873967} 
  {:volume 98, :asset "B", :price 9.876156478646958} 
  {:volume 92, :asset "A", :price 1.000682950534221} 
  {:volume 34, :asset "C", :price 99.37090435963661}]]

[({:asset "B", :open 10.03844864582148, :high 10.052549551239535, :low 10.036589395554982, :close 10.036589395554982, :volume 92, :ticks 3} 
  {:asset "C", :open 100.0674911400899, :high 100.24205723644918, :low 100.0674911400899, :close 100.24205723644918, :volume 114, :ticks 2}
  {:asset "A", :open 1.002992151793049, :high 1.002992151793049, :low 1.002992151793049, :close 1.002992151793049, :volume 40, :ticks 1}) 
 ({:asset "A", :open 1.0071109932645552, :high 1.017316826958779, :low 1.0071109932645552, :close 1.0148233341716053, :volume 253, :ticks 5} 
  {:asset "B", :open 10.080644489757809, :high 10.1251991655507, :low 10.080234298278404, :close 10.08025601469736, :volume 226, :ticks 4} 
  {:asset "C", :open 100.3985649308696, :high 100.99752002609245, :low 100.3985649308696, :close 100.99752002609245, :volume 179, :ticks 4})]



(def x 
 [[#time/instant "2025-03-02T23:10:30.312480888Z"
   {:volume 11, :asset "B", :price 10.02438833116545} 
   {:volume 95, :asset "B", :price 10.049185376002411}
   {:volume 68, :asset "C", :price 100.04180137264183}
   {:volume 9, :asset "A", :price 1.0034300260310423} 
   {:volume 23, :asset "C", :price 99.63345730343312}
   {:volume 5, :asset "B", :price 10.086177770163706} 
   {:volume 34, :asset "C", :price 100.05014148845542}
   {:volume 36, :asset "A", :price 1.0018382174304852}
   {:volume 36, :asset "B", :price 10.12611795975566}
   {:volume 69, :asset "C", :price 100.5298632451661} 
   {:volume 27, :asset "C", :price 100.38007057174158} 
   {:volume 44, :asset "A", :price 0.9978915698974109}
   {:volume 16, :asset "B", :price 10.09123602394572} 
   {:volume 49, :asset "C", :price 100.10257069270516}
   {:volume 18, :asset "B", :price 10.085821135879875}
   {:volume 6, :asset "C", :price 99.66367123258732}
   {:volume 23, :asset "C", :price 100.0244676415738} 
   {:volume 9, :asset "A", :price 0.9949475249184018}
   {:volume 15, :asset "C", :price 100.42931612238023} 
   {:volume 11, :asset "A", :price 0.9974501296857669} 
   {:volume 20, :asset "B", :price 10.083674513705716}
   {:volume 92, :asset "A", :price 0.9949592236286967}
   {:volume 57, :asset "C", :price 100.4618583656858} 
   {:volume 54, :asset "C", :price 100.56126032453258}
   {:volume 78, :asset "B", :price 10.12076968606006} 
   {:volume 94, :asset "B", :price 10.152155530128034}
   {:volume 53, :asset "C", :price 100.35678991870884}
   {:volume 50, :asset "B", :price 10.156482290416804}
   {:volume 71, :asset "B", :price 10.185203156849452}
   {:volume 22, :asset "A", :price 0.9949585618276031}
   {:volume 86, :asset "C", :price 100.62826381927363} {:volume 41, :asset "C", :price 101.08082376422794} {:volume 81, :asset "A", :price 0.994388969102429} {:volume 48, :asset "B", :price 10.181613927953457}] 
   []])







