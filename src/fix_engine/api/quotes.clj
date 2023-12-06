(ns fix-engine.api.quotes)


;; todo: this needs to be on a per session basis.

(defonce quote-data-agent (agent {}))

(defn quote-data-full [msg]
 (let [instrument (:symbol msg)]
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

;   
  )