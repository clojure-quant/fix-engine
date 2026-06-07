(ns fix-engine.impl.log-flow
  (:require
   [missionary.core :as m]))

(defn msg-flow
  "Missionary flow backed by a single downstream subscription (see flow-sender)."
  [!-a]
  (m/stream
   (m/observe
    (fn [!]
      (reset! !-a !)
      (fn []
        (reset! !-a nil))))))

(defn flow-sender
  "Returns {:flow f :send s} where (s v) publishes v on f."
  []
  (let [!-a (atom nil)]
    {:flow (msg-flow !-a)
     :send (fn [v]
             (when-let [! @!-a]
               (! v)))}))
