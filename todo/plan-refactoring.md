I currently have demo.cli-quote-raw and demo.cli-trade-raw demos working.
The problem is that the current design are:
   1. there is no sytematic logging 
   2. fix-session heartbeats / login is the same for trade and quote.
   3. data passing should be done via missionary rendevous and not via flows.
   4. tcp connections can be plain or use ssl


new fix-account-config format:

{:account/name "fxpro-ctrader-quote"
 :account/id 123456 
 :spec "fix-specs/ctrader.edn"
 :header {:begin-string "FIX.4.4"
          :target-comp-id "CSERVER"
          :sender-comp-id "live.fxpro.8284171"
          :target-sub-id "QUOTE"
          :sender-sub-id "QUOTE"}
  :tcp {:host "live-uk-eqx-01.p.c-trader.com"
        :port 5201 
        :ssl false}
  :login {:username "8284171"
          :password "Regenschirm13!"}}


(defn msg-flow [!-a]
  ; without the stream the last subscriber gets all messages
  (m/stream
   (m/observe
    (fn [!]
      (reset! !-a !)
      (fn []
        (reset! !-a nil))))))


(defn flow-sender
  "returns {:flow f
            :send s}
    (s v) pushes v to f."
  []
  (let [!-a (atom nil)]
    {:flow (msg-flow !-a)
     :send (fn [v]
             (when-let [! @!-a]
               (! v)))}))

loggable events:
{:type :fix-str :fix-vec :fix-payload :connection-status
 :direction :in :out
 :data string/vecs/map}

create the following namespaces:

1. fix-engine.impl.tcp.socket

  use the majority of code from: fix-engine.impl.socket
  (defn connect [fix-account-config])
     returns a missionary sp process that returns 
     {
      :push a missionary task to send a string onto the socket
      :pull a missionary task that receives a string from the socket}

2. fix-engine.impl.fix-session    
   use the majority of code from: fix-engine.impl.quotes. this file also has quote-interactor-stuff
   the idea is that fix-session manages login, logout, heartbeats and logging.
   (defn create-fix-session-task [fix-account-config tcp-socket interactor])
     returns 
     {:connection-id a nano-id (nano-id 16) identifiying each outgoing connection.
      :session a state object created via fix-translator.session
      :log-f a missionary flow of loggable events.
      :push a missionary task that gets a vector of [msg-type payload-map]
        the push task does the following:
            1. (log {:type :fix-payload :direction :out :data fix-payload :account/id 1234 :account/name "fxpro-ctrader-quote" })
            2. convert fix-payload to fix-vec
               (log {:type :fix-vec :direction :out :data fix-vec  :account/id 1234 :account/name "fxpro-ctrader-quote" })
            3. convert fix-vec to fix-str
               (log {:type :fix-str :direction :in :data fix-str  :account/id 1234 :account/name "fxpro-ctrader-quote" })
            4. call push of tcp-socket with fix-str
      :pull a missionary task that gets a vector of [msg-type payload-map]
        the pull task does the following:
            1. call pull of tcp-socket to fix-str
               (log {:type :fix-str :direction :in :data fix-str  :account/id 1234 :account/name "fxpro-ctrader-quote" })
            2. convert fix-str to fix-vec
               (log {:type :fix-vec :direction :in :data fix-vec  :account/id 1234 :account/name "fxpro-ctrader-quote" })
            3. convert fix-vec to fix-payload
               (log {:type :fix-payload :direction :in :data fix-payload  :account/id 1234 :account/name "fxpro-ctrader-quote" })
       :run a missionary task that 
            1. sends login message
            2. reads incoming messages
            3. a loop that checks if login was accepted        
               after login is accepted, it runs (interactor fix-account-config pull push)
               sends heartbeats
            4. stops when incoming heartbeats are missed.   
     } 


3. fix-engine.impl.interactor.quote
   use the majority of code from: fix-engine.impl.quotes. this file also has fix-session stuff
   (defn create-quote-interactor [subscription-a quote-rdv]
      (fn [fix-account-config connection-id pull push log]
         1. build ctrader symbol conversion dictionary.
         1. a task that watches subscription-a and subscribes/unsubscribes quotes accordingly
         2. log when there was a subscription failure
         3. delivers quotes that have normalized symbol to quote-rdv.
      )
   )
    

4. fix-engine.impl.interactor.trade
 a draft of this is already in: fix-engine.impl.trade
(defn create-trade-interactor [req-rdv res-rdv]
      (fn [fix-account-config connection-id pull push log]
         1. build ctrader symbol conversion dictionary.
         1. a task that reads from req-rdv and sends fix-events using push (but only when they are not older than 5000 ms)
            we need to send-order cancel-order and get-positions here.
         2. log when there was a order send/cancel failure
         3. delivers order-updates with normalized symbol to res-rdv
      )
   )


5. fix-engine.impl.tcp.boot
   a draft of this is already in fix-engine.impl.boot
   
   (defn boot-with-retry [fix-account-config interactor]
      it will retry connecting:
      1. fix-engine.impl.tcp.socket/connect
      2. fix-engine.impl.fix-session/create-fix-session-task and run this task)


6. add demo.cli-quote-print
   start a a quote-account and print all received quotes.

7. add demo.cli-trade   
   cart a trade-account and print all received updates.
   every 5  seconds send a new random order

