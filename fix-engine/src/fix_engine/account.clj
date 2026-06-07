(ns fix-engine.account
  (:require [clojure.edn :as edn]))

(defn as-account-name
  "`:fxpro-ctrader-quote` -> `\"fxpro-ctrader-quote\"`. Strings pass through."
  [account-ref]
  (cond
    (keyword? account-ref) (name account-ref)
    (string? account-ref) account-ref
    :else (throw (ex-info "account must be a keyword or string"
                          {:account account-ref}))))

(defn settings
  "FIX connection settings for an account with `:account/api` `:fix`."
  [account]
  (:account/settings account))

(defn load-accounts-file
  "Reads a vector of account maps from EDN and indexes them by `:account/name`."
  [accounts-edn-file]
  (let [accounts (-> accounts-edn-file slurp edn/read-string)]
    (when-not (vector? accounts)
      (throw (ex-info "accounts file must be a vector of accounts"
                      {:file accounts-edn-file
                       :got (type accounts)})))
    (into {} (map (juxt :account/name identity) accounts))))

(defn find-account
  [accounts-index account-name]
  (get accounts-index account-name))

(defn configured-names
  [accounts-index]
  (keys accounts-index))
