(ns fix-engine.quote.account
  (:require
   [quanta.quote.protocol :as p]
   [quanta.quote.interactor :refer [create-quote-interactor]]
   [fix-engine.impl.tcp.boot :refer [boot-with-retry]]
   [fix-engine.quote.messaging] ; side effects
   ))

(defmethod p/create-quote-account :fix-quote
  [{:keys [account/id] :as account-config} subscription-a send-quote log]
  (let [interactor (create-quote-interactor subscription-a send-quote)]
    (boot-with-retry account-config log interactor)))
