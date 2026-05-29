(ns demo.cli-quote-print
  (:require
   [missionary.core :as m]
   [fix-engine.account :as account]
   [fix-engine.impl.log-flow :refer [flow-sender]]
   [fix-engine.impl.tcp.boot :refer [boot-with-retry]]
   [fix-engine.impl.interactor.quote :refer [create-quote-interactor]]
   [fix-engine.logger :refer [log]]))

(defn- dbg [& args]
  (apply println "[cli-quote-print]" args)
  (flush))

(defn- load-account [account-name]
  (dbg "loading account" account-name "cwd=" (System/getProperty "user.dir"))
  (let [cfg (account/find-account (account/load-accounts-file "fix-accounts.edn") account-name)]
    (dbg "account loaded?" (boolean cfg))
    cfg))

(defn- account-log-fn [account-config send subscriber-ready?]
  (fn [event]
    (let [e (merge {:account/id (:account/id account-config)
                    :account/name (:account/name account-config)}
                   event)]
      (if @subscriber-ready?
        (do (dbg "log-fn send" (:type event) (:direction event))
            (send e))
        (dbg "log-fn DROPPED (no subscriber yet):" (:type event))))))

(defn- log-event-printer
  "Consumes the log flow (single consumer via m/reduce)."
  [log-f]
  (m/reduce
   (fn [_ event]
     (dbg "log-event:" (:type event) (:direction event)
          (when (#{:fix-str} (:type event))
            (subs (str (:data event)) 0 (min 80 (count (str (:data event)))))))
     (log "EVENT" event)
     nil)
   nil
   log-f))

(defn quote-consumer
  "Continuously takes quotes from quote-rdv (single m/sp loop; one take per quote)."
  [quote-rdv]
  (m/sp
   (loop []
     (let [q (m/? quote-rdv)]
       (dbg "quote-rdv take:" q)
       (log "QUOTE" q)
       (recur)))))

(defn start []
  (dbg "start()")
  (let [account-config (load-account "fxpro-ctrader-quote")
        _ (when-not account-config
            (throw (ex-info "missing account fxpro-ctrader-quote in fix-accounts.edn" {})))
        {:keys [flow send]} (flow-sender)
        subscriber-ready? (atom false)
        log-fn (account-log-fn account-config send subscriber-ready?)
        subscription-a (atom #{"EURUSD" "USDJPY"})
        quote-rdv (m/rdv)
        interactor (create-quote-interactor subscription-a quote-rdv)
        _ (dbg "starting quote consumer (rdv taker, before boot)")
        quote-consumer-t (quote-consumer quote-rdv)
        dispose-quote (quote-consumer-t #(dbg "quote-consumer done" %)
                                        #(dbg "quote-consumer CRASH" %))
        _ (dbg "starting log consumer (m/reduce on flow)")
        log-consumer (log-event-printer flow)
        dispose-log (do
                      (reset! subscriber-ready? true)
                      (dbg "flow subscriber active")
                      (log-consumer #(dbg "log-consumer done" %)
                                    #(dbg "log-consumer CRASH" %)))
        boot-t (boot-with-retry account-config log-fn interactor)
        _ (dbg "starting boot")
        dispose-boot (boot-t #(dbg "boot completed" %)
                             #(dbg "boot CRASH" %))]
    (dbg "start() done")
    {:log-f flow
     :quote-rdv quote-rdv
     :dispose-boot dispose-boot
     :dispose-log dispose-log
     :dispose-printer dispose-quote
     :subscription-a subscription-a}))

(defn start-cli [& _]
  (dbg "start-cli()")
  (start)
  (dbg "blocking (Ctrl-C to exit)")
  @(promise))
