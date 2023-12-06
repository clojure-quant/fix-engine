# todo

nice fix message specification for ctrader:
https://help.ctrader.com/fix/specification/#background-of-fix-api-in-fx-trading

## dependency hell
- aleph tcp socket I guess is ok to sue.
- some deps are depreciated (related to threading)


## session id not only sendercompid.
- session state refers by sendercompid.
  this does not allow to connect quote and trade session.


## logging
  - one logfile per session. in+out+connection status
  - enable/disable
  - for order-routing logfiles should always be there (proof what gets done)
  - for uotes logfiles only are needed for debugging.

## FIX Dictionaries 
   - as edn (not json) - why? 1. more compact, 2. can add comments. 3. can use keywords
   - perhaps utilize this format instead of the json that we use at the moment: https://help.ctrader.com/fix/FIX44-CSERVER.xml
   - message decoding: support vectors (applies to marketdata-snapshot + security-list-response)

## support ssh tunneling
   - in production most fix sessions run via a tunnel.
   - question is if it is easiest to run stunnel, but generate a config file automatically.


## implement message type
  Reject (type "j") - happens when one request is not valid.
  https://help.ctrader.com/fix/specification/#introduction


