(ns fix-engine.quote.account
  (:require
   [quanta.util.boot :refer [boot-with-retry]]
   [quanta.quote.protocol :as p]
   [quanta.quote.interactor :refer [create-quote-interactor]]
   [fix-engine.quote.messaging] ; side effects
   [fix-engine.impl.connect])) ; side effects

(defmethod p/create-quote-account :fix-quote
  [account subscription-a send-quote log]
  (let [account (assoc account :account/session :fix)
        interactor (create-quote-interactor subscription-a send-quote)]
    (boot-with-retry account log interactor)))
