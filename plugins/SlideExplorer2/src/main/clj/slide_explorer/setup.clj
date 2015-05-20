(ns slide-explorer.setup
  (:require [slide-explorer.widgets :as widgets]
            [slide-explorer.main :as main]
            [slide-explorer.utils :as utils])
  (:import (javax.swing JFrame JButton JTextArea)
           (java.awt FlowLayout)
           (java.awt.event ActionListener)))

(def single-frame (atom nil))

(defn create-frame
  []
  (doto (JFrame. "Slide Explorer Setup")
    (.setLayout (FlowLayout. FlowLayout/CENTER))))

(def cheat-sheet-text
" Slide Explorer controls:

  Mouse drag: Pan
  +/-: Zoom in/out
  Alt + Left click: Zoom in
  Alt + Right click: Zoom out
  </>: Slice up/down
  F: Enter or exit full screen mode
  Esc: Exit full screen mode
  1: Hide or show 1x view

 For new data sets: 
  
  Channels and z-stack settings are
  borrowed from the multi-dimensional
  acquisition window.
 
  Ctrl/Cmd + Click: Add/remove positions
  Double-click: Navigate to location
 
  Yellow box: Current stage position
  Red boxes: Positions in position list
")
    

(defn cheat-sheet []
  (doto (JTextArea. cheat-sheet-text)
    (.setEditable false)))

(defn construct-frame [gui-window]
  (let [frame (create-frame)]
    (doto
      (.getContentPane frame)
      (.add (widgets/button "Load..." #(main/load-data-set)))
      (.add (widgets/button "New..." #(main/create-data-set)))
      (.add (cheat-sheet)))
    (doto frame
      (.setResizable false)
      (widgets/show-window-center 400 500 gui-window))
    frame))

(defn show-frame [gui-window]
  (when-not @single-frame
    (reset! single-frame (construct-frame gui-window)))
  (doto @single-frame .show))
                     
(defn test-frame []
  (reset! utils/testing true)
  (construct-frame nil))