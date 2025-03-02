(ns fix-engine.connection.protocol)

(defprotocol Connection
  ; session
  (logon [id msg-handler heartbeat-interval reset-seq-num-flag
          translate-returning-msgs])
  (logout [id reason])
  ; execution
  (new-order [id side size instrument-symbol price]
             [id side size instrument-symbol price additional-params])
  (cancel [id order])
  (cancel-replace [id order]
                  [id order additional-params])
  (order-status [id order])
  ; quotes
  (subscribe [id subscription])
  (securitylist [id])
  
  )
