(ns fix-engine.account-test
  (:require [clojure.test :refer [deftest is]]
            [fix-engine.account :as account]))

(deftest as-account-name-test
  (is (= "fxpro-ctrader-quote" (account/as-account-name :fxpro-ctrader-quote)))
  (is (= "fxpro-ctrader-quote" (account/as-account-name "fxpro-ctrader-quote")))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"account must be a keyword or string"
                        (account/as-account-name 123))))

(deftest load-and-find-account-test
  (let [f (java.io.File/createTempFile "fix-accounts" ".edn")
        path (.getAbsolutePath f)]
    (try
      (spit f '[{:account/name "demo-account"
                 :account/api :fix
                 :account/settings {:tcp {:host "localhost" :port 1 :ssl false}}}])
      (let [idx (account/load-accounts-file path)
            account (account/find-account idx "demo-account")]
        (is (= :fix (:account/api account)))
        (is (= {:host "localhost" :port 1 :ssl false}
               (:tcp (account/settings account))))
        (is (= #{"demo-account"} (set (account/configured-names idx)))))
      (finally (.delete f)))))

(deftest load-accounts-file-rejects-non-vector-test
  (let [f (java.io.File/createTempFile "fix-accounts" ".edn")
        path (.getAbsolutePath f)]
    (try
      (spit f "{:account/name \"not-a-vector\"}")
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"accounts file must be a vector of accounts"
                            (account/load-accounts-file path)))
      (finally (.delete f)))))
