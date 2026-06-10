(ns quanta.quote.subscription
  (:require
   [clojure.set :refer [difference]]))


(defn sub-unsub-sets [old new]
  (let [unsub (difference old new)
        sub (difference new old)]
    {:sub sub :unsub unsub}))


(comment 
  (sub-unsub-sets #{1 2 3} #{2 3 4})  
  ;
  )





