(ns slide-explorer.tiles
  (:import [java.awt.geom AffineTransform]))

(defn next-tile [[x y]]
  (let [radius (Math/max (Math/abs x) (Math/abs y))]
    (cond
      ;; special cases:
      (== 0 x y) [1 0]
      (and (== -1 y) (== x radius)) [(inc x) 0]
      ;; corners:
      (== x y radius) [(dec x) y]
      (== (- x) y radius) [x (dec y)]
      (== (- x) (- y) radius) [(inc x) y]
      (== x (- y) radius) [x (inc y)]
      ;; edges:
      (== x radius) [x (inc y)]
      (== y radius) [(dec x) y]
      (== x (- radius)) [x (dec y)]
      (== y (- radius)) [(inc x) y])))

(def tile-list (iterate next-tile [0 0]))
          
(defn offset-tiles [[delta-x delta-y] tiles]
  (map (fn [tile]
         (let [[x y] tile]
           [(+ delta-x x) (+ delta-y y)])) tiles))