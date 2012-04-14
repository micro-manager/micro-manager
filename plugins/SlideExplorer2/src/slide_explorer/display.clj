(ns slide_explorer.display
  (:import (javax.swing JFrame)
           (java.awt Canvas Graphics Graphics2D)
           (java.awt.image BufferedImage WritableRaster)
           (java.awt.geom AffineTransform)
           mmcorej.TaggedImage
           (org.micromanager.utils MDUtils))
  (:use [org.micromanager.mm :only (json-to-data)]))


(defn image-data [^TaggedImage tagged-image]
  {:pixels (.pix tagged-image)
   :tags (json-to-data (.tags tagged-image))})

(defn get-image-dimensions [^TaggedImage tagged-image]
  [(MDUtils/getWidth (.tags tagged-image))
   (MDUtils/getHeight (.tags tagged-image))])

(defn buffered-image
  ([[w h] pixels]
  (let [image (BufferedImage. w h BufferedImage/TYPE_BYTE_INDEXED)
        raster (cast WritableRaster (.getData image))]
    (.setPixels raster 0 0 w h pixels)
    image))
  ([^TaggedImage tagged-image]
    (buffered-image (get-image-dimensions tagged-image) (.pix tagged-image))))
       
(defn main-canvas
  (proxy [Canvas] []
    (paint [^Graphics g]
           (.drawImage (cast Graphics2D g) (AffineTransform.) nil))))
