(ns fix-engine.quotes
  (:require
   [fix-translator.core :refer [decode-msg]]))



; 262	MDReqID	Yes	Any valid value	String	A unique quote request ID. A new ID for a new subscription, the same ID as used before for a subscription removal.
; 263	SubscriptionRequestType	Yes	1 or 2	Char	1 = Snapshot plus updates (subscribe);
;  2 = Disable previous snapshot plus update request (unsubscribe) .
;  264	MarketDepth	Yes	0 or 1	Integer	A full book will be provided.
;  0 = Depth subscription;
;  1 = Spot subscription.
;  265	MDUpdateType	Yes	Any valid value	Integer	Only the Incremental Refresh is supported.
;  267	NoMDEntryTypes	Yes	2	Integer	Always set to 2 (both bid and ask will be sent) .
;  269	MDEntryType	Yes	0 or 1	Char	This repeating group contains a list of all types of the Market Data Entries the requester wants to receive.
;  0 = Bid;
;  1 = Offer.
;  	NoRelatedSym	Yes	Any valid value	Integer	The number of symbols requested.
;  55	Symbol	Yes	Any valid value	Long	Instrument identificators are provided by Spotware.


; <message name="MarketDataIncrementalRefresh" msgtype="X" msgcat="app">
; <field name="MDReqID" required="N"/>
; <group name="NoMDEntries" required="Y">
;    <field name="MDUpdateAction" required="Y"/>
;    <field name="MDEntryType" required="N"/>
;    <field name="MDEntryID" required="Y"/>
;    <field name="Symbol" required="Y"/>
;    <field name="MDEntryPx" required="N"/>
;    <field name="MDEntrySize" required="N"/>
; </group>
; </message>


(defonce subscription-id (atom 1))

(defn get-subscription-id []
  (swap! subscription-id inc))


(defn subscription [{:keys [symbol]}]
  (let [sub-id (get-subscription-id)]
    [:md-req-id (str sub-id)
     :md-sub-type "1" ; 2=unsubscibe
     :md-update-type "1" ; ctrader only supports 1 type.
     :md-sub-depth "1" ; 0=orderbook 1=best-bid-ask
     :md-sub-number-entries "2" ; send bid and ask together
     :md-sub-entry-type "0" ; 0 = bid
     :md-sub-entry-type "1" ; 1 = ask
     :md-sub-number-instruments "1"
     :symbol symbol]))


(comment

  (get-subscription-id)

  (subscription {:symbol "EURUSD"})


 ;  
  )

;<message name="MarketDataSnapshotFullRefresh" msgtype="W" msgcat="app">
;<field name="MDReqID" required="N"/>
;<field name="Symbol" required="Y"/>
;<group name="NoMDEntries" required="Y">
;  <field name="MDEntryType" required="Y"/>
;  <field name="QuoteEntryID" required="N"/>
;  <field name="MDEntryPx" required="Y"/>
;  <field name="MDEntrySize" required="N"/>
;  <field name="MDEntryID" required="N"/>
;</group>
;</message>


(defonce quote-data-agent (agent {}))


(defn quote-data-full [msg-type msg session]
  (let [venue (:venue session)]
    (when @(:translate? session)
      (let [msg (decode-msg venue msg-type msg)
            instrument (:symbol msg)]
        (send quote-data-agent assoc instrument msg)))))

(defn quote-sanitize [quote]
  {:symbol (:symbol quote)
   :price (:md-entry-price quote)})


(defn snapshot []
  (let [data @quote-data-agent
        quotes (vals data)]
    (map quote-sanitize quotes)))

;; security list

(defn security-list-request []
  (let [sub-id (get-subscription-id)]
    [:security-list-request-id (str sub-id)
     :security-list-type "0"]))

(comment
  @quote-data-agent
  (snapshot)
   ;; => ({:symbol "3", :price 158.828}
   ;;     {:symbol "1", :price 1.0794} 
   ;;     {:symbol "2", :price 1.2593})

;   
  )
 