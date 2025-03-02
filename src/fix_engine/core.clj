(ns fix-engine.core
  (:require
   [fix-engine.impl.account]
   [fix-engine.impl.quotes :refer [create-quote-interactor only-quotes] ]
   [fix-translator.session :refer [create-session]]
   ))

(defn create-fix-engine
  "creates a fix-engine instance"
  [fix-config-file]
  (fix-engine.impl.account/create-fix-engine fix-config-file))

(defn configured-accounts [this]
  (-> this :accounts keys))

(defn get-session
  "gets a session for an account
   this is the fix-engine state (use create-fix-engine)"
  [this account-kw interactor]
  (fix-engine.impl.account/get-session this account-kw interactor))


(defn get-quote-session [this  account-kw]
  (let [session (get-session this account-kw create-quote-interactor)
        decoder (create-session (:accounts this) account-kw)]
    ;session
    (only-quotes decoder session)
    )
  )