(ns fix-engine.impl.tcp.socket
  (:require
   [missionary.core :as m]
   [aleph.tcp :as tcp]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [gloss.io :as io]
   [fix-translator.gloss :refer [fix-protocol]]
   [fix-translator.message-wire :refer [vec->wire]]
   [fix-engine.account :as account]
   [fix-engine.impl.tcp.certificate :refer [create-certificate-manager]])
  (:import missionary.Cancelled))

(defn- dbg [& args]
  (apply println "[tcp.socket]" args)
  (flush))

(defn- deferred->task
  [df]
  (let [v (m/dfv)]
    (d/on-realized df
                   (fn [r] (v (fn [] r)))
                   (fn [e] (v (fn [] (throw e)))))
    (m/absolve v)))

(defn- connected? [stream]
  (when stream
    (let [desc (s/description stream)]
      (and (not (-> desc :sink :closed?))
           (not (-> desc :source :closed?))))))

(defn- wrap-duplex-stream
  "Duplex stream: outbound raw FIX strings, inbound tag=value pairs (gloss framing)."
  [s]
  (let [out (s/stream)]
    (s/connect
     (s/map identity out)
     s
     {:upstream? true
      :downstream? true})
    (s/splice
     out
     (io/decode-stream s fix-protocol))))

(defn- read-pair-t [stream]
  (deferred->task (s/take! stream)))

(defn- read-fix-vec-t
  "Reads one complete FIX message (tag/value vector) from stream inside m/sp."
  [stream]
  (m/sp
   (loop [acc []]
     (if-let [pair (m/? (read-pair-t stream))]
       (let [acc (conj acc pair)]
         (if (= "10" (first pair))
           acc
           (recur acc)))
       nil))))

(defn connect
  "Connects using `:tcp` from `:account/settings`.
   Returns a missionary task producing {:push fn :pull fn} for FIX wire strings."
  [account]
  (m/sp
   (let [{:keys [host port ssl]} (:tcp (account/settings account))
         tcp-config (cond-> {:host host :port port}
                      ssl (merge (create-certificate-manager)))
         _ (dbg "connect" tcp-config)
         stream (m/? (deferred->task (d/chain (tcp/client tcp-config) wrap-duplex-stream)))
         _ (dbg "connected")
         pull (fn []
                (m/sp
                 (try
                   (when-let [fix-vec (m/? (read-fix-vec-t stream))]
                     (vec->wire fix-vec))
                   (catch Cancelled _
                     nil))))
         push (fn [fix-str]
                (m/sp
                 (when-not (connected? stream)
                   (throw (ex-info "tcp push failed (not connected)" {})))
                 (let [ok? (m/? (deferred->task (s/put! stream fix-str)))]
                   (when-not ok?
                     (throw (ex-info "tcp push failed" {:fix-str fix-str}))))))]
     {:push push
      :pull pull})))
