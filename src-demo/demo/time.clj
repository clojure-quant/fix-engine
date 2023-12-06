(ns demo.time
  (:require
   [tick.core :as t]))

(format "%05d" 3)

(format "%04d" 234)

(t/format :iso-zoned-date-time (t/zoned-date-time))

;(require '[tick.locale-en-us]) ; only need this require for custom format patterns
 ; and it's only needed for cljs, although the ns is cljc
(t/format (t/formatter "yyyyMMdd-HH:mm:ss") (t/date-time))

(def formatter (t/formatter "yyyyMMdd-HH:mm:ss"))

(->> t/now
     (t/in "UTC")
    ;(t/format formatter)
    )

(defn now-utc []
  (-> (t/now)
      (t/in "UTC")))

(now-utc)

(->> (now-utc)
    (t/format formatter)
    )
  

  (->> (t/now)
       (t/in "UTC" )
      
      ;(tick/date) 
     ;(t/format formatter)  
    )



(defn timestamp
  "Returns a UTC timestamp in a specified format."
  ([]
   (timestamp "yyyyMMdd-HH:mm:ss"))
  ([format]
   (t/format (t/formatter format) (t/date-time))))

(timestamp)
