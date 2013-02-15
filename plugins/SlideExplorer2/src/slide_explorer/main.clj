(ns slide-explorer.main
  (:import (java.awt Color Point)
           (java.awt.geom AffineTransform Point2D$Double)
           (java.util.prefs Preferences)
           (java.util.concurrent Executors)
           (javax.swing JOptionPane)
           (org.micromanager AcquisitionEngine2010 MMStudioMainFrame)
           (org.micromanager.utils ImageUtils JavaUtils)
           (org.micromanager.acquisition TaggedImageQueue))
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
            [slide-explorer.canvas :as canvas]
            [slide-explorer.tiles :as tiles]
            [slide-explorer.persist :as persist]
            [slide-explorer.disk :as disk]
            [slide-explorer.positions :as positions]
            [slide-explorer.store :as store]))


(def MIN-ZOOM 1/256)

(def MAX-ZOOM 1)

;; affine transforms

(defonce positions-atom (atom []))

(def gui-prefs (Preferences/userNodeForPackage MMStudioMainFrame))

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
        q (engine/run acq-engine settings false)]
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

(defn initial-lut-maps [tagged-image-processors]
  (merge-with merge
              (into {}
                    (for [[chan color] (stack-colors tagged-image-processors)]
                      [chan {:color color}]))
              (into {}
                    (for [[chan images] (group-by #(get-in % [:tags "Channel"]) tagged-image-processors)]
                      [(or chan "Default") (assoc (apply image/intensity-range (map :proc images)) :gamma 1.0)]))))

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
 
(defn add-tiles-at [memory-tiles {:keys [nx ny nz] :as tile-index}
                    affine-stage-to-pixel
                    {:keys [z-origin slice-size-um
                            tile-dimensions acq-settings] :as screen-state}
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
   acquired-images affine]
  (when-let [next-tile (next-tile screen-state
                                  acquired-images)]
    (add-tiles-at memory-tiles-atom next-tile affine 
                  screen-state
                  acquired-images)
    next-tile))

(def explore-executor (Executors/newFixedThreadPool 1))

(defn explore [memory-tiles-atom screen-state-atom acquired-images
               affine]
  (reactive/submit explore-executor
                   #(when (and (= :explore (:mode @screen-state-atom))
                               (acquire-next-tile memory-tiles-atom
                                                  @screen-state-atom
                                                  acquired-images
                                                  affine))
                      (explore memory-tiles-atom screen-state-atom
                               acquired-images affine))))

