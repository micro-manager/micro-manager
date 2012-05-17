(ns slide-explorer.image
  (:import (ij CompositeImage IJ ImagePlus ImageStack)
           (ij.io FileSaver)
           (ij.plugin ZProjector)
           (ij.process ByteProcessor LUT ImageProcessor
                       ImageStatistics ShortProcessor)
           (mmcorej TaggedImage)
           (javax.swing JFrame)
           (java.awt Color)
           (org.micromanager.utils ImageUtils))
  (:use [org.micromanager.mm :only (core load-mm gui)]))

(defmacro timer [expr]
  `(let [ret# (time ~expr)]
     (print '~expr)
     (println " -->" (pr-str ret#))
     ret#))

; Image Processing Needed:
; 1. Trim the images
; 2. Overlay color channels using a set of LUTs
; 3. Split RGB images into multiple colors
; 4. Maximum intensity projection
; 5. Flat-field correction
; 6. Find stitching vector
; 7. Merge and scale the images
; 8. Reading/writing images to disk

; ImageProcessor-related utilities

(defn insert-image
  "Inserts one ImageProcessor into another."
  [proc-host proc-guest x-host y-host]
  (when proc-guest
    (.insert proc-host proc-guest x-host y-host)))

(defn black-processor-like
  "Creates an empty (black) image processor."
  [original-processor]
  (.createProcessor original-processor
                    (.getWidth original-processor)
                    (.getHeight original-processor)))

(defn make-stack
  "Produces an ImageJ ImageStack from a collection
   of ImageProcessors."
  [processors]
  (let [proc1 (first (filter identity processors))]
    (when-not (nil? proc1)
      (let [w (.getWidth proc1)
            h (.getHeight proc1)
            stack (ImageStack. w h)]
        (doseq [processor processors]
          (.addSlice stack
            (if (nil? processor)
              (black-processor-like proc1)
              processor)))
        stack))))

(defn processor-to-image
  "Converts and ImageJ ImageProcessor to an AWT image."
  [^ImageProcessor proc]
  (.createImage proc))

;; Trimming and displacing images

; 0.466 ms 
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

; 5.9 ms
(defn merge-and-scale
  "Takes four ImageProcessors (tiles) and tiles them in a
   2x2 mosaic with no gaps, then scales pixels to half size."
  [img1 img2 img3 img4]
  (let [test-img (or img1 img2 img3 img4)
        w (.getWidth test-img)
        h (.getHeight test-img)
        large (.createProcessor test-img (* 2 w) (* 2 h))]
    (doto large
      (insert-image img1 0 0)
      (insert-image img2 w 0)
      (insert-image img3 0 h)
      (insert-image img4 w h)
      (.setInterpolationMethod ImageProcessor/BILINEAR))
    (.resize large w h)))

;; stats

; 0.335 ms
(defn statistics
  "Get basic intensity statistics for an image processor."
  [processor]
  {:min (.getMin processor)
   :max (.getMax processor)
   :histogram (.getHistogram processor)})
  

(defn intensity-range
  "Get the intensity range over a collection of processors."
  [processors]
  {:min (apply min (map #(.getMin %) processors))
   :max (apply max (map #(.getMax %) processors))})


;; Channels/LUTs

(defn lut-object
  "Creates an ImageJ LUT object with given parameters."
  ([^Color color ^double min ^double max ^double gamma]
  (let [lut (ImageUtils/makeLUT color gamma)]
    (set! (. lut min) min)
    (set! (. lut max) max)
    lut))
  ([{:keys [color min max gamma]}]
    (lut-object color min max gamma)))

(def black-lut
  "An LUT such that the image is not displayed."
  (lut-object Color/BLACK 0 255 1.0))

; 3.99 ms
(defn overlay
  "Takes n ImageProcessors and n lut objects and produces a BufferedImage
   containing the overlay."
  [processors luts]
  (when (first (filter identity processors))
    (let [luts (if (= 1 (count luts))
                 (list (first luts) black-lut)
                 luts)
          processors (if (= 1 (count processors))
                       (let [proc (first processors)]
                         (list proc (black-processor-like proc)))
                       processors)
          stack (make-stack processors)
          img+ (ImagePlus. "" stack)]
      (.setDimensions img+ (.getSize stack) 1 1)
      (.getImage
        (doto (CompositeImage. img+ CompositeImage/COMPOSITE)
          (.setLuts (into-array luts)))))))

(def overlay-memo (memoize overlay))

;; reading/writing ImageProcessors on disk

(defn write-processor [processor path]
  (io! (-> (ImagePlus. "" processor)
           FileSaver.
           (.saveAsTiff path)))
  processor)

(defn read-processor [path]
  (when-let [imgp (io! (IJ/openImage path))]
    (.getProcessor imgp)))

;; Maximum intensity projection

; 11 ms
(defn maximum-intensity-projection
  "Runs a maximum intensity projection across a collection of ImageProcessors,
   returning an ImageProcessor of the same type."
  [processors]
  (let [float-processor
        (->
          (doto
            (ZProjector. (ImagePlus. "" (make-stack processors)))
            .doProjection)
          .getProjection
          .getProcessor)]
    (condp = (type (first processors))
      ByteProcessor (.convertToByte float-processor false)
      ShortProcessor (.convertToShort float-processor false))))

;; testing
    
(defn show-image
  "Shows an AWT image or ImageProcessor in an ImageJ window."
  [img-or-proc]
  (.show (ImagePlus. "" img-or-proc))
  img-or-proc)

