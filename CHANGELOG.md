



2023-12-05 awb99
- deps.edn fix-translator via git-dependency, tick instead of clj-time
- added FIX4.4 (ctrader needs it) + sender-sub-id target-sub-id
- added TargetSubID + SenderSubID
  57	TargetSubID	Yes	QUOTE or TRADE	String	An additional session qualifier. Possible values are QUOTE and TRADE.
  50	SenderSubID	No	Any valid value	String	The assigned value used to identify a specific message originator. Must be set to QUOTE if TargetSubID=QUOTE.
- added Username/Password

