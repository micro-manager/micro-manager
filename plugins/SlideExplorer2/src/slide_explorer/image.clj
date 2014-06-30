(ns slide-explorer.image
  (:import (ij CompositeImage IJ ImagePlus ImageStack)
           (ij.io FileSaver)
           (ij.plugin ImageCalculator ZProjector)
           (ij.plugin.filter GaussianBlur)
           (ij.process ByteProcessor LUT ImageProcessor ColorProcessor
                       ImageStatistics ByteStatistics ShortProcessor
                       FloatProcessor)
           (mmcorej TaggedImage)
           (javax.swing JFrame)
           (java.awt Color)
           (java.io File)
           (javax.imageio ImageIO)
           (org.micromanager.utils ImageUtils))
  (:require [slide-explorer.canvas :as canvas]
            [clojure.java.io :as io]))

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

(defn clone
  "Duplicates and ImageProcessor."
  [processor]
  (when processor
    (.duplicate processor)))

(defn insert-image!
  "Inserts one ImageProcessor into another."
  [proc-host proc-guest x-host y-host]
  (when (and proc-guest proc-host)
    (doto proc-host
      (.insert proc-guest x-host y-host))))

(defn half-size
  [proc]
  (when proc
    (.resize proc
             (/ (.getWidth proc) 2)
             (/ (.getHeight proc) 2))))

