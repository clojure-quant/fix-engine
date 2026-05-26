(ns demo.quote-decode
  (:require
   [missionary.core :as m]
   [fix-translator.schema :refer [create-decoder]]
   [fix-translator.message :refer [fix->payload
                                   encode-fix-msg]]
   [fix-translator.ctrader :refer [->quote]]
  [fix-engine.impl.session :refer [encode-msg decode-msg]]
   ))


(def decoder (create-decoder "fix-specs/ctrader.edn"))

decoder

(decode-msg {:decoder decoder}
            [["8" "FIX.4.4"] ["9" "145"] ["35" "w"] ["34" "202"] ["49" "CSERVER"] ["50" "QUOTE"] ["52" "20260525-16:20:29.542"] ["56" "live.fxpro.8284171"] ["57" "QUOTE"] ["55" "3"] ["262" "nm7yP"] ["268" "2"] ["269" "0"] ["270" "184.958"] ["269" "1"] ["270" "184.964"] ["10" "012"]]
            
            )
            
(try
  (fix->payload decoder [["8" "FIX.4.4"]
                       ["9" "145"] 
                       ["35" "w"] 
                       ["34" "202"] 
                       ["49" "CSERVER"]
                       ["50" "QUOTE"]
                       ["52" "20260525-16:20:29.542"]
                       ["56" "live.fxpro.8284171"]
                       ["57" "QUOTE"]
                       ["55" "3"]
                       ["262" "nm7yP"]
                         ["268" "2"] ["269" "0"] ["270" "184.958"] ["269" "1"] ["270" "184.964"] ["10" "012"]])

  (catch Exception e
    (ex-data e)))



(->quote [["8" "FIX.4.4"] ["9" "145"] ["35" "w"] ["34" "202"]
          ["49" "CSERVER"] ["50" "QUOTE"] ["52" "20260525-16:20:29.542"] ["56" "live.fxpro.8284171"]
          ["57" "QUOTE"]
          ["55" "3"]
          ["262" "nm7yP"]
          ["268" "2"]
          ["269" "0"]
          ["270" "184.958"]
          ["269" "1"]
          ["270" "184.964"]
          ["10" "012"]])

[["8" "FIX.4.4"] ["9" "145"] ["35" "W"] ["34" "202"] ["49" "CSERVER"] ["50" "QUOTE"] ["52" "20260525-16:20:29.542"] ["56" "live.fxpro.8284171"] ["57" "QUOTE"] ["55" "3"] ["262" "nm7yP"] ["268" "2"] ["269" "0"] ["270" "184.958"] ["269" "1"] ["270" "184.964"] ["10" "012"]]
