(ns slide-explorer.setup
  (:require [slide-explorer.user-controls :as controls]
            [slide-explorer.main :as main])
  (:import (javax.swing JFrame JButton)
           (java.awt FlowLayout)
           (java.awt.event ActionListener)))

(def single-frame (atom nil))

(defn create-frame
  []
  (doto (JFrame. "Slide Explorer Setup")
    (.setLayout (FlowLayout. FlowLayout/CENTER))))

(defn construct-frame [gui-window]
  (let [frame (create-frame)]
    (doto
      (.getContentPane frame)
      (.add (controls/button "Load..." #(main/load-data-set)))
      (.add (controls/button "New..." #(main/go))))
    (controls/show-window-center frame 250 100 gui-window)
    frame))

(defn show-frame [gui-window]
  (when-not @single-frame
    (reset! single-frame (construct-frame gui-window)))
  (doto @single-frame .show))
                     
(defn test-frame []
  (construct-frame nil))