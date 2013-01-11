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

F: Enter fullscreen
Esc: Exit fullscreen
+/-: Zoom in/out
</>: Slice up/down
Shift+Click: Add/remove positions
Double-Click: Navigate to present location")
    

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