(ns slide-explorer.main
  (:import (java.awt Color Graphics Graphics2D Point RenderingHints Window)
           (java.awt.event ComponentAdapter KeyAdapter KeyEvent MouseAdapter
                           WindowAdapter)
           (java.awt.image BufferedImage WritableRaster)
           (java.awt.geom AffineTransform Point2D$Double)
           (java.io ByteArrayInputStream)
           (java.util UUID)
           (java.util.prefs Preferences)
           (java.util.concurrent Executors)
           (javax.imageio ImageIO)
           (javax.swing JFileChooser)
           (ij ImagePlus)
           (ij.process ImageProcessor)
           (mmcorej TaggedImage)
           (org.micromanager AcquisitionEngine2010 MMStudioMainFrame)
           (org.micromanager.utils GUIUpdater ImageUtils JavaUtils)
           (org.micromanager.acquisition TaggedImageQueue)
           (org.micromanager.MMStudioMainFrame))
  (:use [org.micromanager.mm :only (core edt mmc gui load-mm json-to-data)]
        [slide-explorer.affine :only (set-destination-origin transform inverse-transform)]
        [slide-explorer.view :only (show add-to-memory-tiles pixel-rectangle)]
        [slide-explorer.image :only (show-image intensity-range lut-object)]
        [slide-explorer.persist :only (save-as)]
        [clojure.java.io :only (file)])
  (:require [slide-explorer.reactive :as reactive]
            [slide-explorer.tile-cache :as tile-cache]
            [slide-explorer.tiles :as tiles]
            [slide-explorer.persist :as persist]))

(load-mm (MMStudioMainFrame/getInstance))

;; affine transforms

(def gui-prefs (Preferences/userNodeForPackage MMStudioMainFrame))

(defn set-stage-to-pixel-transform [^AffineTransform affine-transform]
  (JavaUtils/putObjectInPrefs
    gui-prefs (str "affine_transform_" (core getCurrentPixelSizeConfig))
    (.createInverse affine-transform)))

(defn get-stage-to-pixel-transform []
  (when-let [transform
             (JavaUtils/getObjectFromPrefs
               gui-prefs (str "affine_transform_" (core getCurrentPixelSizeConfig))
               nil)]
    (.createInverse transform)))

(defn pixels-to-stage [^AffineTransform pixel-to-stage-transform [x y]]
  (let [p (.transform pixel-to-stage-transform (Point2D$Double. x y) nil)]
    [(.x p) (.y p)]))
    
(defn stage-to-pixels [^AffineTransform pixel-to-stage-transform [x y]]
  (let [p (.inverseTransform pixel-to-stage-transform (Point2D$Double. x y) nil)]
    [(.x p) (.y p)]))      

(defn origin-here-stage-to-pixel-transform
  "Set the current location to the origin of the 
   stage to pixel transform."
  []
  (set-destination-origin
    (get-stage-to-pixel-transform)
    (.getXYStagePosition gui)))

;; tagged image stuff

