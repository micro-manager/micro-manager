(ns slide-explorer.tiles
  (:import [java.awt.geom AffineTransform]))

;; TILE TRAJECTORY

(defn floor-int
  [x]
  (long (Math/floor x)))

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

(defn center-tile
  "Computes the center tile in a view, given tile dimensions and pixel center."
  [[pixel-center-x pixel-center-y] [tile-width tile-height]]
  [(floor-int (/ pixel-center-x tile-width))
   (floor-int (/ pixel-center-y tile-height))])

(defn number-of-tiles [{:keys [width height zoom]} [tile-width tile-height]]
  (let [largest-side
        (+ 3 (max (floor-int (/ width tile-width zoom))
                  (floor-int (/ height tile-height zoom)) 0))]
    (* largest-side largest-side)))


;; TILE <--> PIXELS

(defn tile-to-pixels [[nx ny] [tile-width tile-height] tile-zoom]
  [(int (* tile-zoom nx tile-width))
   (int (* tile-zoom ny tile-height))])

(defn pixel-rectangle-to-tile-bounds
  [rectangle [tile-width tile-height]]
  {:nl (floor-int (/ (.x rectangle) tile-width))
   :nr (floor-int (/ (+ -1 (.getWidth rectangle) (.x rectangle)) tile-width))
   :nt (floor-int (/ (.y rectangle) tile-height))
   :nb (floor-int (/ (+ -1 (.getHeight rectangle) (.y rectangle)) tile-height))})

(defn tile-in-tile-bounds?
  [[nx ny] bounds]
  (let [{:keys [nl nr nt nb]} bounds]
    (and (<= nl nx nr)
         (<= nt ny nb)))) 

; possibly delete?
(defn- tile-in-pixel-rectangle?
  [[nx ny] rectangle [tile-width tile-height]]
  (tile-in-tile-bounds? [nx ny]
                        (pixel-rectangle-to-tile-bounds
                          rectangle [tile-width tile-height])))

(defn tiles-in-pixel-rectangle
  "Returns a list of tile indices found in a given pixel rectangle."
  [rectangle [tile-width tile-height]]
  (let [nl (floor-int (/ (.x rectangle) tile-width))
        nr (floor-int (/ (+ -1 (.getWidth rectangle) (.x rectangle)) tile-width))
        nt (floor-int (/ (.y rectangle) tile-height))
        nb (floor-int (/ (+ -1 (.getHeight rectangle) (.y rectangle)) tile-height))]
    (for [nx (range nl (inc nr))
          ny (range nt (inc nb))]
      [nx ny])))

;; FINDING EXTENT OF TILES

(defn index-range [indices tag]
  (let [indices (map tag indices)]
   [(apply min indices)
    (apply max indices)]))

(defn tile-range [acquired-images]
  (let [tags [:nx :ny :nz]]
    (zipmap tags (map #(index-range acquired-images %) tags))))

(defn nav-range [acquired-images [tile-width tile-height]]
  (let [{:keys [nx ny nz]} (tile-range acquired-images)]
    {:z nz
     :x (when nx [(* tile-width (first nx)) (* tile-width (inc (second nx)))])
     :y (when ny [(* tile-height (first ny)) (* tile-height (inc (second ny)))])}))



        