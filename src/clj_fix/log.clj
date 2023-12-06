(ns clj-fix.log)

(defn log [session-id in-out msg-type msg]
  (let [file-name (str "log/" session-id)]
    ;(println "logging to: " file-name)
    (spit file-name (str "\r\n" in-out " " msg-type " " msg) :append true)))

(comment
  (log :demo "IN" :test "8=5,9=343,55=DEMO"))

