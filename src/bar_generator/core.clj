(ns bar-generator.core
  (:require
   [tick.core :as t]
   [missionary.core :as m]
   [bar-generator.generator :refer [bar-f]]
   )
  (:import [java.io StringWriter]))

(defn log-text [filename s]
  (spit filename s :append true))


(defn log-data [filename quotes]
  (let [sw (StringWriter.)
        _ (.write sw "\n\n\n")
        _ (doall
           (for [quote quotes]
             (let [quote-str (str "\n" quote)]
               (.write sw quote-str))))
        sdata (.toString sw)]
    (log-text filename sdata)))

(defn log-quotes [quotes]
  (log-data "quotes.log" quotes))

(defn log-quote-text [s]
  (log-text "quotes.log" s))

(defn log-bars-text [quotes]
  (log-text "bars.log" quotes))

(defn log-bars [quotes]
  (log-data "bars.log" quotes))

(comment 
  (log-quotes [1 2 3])
;  
  )

(defn extended-quote-f [market-kw quote-f]
  (m/eduction
   (map (fn [quote] (assoc quote
                           :market market-kw
                           :timestamp (t/instant))))
   quote-f))


(defn create-bargenerator []
  {:markets (atom {})})

(defn start-processing-feed [this market-kw quote-f interval-ms]
  (let [equote-f (extended-quote-f market-kw quote-f) 
        quote-block-f (m/eduction (partition-all 100) equote-f)
        quote-writer-t (m/reduce (fn [_ quotes] 
                                   (log-quotes quotes)
                                   nil) 
                                 nil quote-block-f)
        generated-bar-f (bar-f interval-ms quote-f)
        bar-writer-t (m/reduce (fn [_ bars]
                                   (log-bars bars)
                                   nil)
                                 nil generated-bar-f)
        quote-writer-dispose (quote-writer-t
                              (fn [_] (log-quote-text "\nquote-writer-task completed\n"))
                              (fn [_] (log-quote-text "\nquote-writer-task crash\n")))
        bar-writer-dispose (bar-writer-t
                              (fn [_] (log-bars-text "\nbar-writer-task completed\n"))
                              (fn [_] (log-bars-text "\nbar-writer-task crash\n")))
        ]
    (swap! (:markets this) assoc market-kw 
           {:bar-writer bar-writer-dispose
            :quote-writer quote-writer-dispose})
    (log-quote-text "\nquote-writer-started\n")
    (log-bars-text "\nbar-writer-started\n")
    ))

(defn stop-processing-feed [this market-kw]
  (when-let [{:keys [bar-writer quote-writer]} (market-kw @(:markets this))]
    (swap! (:markets this) dissoc market-kw)
    (bar-writer)
    (quote-writer)))
    
    
