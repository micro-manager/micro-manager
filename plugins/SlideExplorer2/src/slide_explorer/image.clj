(ns slide-explorer.image
  (:use [org.micromanager.mm])
  (:import
    (mmcorej TaggedImage)
    (org.micromanager.utils ImageUtils MDUtils)
    (ij ImagePlus IJ)))

(load-mm)

(defn main-window []
  (ImagePlus. "Slide Explorer II"))

(defn awt-image [^TaggedImage tagged-image]
  (.getImage (ImagePlus. "" (ImageUtils/makeProcessor tagged-image))))



(defn tile [t x y z c]
  )