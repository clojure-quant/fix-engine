(ns clj-fix.core
  (:require
   [clojure.string :as s]
   [lamina.core :as l]
   [aleph.tcp :as a]
   [gloss.core :as g]
   [cheshire.core :as c]
   [tick.core :as t]
   [fix-translator.core :refer [encode-msg decode-msg extract-tag-value get-msg-type load-spec]]
   [clj-fix.connection.protocol :as p]
   [clj-fix.quotes :as quotes]
   [clj-fix.log :refer [log]]
   )
  (:import (java.util.concurrent Executors Future TimeUnit)))

; FIX messages end with '10=xxx' where 'xxx' is a three-digit checksum
; left-padded with zeroes.
(def ^:const msg-delimiter #"10=\d{3}\u0001")
(def ^:const msg-identifier #"(?<=10=\d{3}\u0001)")
(def heartbeat-transmitter (atom nil))

; The standard tag value for FIX message sequence numbers.
(def ^:const seq-num-tag 34)

(def sessions (atom {}))
(def order-id-prefix (atom 0))

(defrecord Conn [label venue host port sender sender-sub target target-sub
                 username password
                 channel in-seq-num
                 out-seq-num next-msg msg-fragment translate?])

(declare disconnect)


(defn now-utc []
  (-> (t/now)
      (t/in "UTC")))

(defn timestamp
  "Returns a UTC timestamp in a specified format."
  ([]
   (timestamp "yyyyMMdd-HH:mm:ss"))
  ([format]
   (t/format (t/formatter format) (now-utc))))

(defn- error
  "Throws an exception with a user-supplied msg."
  [msg]
  (throw (Exception. msg)))

(defn get-session
  "Returns the session details belonging to a session id."
  [id]
  ((:id id) @sessions))

(defn get-channel
  "Returns the channel used by a session."
  [session]
  @@(:channel session))

(defn open-channel?
  "Returns whether a session's channel is open."
  [session]
  (and (not= nil @(:channel session)) (not (l/closed? (get-channel session)))))

(defn create-channel
  "If a session doesn't already have an open channel, then create a new one and
   assign it to the session."
  [session]
  (if (not (open-channel? session))
    (do
      (reset! (:channel session) (a/tcp-client {:host (:host session),
                                                :port (:port session),
                                                :frame (g/string :ascii)}))
      (try (get-channel session)
           (catch java.net.ConnectException e
             (reset! (:channel session) nil)
             (println "create-cannel exception: ")
             (println (.getMessage e)))))
    (error "Channel already open.")))

(defn segment-msg
  "Takes a TCP segment and splits it into a collection of individual FIX
   messages. If the last message in the collection is complete, it appends an
   empty string."
  [msg]
  (println "segment-msg: " msg)
  (let [segments (s/split msg msg-identifier)]
    (if (re-find msg-delimiter (peek segments))
      (conj segments "")
      segments)))

(defn gen-msg-sig
  "Returns a vector of spec-neutral tags and their values as required to create
   a FIX message header."
  [session]
  (let [{:keys [out-seq-num sender sender-sub target target-sub]} session]
    [:sender-comp-id sender
     :target-comp-id target
     :sender-sub-id sender-sub
     :target-sub-id target-sub
     :msg-seq-num (swap! (:out-seq-num session) inc)
     :sending-time (timestamp)]))

(defn send-msg
  "Transforms a vector of spec-neutral tags and their values in the form [t0 v0
   t1 v1 ...] into a FIX message, then sends it through the session's channel."
  [session msg-type msg-body]
  (let [msg (reduce #(apply conj % %2) [[:msg-type msg-type]
                                        (gen-msg-sig session) msg-body])
        encoded-msg (encode-msg (:venue session) msg)]
    (println "FIX-OUT type:" msg-type " body: " encoded-msg)
    (log :msg "OUT" msg-type encoded-msg)
    (l/enqueue (get-channel session) encoded-msg)))


(defn update-next-msg
  [old-msg new-msg] new-msg)

(defn update-user
  "Updates a session's next message agent. This agent holds the latest FIX
   message expected to be processed by the user. A user-supplied watcher is
   attached to this agent."
  [session payload]
  (send (:next-msg session) update-next-msg payload))

