(ns quanta.blotter.protocol2)

(defprotocol trade-messaging
  (api-order [this normalized-order-msg-in])
  (blotter-order-update [this broker-orderupdate-msg-in]))

(defmulti create-trade-messaging
  "a tradeaccount must implement this method to create it.
   each quotefeed implementation must have a unique :type.
     A quotefeed must implement subscription-topic protocol."
  (fn [account-config asset-converter log]
    (:account/api account-config)))
