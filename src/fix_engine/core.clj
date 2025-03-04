(ns fix-engine.core
  (:require
   [fix-engine.impl.account]
   [fix-engine.impl.quotes :refer [create-quote-interactor only-quotes]]
   [fix-translator.session :refer [create-session]]))

(defn create-fix-engine
  "creates a fix-engine instance"
  [fix-config-file]
  (fix-engine.impl.account/create-fix-engine fix-config-file))

(defn configured-accounts [fix-engine]
  (-> fix-engine :accounts keys))

(defn get-session
  "gets a session for an account
   this is the fix-engine state (use create-fix-engine)"
  [fix-engine account-kw interactor]
  (fix-engine.impl.account/get-session fix-engine account-kw interactor))

(defn get-quote-session [fix-engine  account-kw]
  (let [ {:keys [session out-f]} (get-session fix-engine account-kw create-quote-interactor)
        ;decoder (create-session (:accounts fix-engine) account-kw)
        ]
    ;session
    (only-quotes session out-f)))