(ns slide-explorer.image
  (:import (ij CompositeImage ImagePlus ImageStack)
           (ij.process ByteProcessor LUT ImageProcessor ShortProcessor)
           (mmcorej TaggedImage)
           (javax.swing JFrame)
           (java.awt Color)
           (org.micromanager.utils ImageUtils))
  (:use [org.micromanager.mm :only (core load-mm gui)]))

; Image Processing Needed:
; 1. Crop the images
; 2. Overlay color channels using a set of LUTs
; 3. Split RGB images into multiple colors
; 4. Maximum intensity projection
; 5. Flat-field correction
; 6. Find stitching vector

; (defn getPixels [^BufferedImage image] (-> image .getRaster .getDataBuffer .getData))

(defprotocol Croppable
  (crop "Crop an image with upper-left corner x,y and dimensions w,h." [this x y w h]))

(extend-type ByteProcessor
  Croppable
    (crop [this x y w h]
      (doto (ByteProcessor. w h)
        (.insert this (- x) (- y)))))

(extend-type ShortProcessor
  Croppable
    (crop [this x y w h]
      (doto (ShortProcessor. w h)
        (.insert this (- x) (- y)))))

(defn processor-to-image [^ImageProcessor proc]
  (.createImage proc))

(defn lut-object [^Color color ^double min max gamma]
  (let [lut (ImageUtils/makeLUT color gamma)]
    (set! (. lut min) min)
    (set! (. lut max) max)
    lut))

(defn make-stack
  "Produces an ImageJ ImageStack from a collection
   of ImageProcessors."
  [processors]
  (let [proc1 (first processors)
        w (.getWidth proc1)
        h (.getHeight proc1)
        stack (ImageStack. w h)]
    (doseq [processor processors]
      (.addSlice stack processor))
    stack))
        
(defn overlay ;; TODO: fix for when n=1
  "Takes n ImageProcessors and n lut objects and produces a BufferedImage
   that is the overlay."
  [processors luts]
  (let [stack (make-stack processors)
        img+ (ImagePlus. "" stack)]
    (.setDimensions img+ (.getSize stack) 1 1)
    (.getImage
      (doto (CompositeImage. img+ CompositeImage/COMPOSITE)
        (.setLuts (into-array luts))))))

;; test
    
(defn show [img-or-proc]
  (.show (ImagePlus. "" img-or-proc)))

