(ns fix-engine.impl.fix-session
  (:require
   [missionary.core :as m]
   [nano-id.core :refer [nano-id]]
   [tick.core :as t]
   ;[fix-engine.account :as account]
   [fix-translator.schema :refer [get-msg-type]]
   [fix-translator.message :refer [encode-message]]
   [fix-translator.session :refer [create-session fix-msg-vec->payload]]
   [fix-translator.message-wire :refer [vec->wire wire->vec]]
   [fix-translator.ctrader :refer [seclist->assets create-asset-converter]])
  (:import missionary.Cancelled))

(defn- dbg [& args]
  (apply println "[fix-session]" args)
  (flush))

(defn login-payload [account]
  (let [{:keys [username password]} (:login (:account/settings account))]
    [:logon {:encrypt-method :none-other
             :heart-bt-int 60
             :reset-seq-num-flag "Y"
             :username (str username)
             :password (str password)}]))

(defn security-list-request []
  [:security-list-request {:security-req-id (nano-id 5)
                           :security-list-request-type :symbol}])

(def heartbeat-payload [:heartbeat {}])

(defn- header-msg-type
  "App message keywords (e.g. :new-order-single) are not encoded on the FIX
  header by fix-translator; resolve the wire value from the message spec."
  [session msg-type]
  (or (:msgtype (get-msg-type (:decoder session) msg-type))
      msg-type))

(defn- payload->fix-msg-vec
  [session [msg-type payload]]
  (let [{:keys [decoder config outbound-seq-num]} session
        seq-num (swap! outbound-seq-num inc)
        header (assoc (:header config)
                      :msg-type (header-msg-type session msg-type)
                      :msg-seq-num seq-num
                      :sending-time (t/instant))
        fix-message {:header header :payload payload}]
    (encode-message decoder fix-message)))

(defn- timeout-watchdog
  [mailbox ms]
  (m/sp
   (loop []
     (when (= :timeout (m/? (m/timeout mailbox ms :timeout)))
       (throw (ex-info "No message received after specified time"
                       {::type ::timeout ::time-seconds (int (/ ms 1000))})))
     (recur))))

(defn- heartbeat-sender
  [push]
  (m/sp
   (loop []
     (m/? (m/sleep 25000))
     (m/? (push heartbeat-payload))
     (recur))))

(defn- tcp-reader
  "Reads FIX strings from tcp, parses to [msg-type payload], delivers on mailbox."
  [tcp-pull session log in-mbx keepalive]
  (m/sp
   (try
     (loop []
       (when-let [fix-str (m/? (tcp-pull))]
         (log {:type :fix-str :direction :in :data fix-str})
         (let [fix-vec (wire->vec fix-str)]
           (log {:type :fix-vec :direction :in :data fix-vec})
           (let [payload (fix-msg-vec->payload session fix-vec)]
             (log {:type :fix-payload :direction :in :data (second payload)
                   :fix-msg-type (first payload)})
             (keepalive nil)
             (in-mbx payload)
             (recur)))))
     (catch Cancelled _
       (in-mbx nil)))))

(defn create-fix-session-task
  [account tcp-socket log interactor]
  (let [connection-id (nano-id 16)
        session (create-session (:account/settings account))
        log* (fn [event]
               (log (assoc event :connection-id connection-id)))
        tcp-push (:push tcp-socket)
        tcp-pull (:pull tcp-socket)
        in-mbx (m/mbx)
        push (fn [fix-payload]
               (m/sp
                (let [[msg-type payload] fix-payload
                      _ (log* {:type :fix-payload :direction :out :data payload :fix-msg-type msg-type})
                      fix-vec (payload->fix-msg-vec session fix-payload)
                      _ (log* {:type :fix-vec :direction :out :data fix-vec})
                      fix-str (vec->wire fix-vec)]
                  (log* {:type :fix-str :direction :out :data fix-str})
                  (m/? (tcp-push fix-str)))))
        pull (fn []
               (m/sp
                (m/? in-mbx)))
        run (m/sp
             (let [keepalive (m/mbx)
                   reader (tcp-reader tcp-pull session log* in-mbx keepalive)
                   session-body (m/sp
                                 (try
                                   (dbg "session: connecting")
                                   (log* {:type :connection-status :data :connecting})
                                   (dbg "session: sending logon")
                                   (m/? (push (login-payload account)))
                                   (loop [n 0]
                                     (let [[msg-type _] (m/? (pull))]
                                       (dbg "session: waiting for logon, got" msg-type "n=" n)
                                       (if (= msg-type :logon)
                                         (do (log* {:type :connection-status :data :logged-in})
                                             (dbg "session: logged in"))
                                         (recur (inc n)))))
                                   (dbg "session: requesting security-list")
                                   (m/? (push (security-list-request)))
                                   (let [asset-converter
                                         (loop [n 0]
                                           (let [fix-payload (m/? (pull))
                                                 [msg-type _] fix-payload]
                                             (dbg "session: waiting for security-list, got" msg-type "n=" n)
                                             (case msg-type
                                               :security-list
                                               (do (dbg "session: security-list ok, assets="
                                                        (count (or (seclist->assets fix-payload) [])))
                                                   (or (create-asset-converter (seclist->assets fix-payload))
                                                       (throw (ex-info "security-list invalid" {:payload fix-payload}))))
                                               :logout
                                               (throw (ex-info "logout before security-list" {:payload fix-payload}))
                                               (recur (inc n)))))]
                                     (dbg "session: ready, starting interactor")
                                     (log* {:type :connection-status :data :ready})
                                     (m/? (m/join vector
                                                  (interactor account connection-id push pull log* asset-converter)
                                                  (heartbeat-sender push)
                                                  (timeout-watchdog keepalive 90000))))))]
               (try
                 (m/? (m/join vector reader session-body))
                 (catch Cancelled _
                   :cancelled)
                 (catch Exception ex
                   (dbg "session: exception" (ex-message ex) (ex-data ex))
                   (log* {:type :connection-status :data {:error (ex-message ex) :data (ex-data ex)}})
                   (throw ex))
                 (finally
                   (log* {:type :connection-status :data :disconnected})))))]
    {:connection-id connection-id
     :session session
     :push push
     :pull pull
     :run run}))
