(ns slide-explorer.image
  (:import (ij CompositeImage ImagePlus ImageStack)
           (ij.process ByteProcessor LUT ImageProcessor ShortProcessor)
           (mmcorej TaggedImage)
           (javax.swing JFrame)
           (java.awt Color)
           (org.micromanager.utils ImageUtils))
  (:use [org.micromanager.mm :only (core load-mm gui)]))

; Image Processing Needed:
; 1. Trim the images
; 2. Overlay color channels using a set of LUTs
; 3. Split RGB images into multiple colors
; 4. Maximum intensity projection
; 5. Flat-field correction
; 6. Find stitching vector
; 7. Merge and scale the images

; ImageProcessor-related utilities

(defn insert-image
  "Insert one ImageProcessor into another."
  [proc-host proc-guest x-host y-host]
  (when proc-guest
    (.insert proc-host proc-guest x-host y-host)))

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

(defn processor-to-image
  "Converts and ImageJ ImageProcessor to an AWT image."
  [^ImageProcessor proc]
  (.createImage proc))

;; Trimming and displacing images

(defn crop
  "Crops an image, with upper-left corner at position x,y
   and width and height w,h."
  [^ImageProcessor original x y w h]
  (doto (.createProcessor original w h)
    (insert-image original (- x) (- y))))

(defn raw-to-tile
  "Takes a raw ImageProcessor and trims edges by amount overlap,
   offseting the trim to take into account the difference
   between desired and found positions."
  [raw-processor
   [overlap-x overlap-y]
   [desired-x desired-y]
   [found-x found-y]]
  (let [dx (- found-x desired-x)
        dy (- found-y desired-y)]
    (crop raw-processor
          (- (/ overlap-x 2) dx)
          (- (/ overlap-y 2) dy)
          (- (.getWidth raw-processor) overlap-x)
          (- (.getHeight raw-processor) overlap-y))))

;; Merge and scale the images for Mipmap

(defn merge-and-scale
  "Takes four ImageProcessors (tiles) and tiles them in a
   2x2 mosaic with no gaps, then scales pixels to half size."
  [img1 img2 img3 img4]
  (let [w (.getWidth img1)
        h (.getHeight img1)
        large (.createProcessor img1 (* 2 w) (* 2 h))]
    (doto large
      (insert-image img1 0 0)
      (insert-image img2 w 0)
      (insert-image img3 0 h)
      (insert-image img4 w h)
      (.setInterpolationMethod ImageProcessor/BILINEAR))
    (.resize large w h)))

;; Channels/LUTs

(defn lut-object
  "Creates an ImageJ LUT object with given parameters."
  [^Color color ^double min ^double max ^double gamma]
  (let [lut (ImageUtils/makeLUT color gamma)]
    (set! (. lut min) min)
    (set! (. lut max) max)
    lut))

(def black-lut
  "An LUT such that the image is not displayed."
  (lut-object Color/BLACK 0 255 1.0))

(defn overlay
  "Takes n ImageProcessors and n lut objects and produces a BufferedImage
   containing the overlay."
  [processors luts]
  (let [luts (if (= 1 (count luts))
               (list (first luts) black-lut)
               luts)
        processors (if (= 1 (count processors))
                     (let [proc (first processors)]
                       (list proc (.createProcessor proc
                                    (.getWidth proc) (.getHeight proc))))
                     processors)
        stack (make-stack processors)
        img+ (ImagePlus. "" stack)]
    (.setDimensions img+ (.getSize stack) 1 1)
    (.getImage
      (doto (CompositeImage. img+ CompositeImage/COMPOSITE)
        (.setLuts (into-array luts))))))


;; testing
    
(defn show
  "Shows an AWT or ImageProcessor in an ImageJ window."
  [img-or-proc]
  (.show (ImagePlus. "" img-or-proc))
  img-or-proc)

