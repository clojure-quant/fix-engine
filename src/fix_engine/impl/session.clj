(ns fix-engine.impl.session
  (:require
   [clojure.edn :as edn]
   [tick.core :as t]
   [fix-translator.schema :refer [create-decoder]]
   [fix-translator.message :refer [fix->payload
                                   encode-fix-msg]]))

#_(defn load-accounts [accounts-edn-file]
  (-> accounts-edn-file slurp edn/read-string))

(defn create-session  [account-config]
  {:config account-config
   :decoder (create-decoder (:spec account-config))
   :inbound-seq-num (atom 0)
   :outbound-seq-num (atom 0)
   :converter (atom {})})

(defn encode-msg [{:keys [config outbound-seq-num decoder] :as session}
                  [msg-type payload]]
  (let [seq-num (swap! outbound-seq-num inc)
        header (assoc (:header config)
                      :msg-type msg-type
                      :msg-seq-num seq-num
                      :sending-time (t/instant))
        fix-message {:header header
                     :payload payload}]
    (encode-fix-msg decoder fix-message)))

(defn decode-msg [{:keys [decoder] :as session} fix-msg-vec]
  (fix->payload decoder fix-msg-vec))