


; admin
                :logon (logon-accepted msg-type m session)
                :logout (logout-accepted msg-type m session)
                :reject (session-reject)
                :resend-request (resend-request)
                :seq-reset (sequence-reset msg-type m session)
                :heartbeat (heartbeat)
                :test-request (test-request msg-type m session)


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