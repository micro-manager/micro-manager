(ns slide-explorer.wells
  (:import (javax.swing JFrame JPanel)
           (java.awt Color Graphics Graphics2D))
  (:require [slide-explorer.paint :as paint]))

(defn compute-wells [nx ny diameter spacing-x spacing-y]
  (for [i (range nx)
        j (range ny)]
    {:x (* i spacing-x)
     :y (* j spacing-y)
     :size-x diameter
     :size-y diameter}))
  
(defn paint-well [graphics {:keys [x y size-x size-y type]}]
  (condp = type
    :circle
    (doto graphics
      (.setColor Color/DARK_GRAY)
      (.fillOval x y size-x size-y)
      (.setColor Color/WHITE)
      (.drawOval x y size-x size-y))
    :rect
    (doto graphics
      (.setColor Color/DARK_GRAY)
      (.fillRect x y size-x size-y)
      (.setColor Color/WHITE)
      (.drawRect x y size-x size-y))))

(defn paint-wells [graphics {:keys [nx ny type]}]
  (doseq [well (compute-wells nx ny
                              20 25 25)]
    (paint-well graphics (assoc well :type type))))

(defn paint-labels [graphics well-state]
  (.setColor graphics Color/WHITE)
  (doseq [column (range (:nx well-state))]
    (paint/draw-string-center graphics (str (inc column)) (+ 10 (* 25 column)) -10))
  (doseq [row (range (:ny well-state))]
    (paint/draw-string-center graphics (str (char (+ 65 row))) -10 (+ 10 (* 25 row)))))

(def angle (atom 0))

(defn paint-well-panel [^Graphics graphics well-state]
  (doto graphics
    paint/enable-anti-aliasing
    (.translate 30 30)
    (.scale 1.5 1.5)
    (.rotate @angle)
    (.setColor Color/WHITE)
    (paint-labels well-state)
    (paint-wells well-state)))

(defn well-panel [well-state-atom]
  (doto
    (proxy [JPanel] []
      (paintComponent [^Graphics graphics]
                      (proxy-super paintComponent graphics)
                      (paint-well-panel graphics @well-state-atom)))
    (.setBackground Color/BLACK)))
  

(defn test-wells []
  (let [well-state-atom (atom {:nx 12 :ny 8 :type :circle :width 20 :height 20
                               :x-spacing 25 :y-spacing 25})]
    (doto (JFrame.)
      (.setBounds 30 30 400 400)
       (.add (well-panel well-state-atom))
      .show)
    well-state-atom))