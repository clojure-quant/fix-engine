(ns demo.demo3
  (:require 
    [missionary.core :as m]
    [fix-translator.gloss :refer [fix-protocol xf-fix-message without-header]]
   )
  )

(def msg1 
  [ ["8" "FIX.4.4"]
    ["9" "115"]
    ["35" "A"]
    ["34" "1"]
    ["49" "cServer"]
    ["50" "QUOTE"]
    ["52" "20250227-17:26:16.863"]
    ["56" "demo.tradeviewmarkets.3193335"]
    ["57" "QUOTE"]
    ["98" "0"]
    ["108" "60"]
    ["141" "Y"]
    ["10" "218"]]
  )

(def msg23
  [
   ["8" "FIX.4.4"]
   ["9" "144"]
   ["35" "W"]
   ["34" "3"]
   ["49" "cServer"]
   ["50" "QUOTE"]
   ["52" "20250227-17:26:16.864"]
   ["56" "demo.tradeviewmarkets.3193335"]
   ["57" "QUOTE"]
   ["55" "1"]
   ["268" "2"]
   ["269" "0"]
   ["270" "1.04126"]
   ["269" "1"]
   ["270" "1.04127"]
   ["10" "027"]
   ["8" "FIX.4.4"]
   ["9" "144"]
   ["35" "W"]
   ["34" "4"]
   ["49" "cServer"]
   ["50" "QUOTE"]
   ["52" "20250227-17:26:16.864"]
   ["56" "demo.tradeviewmarkets.3193335"]
   ["57" "QUOTE"]
   ["55" "4"]
   ["268" "2"]
   ["269" "0"]
   ["270" "149.854"]
   ["269" "1"]
   ["270" "149.859"]
   ["10" "069"]])


(m/? (->> (m/seed (concat msg1 msg23))
        (m/eduction xf-fix-message)
        (m/reduce conj)))