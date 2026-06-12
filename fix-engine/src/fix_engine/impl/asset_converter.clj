(ns fix-engine.impl.asset-converter
  (:require
   [clojure.set :refer [rename-keys]]
   [quanta.asset.mapper :as p]))

(defn seclist->assets [[_ {:keys [security-request-result no-related-sym] :as sec-list-response}]]
  (when
   (= security-request-result :valid-request)
    (map (fn [asset]
           (rename-keys asset {:symbol-name :asset
                               :symbol-digits :digits
                               :symbol :ctrader})) no-related-sym)))

(defprotocol fix-asset-mapper-protocol
  (set-asset-list [this assets]))

(defrecord fix-asset-mapper [dict-a account log]
  fix-asset-mapper-protocol
  (set-asset-list [_ assets]
    (reset! dict-a  {:dict-by-id (->> assets
                                      (map (juxt :ctrader :asset))
                                      (into {}))
                     :dict-by-name (->> assets
                                        (map (juxt :asset :ctrader))
                                        (into {}))}))
  p/asset-mapper
  (to-api [_ asset]
    (if-let [asset-id (get-in @dict-a [:dict-by-name asset])]
      asset-id
      asset))
  (from-api [_ asset-api]
    (if-let [asset-name (get-in @dict-a [:dict-by-id asset-api])]
      asset-name
      asset-api)))

(defmethod p/create-asset-mapper :fix
  [account log]
  (let [dict-a (atom {})]
    (fix-asset-mapper. dict-a account log)))