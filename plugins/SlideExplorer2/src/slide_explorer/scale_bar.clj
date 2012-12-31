(ns slide-explorer.scale-bar
  (:require [slide-explorer.canvas :as canvas]))

(def log-scales-um
  [0.001 0.002 0.005
   0.01 0.02 0.05
   0.1 0.2 0.5
   1 2 5
   10 20 50
   100 200 500
   1000 2000 5000
   10000 20000 50000])

(defn nearest-log-value
  [x coll]
  (let [logx (Math/log x)]
    (apply min-key #(Math/abs (- logx (Math/log %))) coll)))

(defn nearest-round-scale-um
  [target-scale-um]
  (nearest-log-value target-scale-um log-scales-um))

(defn bar-text
  [length-um]
  (cond 
    (<= 1000. length-um 1000000.) (str (int (/ length-um 1000)) " mm")
    (<= 1.0 length-um 1000.) (str (int length-um) " \u03bcm")
    (<= 0.001 length-um 1.0) (str (int (* 1000 length-um)) " nm")
    (<= 0.000001 length-um 0.001) (str (int (* 1000000 length-um)) " pm")))

(defn bar-settings
  [pixel-size-um]
  (let [target-scale-um (* 100 pixel-size-um)
        round-scale-um (nearest-round-scale-um target-scale-um)]
    {:width-pixels (/ round-scale-um pixel-size-um)
     :text (bar-text round-scale-um)}))

(defn bar-widget 
  [pixel-size-um]
  (when pixel-size-um
    (let [{:keys [width-pixels text]} (bar-settings pixel-size-um)]
      [:compound
       {:x 10 :y 30}
       [:text
        {:text text
         :r width-pixels
         :b -3
         :color :white
         :fill true
         :font {:name "Arial"
                :size 18}}]
       [:line {:l 0 :t 0 :w width-pixels :h 0 
               :stroke {:color :white :width 2.0}}]])))

;; tests

(def scale-bar-test (atom nil))

(defn bar-test []
  (canvas/canvas-frame scale-bar-test)
  (canvas/data-editor scale-bar-test))