(defn transmit-heartbeat
  ([session]
   (when (open-channel? session)
     (send-msg session :heartbeat [[]])))
  ([session test-request-id]
   (when (open-channel? session)
     (send-msg session :heartbeat [:test-request-id test-request-id]))))


(defn start-heartbeats [heartbeat-fn interval]
  (reset! heartbeat-transmitter (Executors/newSingleThreadScheduledExecutor))
  (.scheduleAtFixedRate @heartbeat-transmitter heartbeat-fn interval interval
                        TimeUnit/SECONDS))

(defn stop-heartbeats []
  (.shutdown @heartbeat-transmitter)
  (reset! heartbeat-transmitter nil))

(defn logon-accepted [msg-type msg session]
  (println "logon-accepted msg-type: " msg-type " msg: " msg)
  (let [venue (:venue session)
        decoded-msg (decode-msg venue msg-type msg)]
    (update-user session {:msg-type msg-type
                          :sender-comp-id (:sender-comp-id decoded-msg)})
    (start-heartbeats (fn [] (transmit-heartbeat session))
                      (:heartbeat-interval decoded-msg))))

(defn heartbeat [])

(defn test-request [msg-type msg session]
  (let [venue (:venue session)]
    (transmit-heartbeat session (:test-request-id (decode-msg venue msg-type
                                                              msg)))))

(defn session-reject []
  (println "SESSION REJECT"))

(defn execution-report [msg-type msg session]
  (let [venue (:venue session)]
    (if @(:translate? session)
      (update-user session (merge {:msg-type msg-type}
                                  (decode-msg venue msg-type msg)))
      (update-user session {:msg-type msg-type :report msg}))))

(defn order-cancel-reject []
  (println "ORDER CANCEL REJECT"))

(defn logout-accepted [msg-type msg session]
  (println "logout-accepted!")
  (log :msg "IN " :logout "logout-accepted")
  (let [venue (:venue session)]
    (stop-heartbeats)
    (update-user session {:msg-type msg-type
                          :sender-comp-id (:sender-comp-id
                                           (decode-msg (:venue session)
                                                       msg-type msg))})))
(defn resend-request []
  (println "RESEND REQUEST"))

(defn sequence-reset [msg-type msg session]
  (let [venue (:venue session)
        decoded-msg (decode-msg venue msg-type msg)
        cur-in-seq-num (:in-seq-num session)
        new-seq-num (:new-seq-num decoded-msg)
        dup-flag (:poss-dup-flag decoded-msg)]
    (if (and (= dup-flag "no") (< new-seq-num cur-in-seq-num))
      (do
        (disconnect session)
        (error (str "Inbound sequence-reset message requested reset to "
                    new-seq-num " while current sequence number is "
                    cur-in-seq-num ". No possible duplicate flag set.")))
      (reset! (:in-seq-num session) (- new-seq-num 1)))))

(defn msg-handler
  "Segments an inbound block of messages into individual messages, and processes
   them sequentially."
  [id msg]
  (println "msg-handler id: " (:id id) " msg: " msg)
  (log :msg "IN1" :raw msg)
  (let [session (get-session id)
        msg-fragment (:msg-fragment session)
        segments (segment-msg (str @msg-fragment msg))
        lead-msg (first segments)]
    ; Proceed with processing if there is at least one complete message in
    ; the collection.
    (when (re-find msg-delimiter lead-msg)
      (let [inbound-seq-num (Integer/parseInt (extract-tag-value seq-num-tag
                                                                 lead-msg))
            cur-seq-num (inc @(:in-seq-num session))]
        (if (= inbound-seq-num cur-seq-num)
          (doseq [m (butlast segments)]
            (let [msg-type (get-msg-type (:venue session) m)
                  _ (swap! (:in-seq-num session) inc)]
              (log :msg "IN " msg-type lead-msg)
              (case msg-type
                ; admin
                :logon (logon-accepted msg-type m session)
                :logout (logout-accepted msg-type m session)
                :reject (session-reject)
                :resend-request (resend-request)
                :seq-reset (sequence-reset msg-type m session)
                :heartbeat (heartbeat)
                :test-request (test-request msg-type m session)
                ; trade
                :execution-report (execution-report msg-type m session)
                :order-cancel-reject (order-cancel-reject)
                ; quote
                :quote-data-full (quotes/quote-data-full msg-type m session)
                :quote-security-list (quotes/quote-security-list msg-type m session)
                ; unknown
                :unknown-msg-type (println "UNKNOWN MSG TYPE: " msg-type))))

          (send-msg session :resend-request [:begin-seq-num cur-seq-num
                                             :ending-seq-num 0]))))

    (reset! msg-fragment (peek segments))))

