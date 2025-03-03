(ns bar-generator.bar)

(defn add-trade [{:keys [_open high low volume ticks] :as bar} {:keys [price] :as trade}]
  (assoc bar 
         :high (max high price)
         :low (min low price)
         :close price
         :volume (+ volume (:volume trade))
         :ticks (inc ticks)))

(defn create-bar [trades]
  (let [[first-trade & trades] trades
        {:keys [price volume asset]} first-trade
        initial-bar {:asset asset
                     :open price
                     :high  price
                     :low  price
                     :close  price
                     :volume  volume
                     :ticks 1}]
    (reduce add-trade initial-bar trades)))

(comment 
(create-bar
 [{:volume 3, :asset "B", :price 9.999495246345742}
  {:volume 34, :asset "B", :price 9.982399541778701}
  {:volume 44, :asset "B", :price 9.947307942516321}
  {:volume 21, :asset "B", :price 9.912512426148753}])  
 ; 
  )

 
(defn create-bars [trades]
  (->> trades 
       (group-by :asset)       
       vals
       (map create-bar)
       ))
  
(comment 
  

  (def trades [{:volume 3, :asset "B", :price 9.999495246345742}
               {:volume 34, :asset "B", :price 9.982399541778701}
               {:volume 64, :asset "A", :price 0.9951227736065371}
               {:volume 91, :asset "C", :price 99.70046861583863}
               {:volume 44, :asset "B", :price 9.947307942516321}
               {:volume 0, :asset "C", :price 100.06071524588565}
               {:volume 21, :asset "B", :price 9.912512426148753}])

  (create-bars trades)
 ;  
  )

