(ns fix-engine.blotter.position-report-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [fix-engine.blotter.position-report :as report]))

(deftest consolidate-position-reports-test
  (let [consolidator (report/create-report-consolidator "req-1")
        first-response {:req-id "req-1" :total 2 :position-id "position-1"}
        second-response {:req-id "req-1" :total 2 :position-id "position-2"}]
    (is (nil? (report/add-response consolidator first-response)))
    (is (= [first-response second-response]
           (report/add-response consolidator second-response)))))

(deftest ignores-response-for-another-request-test
  (let [consolidator (report/create-report-consolidator "req-1")]
    (is (nil? (report/add-response consolidator
                                   {:req-id "req-2" :total 1})))
    (is (empty? @(:responses consolidator)))))

(deftest zero-position-report-test
  (let [consolidator (report/create-report-consolidator "req-1")]
    (is (= [] (report/add-response consolidator
                                   {:req-id "req-1" :total 0})))
    (is (empty? @(:responses consolidator)))))

(deftest invalid-total-test
  (testing "a missing total cannot complete a report"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"non-negative :total"
         (report/add-response
          (report/create-report-consolidator "req-1")
          {:req-id "req-1"})))))