(defn navigate [screen-state-atom affine-transform _ _]
  (when (#{:navigate :explore} (:mode @screen-state-atom))
    (swap! screen-state-atom assoc :mode :navigate)
    (let [{:keys [x y]} (user-controls/absolute-mouse-position @screen-state-atom)
          [w h] (:tile-dimensions @screen-state-atom)]
      (locking anti-blur
               (set-xy-position (affine/inverse-transform
                                  (Point2D$Double. x y)
                                  affine-transform))))))
  
(defn create-acquisition-settings
  []
  "Create acquisition settings from current MDA window. Ignores everything
   but channels for now."
  (-> mm/gui .getAcquisitionEngine .getSequenceSettings
      engine/convert-settings
      (assoc :use-autofocus false
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
      (doto (eval '(org.micromanager.pixelcalibrator.PixelCalibratorPlugin.))
        (.setApp (MMStudioMainFrame/getInstance));
        .show))))

(defn provide-constraints [screen-state-atom dir]
  (let [available-keys (disk/available-keys dir)
        tile-range (tiles/tile-range available-keys)
        nav-range (tiles/nav-range tile-range (@screen-state-atom :tile-dimensions))]
    (swap! screen-state-atom assoc :range nav-range) ))
  

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
    (mm/load-mm (MMStudioMainFrame/getInstance))
    (if (and new? (not (origin-here-stage-to-pixel-transform)))
      (pixels-not-calibrated)
      (let [settings (if-not new? (load-settings dir) {:tile-dimensions [512 512]})
            acquired-images (atom #{})
            memory-tiles (tile-cache/create-tile-cache 200 dir (not new?))
            [screen-state panel] (view/show memory-tiles settings)]
        (if new?
          (let [acq-settings (create-acquisition-settings)
                affine-stage-to-pixel (origin-here-stage-to-pixel-transform)
                z-origin (if-let [z-drive (focus-device)]
                           (mm/core getPosition z-drive)
                           0 )
                first-seq (acquire-at (affine/inverse-transform
                                        (Point. 0 0) affine-stage-to-pixel)
                                      z-origin
                                      acq-settings)
                explore-fn #(explore memory-tiles screen-state acquired-images
                                     affine-stage-to-pixel)
                stage (mm/core getXYStageDevice)]
            (mm/core waitForDevice stage)
            (.mkdirs dir)
            (def pnl panel)
            (def affine affine-stage-to-pixel)
            (user-controls/handle-double-click
              panel
              (partial navigate screen-state affine-stage-to-pixel))
            (positions/handle-positions panel screen-state affine-stage-to-pixel)
            (user-controls/handle-mode-keys panel screen-state)
            (reactive/handle-update
              current-xy-positions 
              (fn [_ new-pos-map]
                (let [[x y] (new-pos-map (mm/core getXYStageDevice))
                      pixel (affine/transform (Point2D$Double. x y)
                                              affine-stage-to-pixel)]
                  (swap! screen-state assoc :xy-stage-position
                         (affine/point-to-vector pixel)))))
            (swap! screen-state merge
                   {:acq-settings acq-settings
                    :pixel-size-um (pixel-size-um affine-stage-to-pixel)
                    :z-origin z-origin
                    :slice-size-um 1.0
                    :dir dir
                    :mode :explore
                    :channels (initial-lut-maps first-seq)
                    :tile-dimensions [(mm/core getImageWidth)
                                      (mm/core getImageHeight)]})
            (add-watch screen-state "explore" (fn [_ _ old new] (when-not (= old new)
                                                                  (explore-fn))))
            (explore-fn))
          (future (provide-constraints screen-state dir)))
        (save-settings dir @screen-state)
        (def mt memory-tiles)
        (def ss screen-state)
        (def ai acquired-images))))
  ([]
    (go (io/file (str "tmp" (rand-int 10000000))) true)))
  

(defn navigate-to-pixel [[pixel-x pixel-y] affine-stage-to-pixel]
  (set-xy-position (affine/inverse-transform
                     (Point2D$Double. pixel-x pixel-y)
                     affine-stage-to-pixel)))                
  
(defn load-data-set
  []
  (when-let [dir (persist/open-dir-dialog)]
    (go (io/file dir) false)))

;; tests

(defn get-tile [{:keys [nx ny nz nt nc]}]
  (ImageUtils/makeProcessor (grab-tagged-image)))

(def test-channels
  {"DAPI" {:lut (image/lut-object Color/BLUE  0 255 1.0)}
   "GFP"  {:lut (image/lut-object Color/GREEN 0 255 1.0)}
   "Cy5"  {:lut (image/lut-object Color/RED   0 255 1.0)}})

(defn test-tile [nx ny nz nc]
  (store/add-to-memory-tiles mt {:nx nx
                              :ny ny
                              :nz nz
                              :nt 0
                              :nc nc}
                             (get-tile nil)
                             MIN-ZOOM))

(defn test-tiles
  ([n] (test-tiles n n 0 0))
  ([nx ny nz]
    (mm/core setExposure 100)
    (.start (Thread.
              #(doseq [i (range (- nx) (inc nx))
                       j (range (- ny) (inc ny))
                       k (range (- nz) (inc nz))
                       chan (keys (@ss :channels))]
                 ;(Thread/sleep 1000)
                 (test-tile i j k chan))))))
