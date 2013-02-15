(ns slide-explorer.setup
  (:require [slide-explorer.user-controls :as controls]
            [slide-explorer.main :as main])
  (:import (javax.swing JFrame JButton JTextArea)
           (java.awt FlowLayout)
           (java.awt.event ActionListener)))

(def single-frame (atom nil))

(defn create-frame
  []
  (doto (JFrame. "Slide Explorer Setup")
    (.setLayout (FlowLayout. FlowLayout/CENTER))))

(def cheat-sheet-text
"Slide Explorer controls:

  +/-: Zoom in/out
  </>: Slice up/down
  F: Enter or exit full screen mode
  Esc: Exit full screen mode
  1: Hide or show 1x view

For live data sets: 
  Shift + Click: Add/remove positions
  Double-click: Navigate to location
")
    

(defn cheat-sheet []
  (doto
  (JTextArea. cheat-sheet-text)
    (.setEditable false)))

(defn construct-frame [gui-window]
  (let [frame (create-frame)]
    (doto
      (.getContentPane frame)
      (.add (controls/button "Load..." #(main/load-data-set)))
      (.add (controls/button "New..." #(main/go)))
      (.add (cheat-sheet)))
    (controls/show-window-center frame 400 320 gui-window)
    frame))

(defn show-frame [gui-window]
  (when-not @single-frame
    (reset! single-frame (construct-frame gui-window)))
  (doto @single-frame .show))
                     
(defn test-frame []
  (construct-frame nil))