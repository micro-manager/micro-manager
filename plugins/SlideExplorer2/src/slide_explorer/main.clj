(ns slide-explorer.main
  (:import (java.awt Color Point)
           (java.awt.geom AffineTransform Point2D$Double)
           (java.util.prefs Preferences)
           (java.util.concurrent Executors)
           (javax.swing JOptionPane)
           (org.micromanager AcquisitionEngine2010 MMStudio)
           (org.micromanager.utils ImageUtils JavaUtils)
           (org.micromanager.acquisition TaggedImageQueue)
           (org.micromanager.pixelcalibrator.PixelCalibratorPlugin))
  (:require [clojure.java.io :as io]
            [clojure.set]
            [org.micromanager.mm :as mm]
            [org.micromanager.acq-engine :as engine]
            [slide-explorer.reactive :as reactive]
            [slide-explorer.view :as view]
            [slide-explorer.user-controls :as user-controls]
            [slide-explorer.affine :as affine]
            [slide-explorer.image :as image]
            [slide-explorer.tile-cache :as tile-cache]
            [slide-explorer.tiles :as tiles]
            [slide-explorer.persist :as persist]
            [slide-explorer.disk :as disk]
            [slide-explorer.positions :as positions]
            [slide-explorer.store :as store]
            [slide-explorer.utils :as utils]
            [slide-explorer.reactive :as reactive]))


(def MIN-ZOOM 1/256)

(def MAX-ZOOM 1)

;; affine transforms

(defonce positions-atom (atom []))

(def gui-prefs (Preferences/userNodeForPackage MMStudio))

(def current-xy-positions (atom {}))

(def current-z-positions (atom {}))

; prevents sudden navigate request from
; causing images to be acquired in the 
; wrong place.
(def anti-blur (Object.)) 