(defn gen-msg-handler
  "Returns a message handler for the session's channel."
  [id]
  (fn [msg]
    (println "gen-msg-handler msg: " msg)
    (msg-handler id msg)))

(defn replace-with-map-val
  "Takes a vector of tag-value pairs and a map of tag-value pairs. For each tag
   that is present only in the vector, return it. For each tag that is present
   in both the vector and the map, return the map pair."
  [tag-value-vec tag-value-map]
  (for [e (partition 2 tag-value-vec)]
    (if-let [shared-key (find tag-value-map (first e))]
      shared-key
      e)))

(defn merge-params
  "Takes a vector of tag-value pairs and a map of tag-value pairs, and
   merges them. For any tag that's present in both, take the value from the
   map."
  [required-tags-values additional-params]
  (let [reqd-keys (set (take-nth 2 required-tags-values))
        ts (apply concat (replace-with-map-val required-tags-values
                                               additional-params))]
    (reduce
     (fn [lst [tag value]]
       (if (contains? reqd-keys tag)
         lst
         (conj lst tag value))) (vec ts) (vec additional-params))))

(defn connect
  "Connect a session's channel."
  ([id translate-returning-msgs]
   (if-let [session (get-session id)]
     (do
       (println "connecting to sesion id: " id)
       (reset! (:translate? session) translate-returning-msgs)
       (create-channel session)
       (l/receive-all (get-channel session) #((gen-msg-handler id) %)))
     (error (str "Session " (:id id) " not found. Please create it first.")))))

(defrecord FixConn [id]
  p/Connection
  (logon
    [id msg-handler heartbeat-interval reset-seq-num translate-returning-msgs]
    (let [session (get-session id)]
      (when (not (open-channel? session))
        (log :msg "---CONNECT---" "" "")
        (connect id translate-returning-msgs))
      (when (= reset-seq-num :yes)
        (reset! (:out-seq-num session) 0)
        (reset! (:in-seq-num session) 0))
      (add-watch (:next-msg session) :user-callback msg-handler)
      (send-msg session :logon [:heartbeat-interval heartbeat-interval
                                :reset-seq-num reset-seq-num
                                :encrypt-method :none
                                :username (:username session)
                                :password (:password session)])))
  
    (logout [id reason]
          (send-msg (get-session id) :logout [:text reason]))
  
    ;; execution

  (new-order [id side size instrument-symbol price]
    (p/new-order id side size instrument-symbol price nil))

  (new-order [id side size instrument-symbol price additional-params]
    (let [session (get-session id)
          required-tags-values [:client-order-id (str (gensym @order-id-prefix))
                                :hand-inst :private :order-qty size
                                :order-type :limit :price price
                                :side side :symbol instrument-symbol
                                :transact-time (timestamp)]]
      (if-let [addns additional-params]
        (send-msg session :new-order-single (merge-params required-tags-values
                                                          additional-params))
        (send-msg session :new-order-single required-tags-values))))

  (cancel [id order]
    (if (not= nil order)
      (let [session (get-session id)
            orig-client-order-id (:client-order-id order)
            required-tags-values (into (vec (mapcat
                                             #(find order %)
                                             [:client-order-id :order-qty :side :symbol
                                              :transact-time])) [:orig-client-order-id orig-client-order-id])]
        (send-msg session :order-cancel-request required-tags-values))))

  (cancel-replace [id order]
    (p/cancel-replace id order nil))

  (cancel-replace [id order additional-params]
    (let [session (get-session id)
          orig-client-order-id (:client-order-id order)
          required-tags-values (into
                                [:client-order-id (str (gensym (name (:id id))))
                                 :orig-client-order-id orig-client-order-id]
                                (vec (mapcat #(find order %)
                                             [:hand-inst :order-qty :order-type :price :side :symbol
                                              :transact-time])))]
      (if-let [addns additional-params]
        (send-msg session :order-cancel-replace-request (merge-params
                                                         required-tags-values
                                                         additional-params))
        (send-msg session :order-cancel-replace-request required-tags-values))))

  (order-status [id order]
      (println "order-status: " order))
  
  ;; QUOTES
    
  (subscribe [id subscription]
    (when subscription
     (let [session (get-session id)
           required-tags-values (quotes/subscription subscription)]
              (send-msg session :quote-subscription required-tags-values))))
  
  (securitylist [id]
     (let [session (get-session id)
           required-tags-values (quotes/security-list-request)]
        (send-msg session :quote-security-list-request required-tags-values)))
  ;
  )

    
  

(defn new-fix-session
  "Create a new FIX session and add it to sessions collection."
  [label venue-name host port sender sender-sub target target-sub username password]
  (let [venue (keyword venue-name)]
    (if (load-spec venue)
      (let [id (keyword (str sender "-" target))]
        (if (not (contains? @sessions id))
          (do
            (swap! sessions assoc id (Conn. label venue host port sender sender-sub target target-sub
                                            username password
                                            (atom nil) (atom 0) (atom 0)
                                            (agent {}) (atom "") (atom nil)))
            (FixConn. id))
          (error (str "Session " id " already exists. Please close it first."))))
      (error (str "Spec for " venue " failed to load.")))))

(defn disconnect
  "Disconnect from a FIX session without logging out."
  [id]
  (if-let [session (get-session id)]
    (if (open-channel? session)
      (l/close (get-channel session)))
    (error (str "Session " id " not found."))))

(defn write-session
  "Write the details of a session to a config file for sequence tracking."
  [id]
  (let [config (c/parse-string (slurp "config/clients.cfg") true)
        session (get-session id)
        client-label (:label session)]
    (spit "config/clients.cfg" (c/generate-string
                                (assoc-in
                                 (assoc-in
                                  (assoc-in config [client-label :last-logout] (timestamp "yyyyMMdd"))
                                  [client-label :inbound-seq-num] @(:in-seq-num session))
                                 [client-label :outbound-seq-num] @(:out-seq-num session))
                                {:pretty true}))))

(defn end-session
  "End a session and remove it from the sessions collection."
  [id]
  (if-let [session (get-session id)]
    (do
      (disconnect id)
      (write-session id)
      (swap! sessions dissoc (:id id))
      true)
    (error (str "Session " id " not found."))))

(defn write-system-config
  "Write clj-fix initialization details to a configuration file. This file
   tracks the number of times clj-fix has been initialized in order to set the
   order-id-prefix to ensure unique client order ids."
  [file date order-id-prefix]
  (spit file (c/generate-string {:last-startup date
                                 :order-id-prefix order-id-prefix}
                                {:pretty true})))

(defn initialize
  "Initialize clj-fix. All this does is set the order-id-prefix to ensure unique
   client order ids for the session."
  [config-file]
  (let [today (timestamp "yyyyMMdd")
        file (str "config/" config-file ".cfg")]
    (try
      (if-let [config (c/parse-string (slurp file) true)]
        (if (= today (:last-startup config))
          (do
            (reset! order-id-prefix (inc (:order-id-prefix config)))
            (write-system-config file today @order-id-prefix))
          (do
            (write-system-config file today 0)
            (initialize config-file))))
      (catch Exception e
        (do
          (write-system-config file today 0)
          (initialize config-file))))))

(initialize "global")

(defn update-fix-session
  "Sets a session's sequence numbers to supplied values."
  [id inbound-seq-num outbound-seq-num]
  (let [session (get-session id)]
    (reset! (:in-seq-num session) inbound-seq-num)
    (reset! (:out-seq-num session) outbound-seq-num)))

(defn load-client
  "Loads client details from a configuration file and creates a new fix
   session from it."
  [client-label]
  (when-let [config (client-label (c/parse-string (slurp "config/clients.cfg")
                                                  true))]
    (let [{:keys [venue host port sender sender-sub target target-sub
                  username password
                  last-logout inbound-seq-num
                  outbound-seq-num]} config
          fix-session (new-fix-session client-label venue host port
                                       sender sender-sub
                                       target target-sub
                                       username password)]
      (when (= last-logout (timestamp "yyyyMMdd"))
        (update-fix-session fix-session inbound-seq-num outbound-seq-num))
      fix-session)))

(defn close-all
  "Clears the session collection. This is strictly for utility and will be
   removed."
  []
  (reset! sessions {}))
