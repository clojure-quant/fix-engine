(ns quanta.quote.protocol)

(defmulti create-quote-account
  "a trade-account must implement this method to create it.
   account-config is a map that must contain 
      :account/id  - unique identifier (long)
      :account/api - the quote api identifier (keyword)
      :account/settings - settings as required by the api
   subscription-a - a set of asset ids that the account is subscribed to
   emit-quote - a function that can send quotes to the account
   log - a function that can log messages
   "
  (fn [account-config subscription-a emit-quote log]
    (:account/api account-config)))