(ns fix-engine.bar-generator
  (:require
   [tick.core :as t]
   [missionary.core :as m])
  (:import [java.io StringWriter]))

(defn log-quote-text [s]
  (spit "quotes.log" s :append true))

(defn log-quotes [quotes]
  (let [sw (StringWriter.)
        _ (doall
           (for [quote quotes]
             (let [quote-str (str "\n" quote)]
               (.write quote-str))))
        sdata (.toString sw)]
    (log-quote-text sdata)))


(defn extended-quote-f [market-kw quote-f]
  (m/eduction
   (map (fn [quote] (assoc quote
                           :market market-kw
                           :timestamp (t/instant))))
   quote-f))


(defn create-bargenerator []
  {:markets (atom {})})

(defn start-processing-feed [this market-kw quote-f]
  (let [equote-f (extended-quote-f market-kw quote-f)
        quote-block-f equote-f
        ;quote-block-f (m/eduction (partition-all 10) equote-f)
        quote-writer-t (m/reduce (fn [_ quotes] 
                                   (log-quotes quotes) nil) 
                                 nil quote-block-f)
        quote-writer-dispose (quote-writer-t
                              (fn [_] (log-quote-text "\nquote-writer-task completed\n"))
                              (fn [_] (log-quote-text "\nquote-writer-task crash\n")))]
    (swap! (:markets this) assoc market-kw quote-writer-dispose)
    (log-quote-text "\nquote-writer-started\n")
    ))

(defn stop-processing-feed [this market-kw]
  (when-let [dispose! (market-kw @(:markets this))]
    (swap! (:markets this) dissoc market-kw)
    (dispose!)))
    
    
