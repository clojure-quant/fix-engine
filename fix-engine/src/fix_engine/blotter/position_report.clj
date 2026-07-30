(ns fix-engine.blotter.position-report)

(defn create-report-consolidator
  [pos-req-id]
  {:pos-req-id (str pos-req-id)
   :responses (atom [])})

(defn add-response
  [{:keys [pos-req-id responses]} response]
  (when (= pos-req-id (some-> (:req-id response) str))
    (let [total (:total response)]
      (cond
        (= 0 total) []
        (and (number? total) (pos? total))
        (let [responses (swap! responses conj response)]
          (when (= total (count responses))
            responses))
        :else
        (throw (ex-info "position report requires a non-negative :total"
                        {:response response}))))))
