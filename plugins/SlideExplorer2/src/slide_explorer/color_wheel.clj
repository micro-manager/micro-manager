(ns slide-explorer.color-wheel
  (:import (java.awt Color)
           (java.awt.image BufferedImage))
  (:require [slide-explorer.canvas :as canvas]
            [slide-explorer.image :as image]
            [clojure.java.io :as io]))

(def PI_2 (* 2 Math/PI))

(defn coords-to-hsb [x y]
  (let [radius (Math/sqrt
                 (+ (Math/pow x 2)
                    (Math/pow y 2)))]
    (cond (= 0 radius) {:hue 0 :saturation 0 :brightness 1.0}
          (<= radius 1) {:hue (+ 0.5 (/ (Math/atan2 x y) PI_2))
                         :saturation radius
                         :brightness 1.0})))

(defn hsb-to-color
  "Convert an hsb map to a java awt Color object."
  [{:keys [hue saturation brightness]}]
  (when (and hue saturation brightness)
    (Color/getHSBColor hue saturation brightness)))
  

(defn wheel-description
  "Canvas-style description of wheel."
  [radius]
  (let [ranges (range (- radius) (inc radius))]
    (filter identity
            (for [x ranges
                  y ranges]
              (let [color (hsb-to-color
                            (coords-to-hsb
                              (- (/ x radius)) 
                              (- (/ y radius))))]
                (when color
                  [:line {:x x :y y :w 0 :h 0
                           :color color}]))))))

(defn wheel-image
  "Create a wheel image (slow!)."
  [radius]
  (let [side (* 2 radius)
        image (BufferedImage. side side BufferedImage/TYPE_INT_ARGB)
        graphics (.createGraphics image)]
    (canvas/draw
      graphics
      [:graphics {}
       (concat [:compound {:x radius :y radius :w side :h side}]
               (wheel-description radius))])
    image))

(defn generate-wheel-image
  "Generate the wheel image and write it to a file."
  [radius file]
  (image/write-image file (wheel-image radius)))

(comment
  (generate-wheel-image 128 "hue-saturation-wheel.png")
  )
    

;; tests

(defn hue-range []
  (for [hue (range 0 1 0.001)]
    (hsb-to-color {:hue hue :saturation 1.0 :brightness 1.0})))

(def canvas-atom (atom nil))

(defn wheel-test []
  (canvas/canvas-frame canvas-atom))
