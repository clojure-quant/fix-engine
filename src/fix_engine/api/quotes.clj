(ns fix-engine.api.quotes
  (:require 
    [clojure.edn :as edn])
  )

;; symbol mapping 

(def ctrader-symbol-dict
  (-> "dialect/ctrader.edn" slurp edn/read-string))

(defn symbol->ctrader-id [symbol]
  (->> (filter #(= symbol (:symbol %)) ctrader-symbol-dict)
       first
       :id))

(defn ctrader-id->symbol [id]
  (->> (filter #(= id (:id %)) ctrader-symbol-dict)
       first
       :symbol))

(defn ctrader-id-str->symbol [id]
  (ctrader-id->symbol (parse-long id)))

;; todo: this needs to be on a per session basis.

(defonce quote-data-agent (agent {}))

(defn quote-data-full [msg]
 (let [msg (update msg :symbol ctrader-id-str->symbol)
       instrument (:symbol msg)]
   (send quote-data-agent assoc instrument msg)))

(defn quote-sanitize [quote]
  {:symbol (:symbol quote)
   :price (:md-entry-price quote)})

(defn quote-snapshot []
  (let [data @quote-data-agent
        quotes (vals data)]
    (map quote-sanitize quotes)))


(comment
  @quote-data-agent
  (quote-snapshot)
   ;; => ({:symbol "3", :price 158.828}
   ;;     {:symbol "1", :price 1.0794} 
   ;;     {:symbol "2", :price 1.2593})

  ctrader-symbol-dict
  (symbol->ctrader-id "EUR/USD")
  (symbol->ctrader-id "USD/JPY")
  (ctrader-id->symbol 1)
  (ctrader-id->symbol 4)

;   
  )