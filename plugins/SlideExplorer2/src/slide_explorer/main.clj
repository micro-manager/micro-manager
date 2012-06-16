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
           (ij ImagePlus)
           (ij.process ImageProcessor)
           (mmcorej TaggedImage)
           (org.micromanager AcquisitionEngine2010 MMStudioMainFrame)
           (org.micromanager.utils GUIUpdater ImageUtils JavaUtils)
           (org.micromanager.acquisition TaggedImageQueue)
           (org.micromanager.MMStudioMainFrame))
  (:use [org.micromanager.mm :only (core edt mmc gui load-mm json-to-data)]
        [slide-explorer.affine :only (set-destination-origin transform inverse-transform)]
        [slide-explorer.view :only (show add-to-memory-tiles tile-in-pixel-rectangle?
                                    pixel-rectangle screen-rectangle tiles-in-pixel-rectangle
                                    evict-oldest)]
        [slide-explorer.image :only (show-image intensity-range lut-object)]
        [slide-explorer.tiles :only (floor-int center-tile tile-list offset-tiles)])
  (:require [slide-explorer.disk :as disk]
            [slide-explorer.reactive :as reactive]))

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
  (let [q (.run (AcquisitionEngine2010. gui) (.. gui getAcquisitionEngine getSequenceSettings) false)]
    (take-while #(not= % TaggedImageQueue/POISON)
                (repeatedly #(.take q)))))

(def acquire-tagged-image-sequence-memo
  (memoize acquire-tagged-image-sequence))

(defn acquire-processor-sequence []
  (map tagged-image-to-processor (acquire-tagged-image-sequence-memo)))

(defn acquire-at
  "Move the stage to position x,y and acquire a multi-dimensional
   sequence of images using the acquisition engine."
  ([x y]
    (acquire-at (Point2D$Double. x y)))
  ([^Point2D$Double stage-pos]
    (let [xy-stage (core getXYStageDevice)]
      (set-xy-position stage-pos)
      (core waitForDevice xy-stage)
      (acquire-processor-sequence))))
 
;; run using acquisitions

;;; channel setup

(defn initial-channel-display-settings [tagged-image-processors]
  (merge-with merge
              (into {}
                    (for [[chan color] (stack-colors tagged-image-processors)]
                      [chan {:color color}]))
              (into {}
                    (for [[chan images] (group-by #(get-in % [:tags "Channel"]) tagged-image-processors)]
                      [(or chan "Default") (assoc (apply intensity-range (map :proc images)) :gamma 1.0)]))))

(defn initial-lut-objects [tagged-image-processors]
  (into {}
        (for [[chan lut-map] (initial-channel-display-settings tagged-image-processors)]
          [chan {:lut (lut-object lut-map)}])))

;; tile arrangement

(defn next-tile [disk-tiles-index screen-state acquired-images [tile-width tile-height]]
  (let [center-tile (center-tile [(:x screen-state) (:y screen-state)]
                                 [tile-width tile-height])
        trajectory (offset-tiles center-tile tile-list)]
    (first (remove @acquired-images trajectory))))

;; tile acquisition management

(def image-processing-executor (Executors/newFixedThreadPool 1))

(defn add-tiles-at [memory-tiles [nx ny] affine-stage-to-pixel acquired-images]
  (doseq [image (time (doall (acquire-at (inverse-transform
                              (Point. (* 512 nx) (* 512 ny))
                              affine-stage-to-pixel))))]
    (let [indices {:nx nx
                   :ny ny
                   :nz (get-in image [:tags "SliceIndex"])
                   :nt 0
                   :nc (or (get-in image [:tags "Channel"]) "Default")}]
      ;(println indices @acquired-images)
      (swap! acquired-images conj [nx ny])
      (.submit image-processing-executor
               #(add-to-memory-tiles 
                  memory-tiles
                  indices
                  (image :proc))))))
    
(defn acquire-next-tile
  [memory-tiles-atom disk-tiles-index
   screen-state-atom acquired-images
   affine [tile-width tile-height]]
  (when-let [next-tile (next-tile @disk-tiles-index
                                  @screen-state-atom
                                  acquired-images
                                  [tile-width tile-height])]
    (when (tile-in-pixel-rectangle? next-tile (pixel-rectangle @screen-state-atom)
                                    [tile-width tile-height])
      (add-tiles-at memory-tiles-atom next-tile affine acquired-images)
      next-tile)))

(defn handle-error [e]
  (def q e))

(def explore-executor (Executors/newFixedThreadPool 1))

(defn explore [memory-tiles-atom disk-tiles-index screen-state-atom acquired-images
               affine [tile-width tile-height]]  
  (.submit explore-executor
           #(try
               (when (acquire-next-tile memory-tiles-atom
                                       disk-tiles-index
                                       screen-state-atom
                                       acquired-images
                                       affine
                                       [tile-width tile-height])
                  (explore memory-tiles-atom disk-tiles-index screen-state-atom
                         acquired-images affine [tile-width tile-height]))
              (catch Exception e (handle-error e)))))

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


(defn save-evicted
  [atom]
  (reactive/handle-removed-items
    atom
    (fn [[key val]]
      (when-not (@atom key)
        (disk/write-tile (disk/tile-dir atom) key val)))))
  
(defn map-subset?
  "Is map1 a \"subset\" of map2?"
  [map1 map2]
  (= map1 (select-keys map2 (keys map1))))

(defn visible-loader
  "Loads tiles needed for drawing; unloads the reset."
    [screen-state-atom memory-tile-atom disk-tile-index]
      (let [visible-tile-positions
            (set (map (fn [[nx ny]]
                        (hash-map
                          :nx nx :ny ny
                          :zoom (@screen-state-atom :zoom)
                          :nz (@screen-state-atom :z)))
                      (tiles-in-pixel-rectangle (screen-rectangle @screen-state-atom)
                                                [512 512])))
            relevant-keys (keys (first visible-tile-positions))]
        (doseq [tile @disk-tile-index]
          (if (visible-tile-positions (select-keys tile relevant-keys))
            (do 
              ;(println tile)
              (disk/load-tile memory-tile-atom tile))
            (do
              ;(println "no")
              ;(disk/unload-tile memory-tile-atom tile)
              )))))

(defn load-visible-only
  "Runs visible-loader whenever screen-state-atom changes."
  [screen-state-atom memory-tile-atom disk-tile-index]
  (let [react-fn (fn [_ _] (visible-loader screen-state-atom memory-tile-atom disk-tile-index))
        agent (agent {})]
    (def agent1 agent)
    (reactive/handle-update
      screen-state-atom
      react-fn
      agent)
    (reactive/handle-update
      disk-tile-index
      react-fn
      agent)))
      
(defn index-added-tiles
  "Record in disk-tile-index what images have been acquired."
  [memory-tiles-atom disk-tile-index]
  (reactive/handle-added-items memory-tiles-atom
                               (fn [[k v]]
                                 (swap! disk-tile-index conj k))))

(defn go
  "The main function that starts a slide explorer window."
  []
  (core waitForDevice (core getXYStageDevice))
  (let [dir (str "tmp" (rand-int 10000000))
        memory-tiles (doto (atom {}) (alter-meta! assoc ::directory dir))
        display-tiles (atom {})
        disk-tile-index (atom #{})
        acquired-images (atom #{})
        xy-stage (core getXYStageDevice)
        affine-stage-to-pixel (origin-here-stage-to-pixel-transform)
        first-seq (acquire-at (inverse-transform (Point. 0 0) affine-stage-to-pixel))
        screen-state (show memory-tiles)
        explore-fn #(explore memory-tiles disk-tile-index screen-state acquired-images
                             affine-stage-to-pixel [512 512])]
    (.mkdirs (java.io.File. dir))
    (def mt memory-tiles)
    (def affine affine-stage-to-pixel)
    (def ss screen-state)
    (def dti disk-tile-index)
    (def ai acquired-images)
    (swap! ss assoc :channels (initial-lut-objects first-seq))
    ;(evict-oldest memory-tiles 300 true)
    ;(save-evicted memory-tiles)
    ;(index-added-tiles memory-tiles disk-tile-index)
    ;(load-visible-only screen-state memory-tiles disk-tile-index)
    (explore-fn)
    (add-watch ss "explore" (fn [_ _ old new] (when-not (= old new)
                                                (explore-fn))))
    ))
  
;; tests

(defn get-tile [{:keys [nx ny nz nt nc]}]
  (ImageUtils/makeProcessor (grab-tagged-image)))

(defn start []
  (let [memory-tiles (atom {})
        display-tiles (agent {})
        xy-stage (core getXYStageDevice)]
    (def mt memory-tiles)
    (def ss (show memory-tiles))))

(def test-channels
  {"DAPI" {:lut (lut-object Color/BLUE  0 255 1.0)}
   "GFP"  {:lut (lut-object Color/GREEN 0 255 1.0)}
   "Cy5"  {:lut (lut-object Color/RED   0 255 1.0)}})

(defn test-start []
  (start)
  (swap! ss assoc :channels test-channels))

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