(defn stack-colors
  "Gets the channel colors from a tagged-processor-sequence."
  [tagged-processor-sequence]
  (let [summary (-> tagged-processor-sequence first :tags (get "Summary"))]
    (zipmap (summary "ChNames") (map #(Color. %) (summary "ChColors")))))

(defn tagged-image-to-processor [tagged-image]
  {:proc (ImageUtils/makeProcessor tagged-image)
   :tags (json-to-data (.tags tagged-image))})

;; stage communications

(defn get-xy-position []
  (core waitForDevice (core getXYStageDevice))
  (.getXYStagePosition gui))

(defn set-xy-position [^Point2D$Double position]
  (core waitForDevice (core getXYStageDevice))
  (core setXYPosition (core getXYStageDevice) (.x position) (.y position)))

;; image acquisition

(def grab-tagged-image
  "Grabs a single image from camera."
    (fn []
      (core snapImage)
      (core getTaggedImage)))

(defn acquire-tagged-image-sequence []
  (let [engine (AcquisitionEngine2010. gui)
        settings (.. gui getAcquisitionEngine getSequenceSettings)
        q (.run engine settings false)]
    (take-while #(not= % TaggedImageQueue/POISON)
                (repeatedly #(.take q)))))

(defn acquire-processor-sequence []
  (map tagged-image-to-processor (acquire-tagged-image-sequence)))

(def acquire-processor-sequence* (memoize acquire-processor-sequence))

(defn acquire-at
  "Move the stage to position x,y and acquire a multi-dimensional
   sequence of images using the acquisition engine."
  ([x y]
    (acquire-at (Point2D$Double. x y)))
  ([^Point2D$Double stage-pos]
    (let [xy-stage (core getXYStageDevice)]
      (set-xy-position stage-pos)
      (core waitForDevice xy-stage)
      (acquire-processor-sequence)
      )))

;; run using acquisitions

;;; channel display settings

(defn initial-lut-maps [tagged-image-processors]
  (merge-with merge
              (into {}
                    (for [[chan color] (stack-colors tagged-image-processors)]
                      [chan {:color color}]))
              (into {}
                    (for [[chan images] (group-by #(get-in % [:tags "Channel"]) tagged-image-processors)]
                      [(or chan "Default") (assoc (apply intensity-range (map :proc images)) :gamma 1.0)]))))

;; tile arrangement

(defn next-tile [screen-state acquired-images]
  (let [tile-dimensions (screen-state :tile-dimensions)
        center (tiles/center-tile [(:x screen-state) (:y screen-state)]
                                 tile-dimensions)
        pixel-rect (pixel-rectangle screen-state)
        bounds (tiles/pixel-rectangle-to-tile-bounds pixel-rect tile-dimensions)
        number-tiles (tiles/number-of-tiles screen-state tile-dimensions)]
    (->> tiles/tile-list
         (tiles/offset-tiles center)
         (take number-tiles)
         (filter #(tiles/tile-in-tile-bounds? % bounds))
         (remove @acquired-images)
         first)))

;; tile acquisition management

(def image-processing-executor (Executors/newFixedThreadPool 1))
 
(defn add-tiles-at [memory-tiles [nx ny] affine-stage-to-pixel acquired-images tile-dimensions]
  (swap! acquired-images conj [nx ny])
  (let [[tile-width tile-height] tile-dimensions]
    (doseq [image (doall (acquire-at (inverse-transform
                                       (Point. (* tile-width nx)
                                               (* tile-height ny))
                                       affine-stage-to-pixel)))]
      (let [indices {:nx nx
                     :ny ny
                     :nz (get-in image [:tags "SliceIndex"])
                     :nt 0
                     :nc (or (get-in image [:tags "Channel"]) "Default")}]
        (add-to-memory-tiles 
          memory-tiles
          indices
          (image :proc))))))
    
(defn acquire-next-tile
  [memory-tiles-atom
   screen-state-atom acquired-images
   affine]
  (when-let [next-tile (next-tile @screen-state-atom
                                  acquired-images)]
    (add-tiles-at memory-tiles-atom next-tile affine acquired-images
                  (@screen-state-atom :tile-dimensions))
    next-tile))

(def explore-executor (Executors/newFixedThreadPool 1))

(defn explore [memory-tiles-atom screen-state-atom acquired-images
               affine]
  ;(println "explore")
  (reactive/submit explore-executor
                   #(when (acquire-next-tile memory-tiles-atom
                                             screen-state-atom
                                             acquired-images
                                             affine)
                      (explore memory-tiles-atom screen-state-atom
                           acquired-images affine))))
                      

;; SAVE AND LOAD SETTINGS


(defn save-settings [dir screen-state]
  (spit (file dir "metadata.txt")
        (pr-str (select-keys screen-state [:channels :tile-dimensions]))))

(defn load-settings [dir]
  (read-string (slurp (file dir "metadata.txt"))))

; Overall scheme
; the GUI is generally reactive.
; vars:
; memory-tiles (indices -> pixels)
; disk-tile-index (set of tile indices)
; display-tiles (indices -> pixels)
; view-state
;
; Whenever an image is acquired, it is processed, mipmapped and each
; resulting tiles are added to memory-tiles. Tiles
; added are automatically asynchronously saved to disk, and the indices
; are added to disk-tiles.
; memory-tiles and overlay-tiles are limited to 100 images each,
; using an LRU eviction policy.
; When view-state viewing area is adjusted, tiles needed for the
; new viewing area are loaded back into memory-tiles. If we are
; in explore mode, then images not available in memory-tiles or
; disk tiles are acquired according to the spiral trajectory.
; Overlay tiles are generated whenever memory tiles are added
; or the contrast is changed.
; The view redraws tiles inside viewing area whenever view-state
; has been adjusted or a new image appears in overlay-tiles.

(defn go
  "The main function that starts a slide explorer window."
  ([dir new?]
    (let [settings (if-not new? (load-settings dir) {:tile-dimensions [512 512]})
          acquired-images (atom #{})
          [screen-state memory-tiles] (show dir acquired-images settings)]
      (when new?
        (core waitForDevice (core getXYStageDevice))
        (let [affine-stage-to-pixel (origin-here-stage-to-pixel-transform)
              first-seq (acquire-at (inverse-transform (Point. 0 0) affine-stage-to-pixel))
              explore-fn #(explore memory-tiles screen-state acquired-images
                                   affine-stage-to-pixel)]
          (.mkdirs dir)
          (def mt memory-tiles)
          (def affine affine-stage-to-pixel)
          (println "about to get channel luts")
          (swap! screen-state merge
                 {:channels (initial-lut-maps first-seq)
                  :tile-dimensions [(core getImageWidth)
                                    (core getImageHeight)]})
          (when new?
            (explore-fn)
            (add-watch screen-state "explore" (fn [_ _ old new] (when-not (= old new)
                                                                  (explore-fn)))))))
      (def ss screen-state)
      (def ai acquired-images)))
  ([]
    (go (file (str "tmp" (rand-int 10000000))) true)))
  

(defn load-data-set
  []
  (when-let [dir (persist/open-dir-dialog)]
    (go (file dir) false)))

;; tests

(defn get-tile [{:keys [nx ny nz nt nc]}]
  (ImageUtils/makeProcessor (grab-tagged-image)))

(def test-channels
  {"DAPI" {:lut (lut-object Color/BLUE  0 255 1.0)}
   "GFP"  {:lut (lut-object Color/GREEN 0 255 1.0)}
   "Cy5"  {:lut (lut-object Color/RED   0 255 1.0)}})

(defn test-tile [nx ny nz nc]
  (add-to-memory-tiles mt {:nx nx
                              :ny ny
                              :nz nz
                              :nt 0
                              :nc nc}
                          (get-tile nil)))

(defn test-tiles
  ([n] (test-tiles n n 0 0))
  ([nx ny nz]
    (core setExposure 100)
    (.start (Thread.
              #(doseq [i (range (- nx) (inc nx)) j (range (- ny) (inc ny))
                       k (range (- nz) (inc nz))
                       chan (keys (@ss :channels))]
                 ;(Thread/sleep 1000)
                 (test-tile i j k chan))))))