(defn match-set
  "Retains only those items in set where applying key-fn returns val."
  [key-fn val set]
  (clojure.set/select #(= (key-fn %) val) set))

(defn set-stage-to-pixel-transform [^AffineTransform affine-transform]
  (JavaUtils/putObjectInPrefs
    gui-prefs (str "affine_transform_" (mm/core getCurrentPixelSizeConfig))
    (.createInverse affine-transform)))

(defn get-stage-to-pixel-transform []
  (when-let [transform
             (JavaUtils/getObjectFromPrefs
               gui-prefs (str "affine_transform_" (mm/core getCurrentPixelSizeConfig))
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
  (when-let [transform (get-stage-to-pixel-transform)]
    (affine/set-destination-origin transform (.getXYStagePosition mm/gui))))

(defn pixel-size-um
  "Compute the pixel size from a stage-to-pixel transform."
  [stage-to-pixel-transform]
  (Math/pow (Math/abs (.getDeterminant stage-to-pixel-transform)) -1/2))

;; tagged image stuff

(defn stack-colors
  "Gets the channel colors from a tagged-processor-sequence."
  [tagged-processor-sequence]
  (let [summary (-> tagged-processor-sequence first :tags (get "Summary"))]
    (zipmap (summary "ChNames") (summary "ChColors"))))

(defn tagged-image-to-processor [tagged-image]
  {:proc (ImageUtils/makeProcessor tagged-image)
   :tags (mm/json-to-data (.tags tagged-image))})

;; stage communications

(defn non-empty [x]
  (when-not (empty? x) x))

(defn focus-device []
  (non-empty (mm/core getFocusDevice)))

(defn get-xy-position []
  (mm/core waitForDevice (mm/core getXYStageDevice))
  (.getXYStagePosition mm/gui))

(defn set-xy-position
  ([^Point2D$Double position]
    (set-xy-position (.x position) (.y position)))
  ([x y]
    (let [stage (mm/core getXYStageDevice)]
      (mm/core setXYPosition stage x y)
      (mm/core waitForDevice stage)
      (swap! current-xy-positions assoc stage [x y]))))

(defn set-z-position
  [z]
  (when-let [stage (focus-device)]
    (when (not= z (@current-z-positions stage))
      (mm/core setPosition stage z)
      (mm/core waitForDevice stage))
    (swap! current-z-positions assoc stage z)))

;; image acquisition

(def grab-tagged-image
  "Grabs a single image from camera."
    (fn []
      (mm/core snapImage)
      (mm/core getTaggedImage)))

(defn acquire-tagged-image-sequence [settings]
  (let [acq-engine (AcquisitionEngine2010. mm/gui)
        q (engine/run acq-engine settings false nil nil)]
    (take-while #(not= % TaggedImageQueue/POISON)
                (repeatedly #(.take q)))))

(defn acquire-processor-sequence [settings]
  (map tagged-image-to-processor (acquire-tagged-image-sequence settings)))

(def acquire-processor-sequence* (memoize acquire-processor-sequence))

(defn acquire-at
  "Move the stage to position x,y and acquire a multi-dimensional
   sequence of images using the acquisition engine."
  ([x y z settings]
    (acquire-at (Point2D$Double. x y) z settings))
  ([^Point2D$Double stage-pos z-pos settings]
    (locking anti-blur
             (set-xy-position stage-pos)
             (set-z-position z-pos)
             (acquire-processor-sequence settings))))

;; run using acquisitions

;;; channel display settings

(defn apply-to-map-values [f m]
  (into {}
        (for [[k v] m] [k (f v)])))

(defn channel-info [tagged-images-in-a-channel]
  (apply image/intensity-range (map :proc tagged-images-in-a-channel)))  

(defn initial-lut-maps [tagged-image-processors]
  (let [colors (stack-colors tagged-image-processors)
        image-map (group-by #(get-in % [:tags "Channel"]) tagged-image-processors)]
    (into {}
          (for [[name images] image-map]
            [name (merge {:color (colors name -1) :gamma 1.0}
                         (channel-info images))]))))

;; tile arrangement

(defn as-map [[x y] z]
  {:nx x :ny y :nz z})

(defn next-tile [{:keys [tile-dimensions x y z] :as screen-state} acquired-images]
  (let [center (tiles/center-tile [x y] tile-dimensions)
        pixel-rect (view/pixel-rectangle screen-state)
        bounds (tiles/pixel-rectangle-to-tile-bounds pixel-rect tile-dimensions)
        number-tiles (tiles/number-of-tiles screen-state tile-dimensions)
        acquired-this-slice (match-set :nz z @acquired-images)]
    (->> tiles/tile-list
         (tiles/offset-tiles center)
         (take number-tiles)
         (filter #(tiles/tile-in-tile-bounds? % bounds))
         (map #(as-map % z))
         (remove acquired-this-slice)
         first)))

;; tile acquisition management

(def image-processing-executor (Executors/newFixedThreadPool 1))
 
(defn add-tiles-at [memory-tiles
                    {:keys [nx ny nz] :as tile-index}
                    {:keys [z-origin slice-size-um
                            tile-dimensions acq-settings
                            affine-stage-to-pixel] :as screen-state}
                    acquired-images]
  (swap! acquired-images conj (select-keys tile-index [:nx :ny :nz :nt]))
  (let [[tile-width tile-height] tile-dimensions]
    (doseq [image (doall (acquire-at (affine/inverse-transform
                                       (Point. (* tile-width nx)
                                               (* tile-height ny))
                                       affine-stage-to-pixel)
                                     (+ (* nz slice-size-um) z-origin)
                                     acq-settings))]
      (let [indices {:nx nx
                     :ny ny
                     :nz (or nz (get-in image [:tags "SliceIndex"]))
                     :nt 0
                     :nc (or (get-in image [:tags "Channel"]) "Default")}]
        (store/add-to-memory-tiles 
          memory-tiles indices (image :proc) MIN-ZOOM)))))
    
(defn acquire-next-tile
  [memory-tiles-atom
   screen-state
   acquired-images]
  (when-let [next-tile (next-tile screen-state
                                  acquired-images)]
    (add-tiles-at memory-tiles-atom next-tile 
                  screen-state acquired-images)
    next-tile))

(def explore-executor (Executors/newFixedThreadPool 1))

(defn explore [memory-tiles-atom screen-state-atom acquired-images]
  (reactive/submit explore-executor
                   #(when (and (= :explore (:mode @screen-state-atom))
                               (acquire-next-tile memory-tiles-atom
                                                  @screen-state-atom
                                                  acquired-images))
                      (trampoline explore
                                  memory-tiles-atom
                                  screen-state-atom
                                  acquired-images))))

(defn navigate [screen-state-atom _ _]
  (when (#{:navigate :explore} (:mode @screen-state-atom))
    (swap! screen-state-atom assoc :mode :navigate)
    (let [{:keys [x y]} (user-controls/absolute-mouse-position @screen-state-atom)
          [w h] (:tile-dimensions @screen-state-atom)]
      (locking anti-blur
               (set-xy-position (affine/inverse-transform
                                  (Point2D$Double. x y)
                                  (:affine-stage-to-pixel @screen-state-atom)))))))
  
(defn create-acquisition-settings
  []
  "Create acquisition settings from current MDA window. Ignores everything
   but channels for now."
  (-> mm/gui .getAcquisitionEngine .getSequenceSettings
      engine/convert-settings
      (assoc :use-autofocus false
             :use-position-list false
             :frames nil
             :positions nil
             :slices nil
             :numFrames 0)))

;; SAVE AND LOAD SETTINGS


(defn save-settings [dir screen-state]
  (spit (io/file dir "metadata.txt")
        (pr-str (select-keys screen-state [:channels :tile-dimensions
                                           :pixel-size-um]))))

(defn load-settings [dir]
  (read-string (slurp (io/file dir "metadata.txt"))))

(defn pixels-not-calibrated []
  (let [answer  (JOptionPane/showConfirmDialog
                  nil
                  "The current magnification setting needs to be calibrated.
Would you like to run automatic pixel calibration?"
                  "Pixel calibration required."
                  JOptionPane/YES_NO_OPTION)]
    (when (= answer JOptionPane/YES_OPTION)
      (doto (org.micromanager.pixelcalibrator.PixelCalibratorPlugin.)
        (.setApp (MMStudio/getInstance))
        .show))))

(defn provide-constraints [screen-state-atom available-keys]
  (let [nav-range (tiles/nav-range available-keys (@screen-state-atom :tile-dimensions))]
    (swap! screen-state-atom assoc :range nav-range) ))
  
(defn watch-xy-stages [screen-state-atom]
  (reactive/handle-update
    current-xy-positions 
    (fn [_ new-pos-map]
      (let [[x y] (new-pos-map (mm/core getXYStageDevice))
            pixel (affine/transform (Point2D$Double. x y)
                                    (@screen-state-atom :affine-stage-to-pixel))]
        (swap! screen-state-atom assoc :xy-stage-position
               (affine/point-to-vector pixel))))))

(defn double-click-to-navigate [screen-state-atom]
  (user-controls/handle-double-click
    (view/panel screen-state-atom)
    (partial navigate screen-state-atom)))
 
(defn explore-when-needed [screen-state-atom explore-fn]
  (mm/core waitForSystem)
  (reactive/add-watch-simple
    screen-state-atom 
             (fn [old new]
               (when-not (= old new)
                 (explore-fn))))
  (explore-fn))
  
(defn new-acquisition-session [screen-state-atom memory-tiles-atom]
  (let [acquired-images (atom #{})
        dir (tile-cache/tile-dir memory-tiles-atom)
        acq-settings (create-acquisition-settings)
        affine-stage-to-pixel (origin-here-stage-to-pixel-transform)
        z-origin (if-let [z-drive (focus-device)]
                   (mm/core getPosition z-drive)
                   0 )
        first-seq (acquire-at (affine/inverse-transform
                                (Point. 0 0) affine-stage-to-pixel)
                              z-origin
                              acq-settings)
        explore-fn #(explore memory-tiles-atom screen-state-atom acquired-images)]
    (def fs  first-seq)
    (let [width (mm/core getImageWidth)
          height (mm/core getImageHeight)]
      (swap! screen-state-atom merge
             {:acq-settings acq-settings
              :affine-stage-to-pixel affine-stage-to-pixel
              :pixel-size-um (pixel-size-um affine-stage-to-pixel)
              :z-origin z-origin
              :slice-size-um 1.0
              :dir dir
              :mode :explore
              :channels (initial-lut-maps first-seq)
              :tile-dimensions [width height]
              :x (long (/ width 2))
              :y (long (/ height 2))}))
    (println (:channels @screen-state-atom))
    (double-click-to-navigate screen-state-atom)
    (watch-xy-stages screen-state-atom)
    (positions/handle-positions screen-state-atom)
    (user-controls/handle-mode-keys (view/panel screen-state-atom) screen-state-atom)
    (explore-when-needed screen-state-atom explore-fn)
    (when @utils/testing
      (def ai acquired-images)
      (def affine affine-stage-to-pixel))))
  

; Overall scheme
; the GUI is generally reactive.
; vars:
; memory-tiles (indices -> pixels)
; disk-tile-index (set of tile indices)
; display-tiles (indices -> pixels)
; view-state
;
; Whenever an image is acquired, it is processed, mipmapped and each
; resulting tiles are added to memory-tiles-atom. Tiles
; added are automatically asynchronously saved to disk, and the indices
; are added to disk-tiles.
; memory-tiles and display-tiles-atom are limited to 200 images each,
; using an LRU eviction policy.
; When view-state viewing area is adjusted, tiles needed for the
; new viewing area are loaded back into memory-tiles. If we are
; in explore mode, then images not available in memory-tiles or
; disk tiles are acquired according to the spiral trajectory.
; Overlay tiles are generated whenever memory tiles are added
; or the contrast is changed.
; The view redraws tiles inside viewing area whenever view-state
; has been adjusted or a new image appears in display-tiles-atom.

(defn go
  "The main function that starts a slide explorer window."
  ([dir new?]
    (mm/load-mm (MMStudio/getInstance))
    (when (and new? (not (origin-here-stage-to-pixel-transform)))
      (pixels-not-calibrated))
    (let [settings (if-not new?
                     (load-settings dir)
                     {:tile-dimensions [512 512]})
          memory-tiles-atom (tile-cache/create-tile-cache 200 dir (not new?))
          screen-state-atom (view/show memory-tiles-atom settings)]
      (if new?
        (new-acquisition-session screen-state-atom memory-tiles-atom)
        (future (let [indices (disk/available-keys dir)]
                  (provide-constraints screen-state-atom indices))))
      (save-settings dir @screen-state-atom)
      (def ss screen-state-atom)))
  ([]
    (go (io/file (str "tmp" (rand-int 10000000))) true)))
  
(defn create-data-set
  []
  (when-let [dir (persist/create-dir-dialog)]
    (go (io/file dir) true)))

(defn load-data-set
  []
  (when-let [dir (persist/open-dir-dialog)]
    (go (io/file dir) false)))

