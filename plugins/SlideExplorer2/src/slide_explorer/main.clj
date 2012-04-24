(ns slide-explorer.main
  (:import (mmcorej CMMCore TaggedImage)
           (java.util.prefs Preferences)
           (org.micromanager MMStudioMainFrame)
           (org.micromanager.utils JavaUtils MDUtils))
  (:use [org.micromanager.mm]))

(load-mm)


(defn get-stage-to-pixel-transform []
  (let [prefs (Preferences/userNodeForPackage MMStudioMainFrame)]
    (JavaUtils/getObjectFromPrefs
      prefs (str "affine_transform_" (core getCurrentPixelSizeConfig)) nil)))

(defn grab-tagged-image
  "Grab a single image from camera."
  []
  (core snapImage)
  (core getTaggedImage))

(def pixel-size (core getPixelSizeUm true))

(defn get-image-width [^TaggedImage image]
  (MDUtils/getWidth (.tags image)))
  
(defn get-image-height [^TaggedImage image]
  (MDUtils/getWidth (.tags image)))

(defn tile-dimensions [^TaggedImage image]
  (let [w (get-image-width image)
        h (get-image-height image)]
    [(long (* 3/4 w)) (long (* 3/4 h))]))

