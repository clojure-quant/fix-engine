(ns fix-engine.quote.fix-quote
  (:require
   [quanta.quote.protocol :as p]
   [fix-engine.impl.tcp.boot :refer [boot-with-retry]]
   [fix-engine.impl.interactor.quote :refer [create-quote-interactor]]))


(defmethod p/create-quote-account :fix-quote
  [{:keys [account/id] :as account-config} subscription-a send-quote log]
  (let [interactor (create-quote-interactor subscription-a send-quote)]
    (boot-with-retry account-config log interactor)))