(defn random-byte-image
  "Produces an ByteProcessor with random values."
  [width height]
  (let [pixels (byte-array
                 (map byte (repeatedly (* width height)
                                       #(- (rand-int 256) 128))))]
    (doto (ByteProcessor. width height)
      (.setPixels pixels))))

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

(defn convert-to-type
  "Converts a processor to a type
   as :byte, :short or :float"
  [proc type]
  (condp = type
    :byte (.convertToByte proc false)
    :short (.convertToShort proc false)
    :float (.convertToFloat proc))) ; note different signature!

(defn get-type
  "Returns :float, :byte, or :short."
  [proc]
  ({ByteProcessor :byte
    ShortProcessor :short
    FloatProcessor :float}
                   (type proc)))

(defn convert-to-type-like
  "Converts proc to a processor of the same type
   as template-proc."
  [proc template-proc]
  (convert-to-type proc (get-type template-proc)))

;; Trimming and displacing images

; 0.466 ms 
(defn crop
  "Crops an image, with upper-left corner at position x,y
   and width and height w,h."
  [^ImageProcessor original x y w h]
  (doto (.createProcessor original w h)
    (insert-image! original (- x) (- y))))

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
          (- (int (/ overlap-x 2)) dx)
          (- (int (/ overlap-y 2)) dy)
          (- (.getWidth raw-processor) overlap-x)
          (- (.getHeight raw-processor) overlap-y))))

;; Merge and scale the images for Mipmap

; 5.9 ms

(defn insert-half-tile!
  "Creates a new ImageProcessor with the old
   ImageProcessor, tile, inserted into a dest."
  [dest tile [left? upper?]]
  (let [w (.getWidth dest)
        h (.getHeight dest)
        x (if left? 0 (/ w 2))
        y (if upper? 0 (/ h 2))]
    (doto dest
      (.setInterpolationMethod ImageProcessor/BILINEAR)
      (insert-image! (half-size tile) x y))))

(defn insert-half-tile
  [tile [left? upper?] mosaic]
  (let [dest (or (clone mosaic) (black-processor-like tile))]
    (insert-half-tile! dest tile [left? upper?])))

;; stats
 
(defn intensity-range
  "Get the intensity range for one or more processors."
  ([processor]
      (let [stat (ImageStatistics/getStatistics
                   processor ImageStatistics/MIN_MAX nil)]
        {:min (.min stat)
         :max (.max stat)}))
  ([processor & processors]
    (let [min-maxes (map intensity-range (cons processor processors))]
      {:min (apply min (map :min min-maxes))
       :max (apply max (map :max min-maxes))})))

(defn intensity-distribution
  "Get the intensity distribution for a processor,
   suitable for a histogram."
  [processor]
  (let [stat (ImageStatistics/getStatistics
               processor ImageStatistics/MIN_MAX nil)]
    {:data (.getHistogram stat)
     :min (.histMin stat)
     :max (.histMax stat)}))
    

;; Channels/LUTs

(defn lut-object
  "Creates an ImageJ LUT object with given parameters."
  ([color ^double min ^double max ^double gamma]
    (let [lut (ImageUtils/makeLUT (canvas/color-object color) gamma)]
      (set! (. lut min) min)
      (set! (. lut max) max)
      lut))
  ([{:keys [color min max gamma]}]
    (lut-object color min max gamma))
  ([color min max]
    (lut-object color min max 1.0)))

(def black-lut
  "An LUT such that the image is not displayed."
  {:color Color/BLACK :min 0 :max 255 :gamma 1.0})

(defn composite-image
  "Takes n ImageProcessors and produces a CompositeImage."
  [processors]
  (when (first (filter identity processors))
    (let [processors (if (= 1 (count processors))
                       (let [proc (first processors)]
                         (list proc (black-processor-like proc)))
                       processors)
          stack (make-stack processors)
          img+ (ImagePlus. "" stack)]
      (.setDimensions img+ (.getSize stack) 1 1)
      (CompositeImage. img+ CompositeImage/COMPOSITE))))
                    
; ~4 ms
(defn overlay
  "Takes n ImageProcessors and n lut-maps [:color :min :max :gamma] and
   produces a BufferedImage containing the overlay."
  [processors lut-maps]
  (when (first (filter identity processors))
    (let [luts (map lut-object
                    (if (= 1 (count lut-maps))
                      (list (first lut-maps) black-lut)
                      lut-maps))]
      (.getImage
        (doto (composite-image processors)
          (.setLuts (into-array luts)))))))

;; reading/writing ImageProcessors/Images on disk

(defn write-processor
  "Writes an image processor to disk, in TIFF format, at path."
  [path processor]
  (io! (-> (ImagePlus. "" processor)
           FileSaver.
           (.saveAsTiff path)))
  processor)

(defn read-processor
  "Reads a TIFF file found at path into an image processor."
  [path]
  (let [full-path (.getAbsolutePath (File. path))]
    (when (.exists (File. full-path))
      (when-let [imgp (io! (IJ/openImage full-path))]
        (.getProcessor imgp)))))       

(defn read-image
  "Reads an image file to an AWT image."
  [file]
  (let [file (io/file file)]
    (ImageIO/read file)))

(defn write-image
  "Writes an AWT Image object to a file."
  [file image]
  (let [file (io/file file)
        suffix (last (.split (.getAbsolutePath file) "\\."))]
    (ImageIO/write image suffix file)))

;; Intensity projection

(def projection-methods
  {:average ZProjector/AVG_METHOD
   :mean ZProjector/AVG_METHOD
   :max ZProjector/MAX_METHOD
   :min ZProjector/MIN_METHOD
   :sum ZProjector/SUM_METHOD
   :standard-deviation ZProjector/SD_METHOD
   :median ZProjector/MEDIAN_METHOD})

(defn intensity-projection
  "Runs an intensity projection across a collection of ImageProcessors,
   returning an ImageProcessor of the same type. Methods
   are :average, :max, :min, :sum, :standard-deviation, :median."
  [method processors]
  (->
    (doto
      (ZProjector. (ImagePlus. "" (make-stack processors)))
      (.setMethod (projection-methods method))
      .doProjection)
    .getProjection
    .getProcessor
    (convert-to-type-like (first processors))))

(defn gaussian-blur
  "Applys a gaussian blur to ImageProcessor, with given radius."
  [processor radius]
  (let [new-proc (clone processor)]
    (.blurGaussian (GaussianBlur.) new-proc radius radius 0.0002)
    new-proc))

(defn divide
  [processor value]
  (doto (.convertToFloat processor)
    (.multiply (/ value))))

(defn normalize-to-max
  "Rescale intensities so the max value of processor is 1.0."
  [processor]
  (divide processor (.max (.getStatistics processor))))

(defn combine-processors
  "Arithmetically combine processor 1 with processor 2, where op
   is :add, :subtract, :multiply, :divide."
  [op proc1 proc2]
  (-> (ImageCalculator.)
      (.run 
        (str (name op) " float")
        (ImagePlus. "" proc1)
        (ImagePlus. "" proc2))
      .getProcessor))

(defn multiply
  "Multiply an image processor by a factor."
  [processor factor]
  (doto (clone processor)
    (.multiply factor)))

(def add-processors
  "Arithmetically add two processors, pixel by pixel."
  (partial combine-processors :add))

(defn pixel
  "Returns the intensity value of a pixel at x,y."
  [proc x y]
  (.getPixelValue proc x y)) 

(defn center-pixel
  "Returns the intensity value of a pixel at the center of
   the image."
  [proc]
  (let [x (long (/ (.getWidth proc) 2))
        y (long (/ (.getHeight proc) 2))]
    (pixel proc x y)))    


;; testing
    
(defn show
  "Shows an AWT image or ImageProcessor in an ImageJ window."
  [img-or-proc]
  (.show (ImagePlus. "" img-or-proc))
  img-or-proc)



