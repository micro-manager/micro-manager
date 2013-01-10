(ns slide-explorer.main
  (:import (java.awt Color Point)
           (java.awt.geom AffineTransform Point2D$Double)
           (java.util.prefs Preferences)
           (java.util.concurrent Executors)
           (org.micromanager AcquisitionEngine2010 MMStudioMainFrame)
           (org.micromanager.utils ImageUtils JavaUtils)
           (org.micromanager.acquisition TaggedImageQueue))
  (:require [clojure.java.io :as io]
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
            [slide-explorer.persist :as persist]))

;; affine transforms

(defonce positions-atom (atom []))

(def gui-prefs (Preferences/userNodeForPackage MMStudioMainFrame))

(def current-xy-positions (atom {}))

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
  (affine/set-destination-origin
    (get-stage-to-pixel-transform)
    (.getXYStagePosition mm/gui)))

(defn pixel-size-um
  "Compute the pixel size from a stage-to-pixel transform."
  [stage-to-pixel-transform]
  (Math/pow (.getDeterminant stage-to-pixel-transform) -1/2))

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

(defn get-xy-position []
  (mm/core waitForDevice (mm/core getXYStageDevice))
  (.getXYStagePosition mm/gui))

(defn set-xy-position
  ([^Point2D$Double position]
    (set-xy-position (.x position) (.y position)))
  ([x y]
    (let [stage (mm/core getXYStageDevice)]
      (mm/core waitForDevice stage)
      (mm/core setXYPosition stage x y)
      (mm/core waitForDevice stage)
      (swap! current-xy-positions assoc stage [x y]))))

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
  ([x y settings]
    (acquire-at (Point2D$Double. x y) settings))
  ([^Point2D$Double stage-pos settings]
    (let [xy-stage (mm/core getXYStageDevice)]
      (set-xy-position stage-pos)
      (acquire-processor-sequence settings)
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
                      [(or chan "Default") (assoc (apply image/intensity-range (map :proc images)) :gamma 1.0)]))))

;; tile arrangement

(defn next-tile [screen-state acquired-images]
  (let [tile-dimensions (screen-state :tile-dimensions)
        center (tiles/center-tile [(:x screen-state) (:y screen-state)]
                                 tile-dimensions)
        pixel-rect (view/pixel-rectangle screen-state)
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
 
(defn add-tiles-at [memory-tiles [nx ny] affine-stage-to-pixel
                    acquired-images tile-dimensions settings]
  (swap! acquired-images conj [nx ny])
  (let [[tile-width tile-height] tile-dimensions]
    (doseq [image (doall (acquire-at (affine/inverse-transform
                                       (Point. (* tile-width nx)
                                               (* tile-height ny))
                                       affine-stage-to-pixel)
                                     settings))]
      (let [indices {:nx nx
                     :ny ny
                     :nz (get-in image [:tags "SliceIndex"])
                     :nt 0
                     :nc (or (get-in image [:tags "Channel"]) "Default")}]
        (view/add-to-memory-tiles 
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
                  (@screen-state-atom :tile-dimensions)
                  (@screen-state-atom :acq-settings))
    next-tile))

(def explore-executor (Executors/newFixedThreadPool 1))

(defn explore [memory-tiles-atom screen-state-atom acquired-images
               affine]
  ;(println "explore" (and (= :explore (:mode @screen-state-atom))))
  (reactive/submit explore-executor
                   #(when (and (= :explore (:mode @screen-state-atom))
                               (acquire-next-tile memory-tiles-atom
                                                  screen-state-atom
                                                  acquired-images
                                                  affine))
                      (explore memory-tiles-atom screen-state-atom
                               acquired-images affine))))

(defn navigate [screen-state-atom affine-transform _ _]
  (when (#{:navigate :explore} (:mode @screen-state-atom))
    (swap! screen-state-atom assoc :mode :navigate)
    (let [{:keys [x y]} (user-controls/absolute-mouse-position @screen-state-atom)
               [w h] (:tile-dimensions @screen-state-atom)]
      (set-xy-position (affine/inverse-transform
                         (Point2D$Double. x y)
                         affine-transform)))))
  
(defn create-acquisition-settings []
  (-> mm/gui .getAcquisitionEngine .getSequenceSettings
      engine/convert-settings
      (assoc :use-autofocus false
             :frames nil
             :positions nil
             :numFrames 0)))

;; Position List

(defn alter-position [screen-state-atom conj-or-disj position-map]
  (swap! screen-state-atom update-in [:positions]
         conj-or-disj position-map))

(defn grid-distances [pos0 pos1]
  (merge-with #(Math/abs (- %1 %2)) pos0 pos1))

(defn tile-distances [grid-distances w h]
  (merge-with / grid-distances {:x w :y h}))

(defn in-tile [{:keys [x y] :as tile-distances}]
  (and (>= 1/2 x) (>= 1/2 y)))

(defn position-clicked [available-positions pos w h]
  (first
    (filter #(-> %
                 (grid-distances pos)
                 (tile-distances w h)
                 (in-tile))
            available-positions)))

(defn toggle-position [screen-state-atom _ _]
  (let [pos (user-controls/absolute-mouse-position @screen-state-atom)
        [w h] (:tile-dimensions @screen-state-atom)]
    (if-let [old-pos (position-clicked (:positions @screen-state-atom) pos w h)]
      (alter-position screen-state-atom disj old-pos)
      (alter-position screen-state-atom conj pos))))

(defn get-position-list-coords []
  (set
    (map #(-> % bean
              (select-keys [:x :y]))
         (mm/get-positions))))

(defn update-positions-atom! []
  (let [new-value (get-position-list-coords)]
    (when (not= new-value @positions-atom)
      (reset! positions-atom new-value))))

(defn follow-positions []
  (future (loop []
            (update-positions-atom!)
            (Thread/sleep 1000)
            (recur))))


;; flat field determination

(def flat-field-positions
  (map #(* 1/8 %) (range -4 5)))

(def flat-field-coords
  (for [x flat-field-positions
        y flat-field-positions]
    [x y]))

(defn flat-field-scaled-coords []
  (let [width (mm/core getImageWidth)
        height (mm/core getImageHeight)]
    (map #(let [[x y] %] (Point. (* width x) (* height y)))
         flat-field-coords))) 

(defn flat-field-stage-coords [scaled-coords]
  (let [transform (origin-here-stage-to-pixel-transform)]
    (map #(affine/inverse-transform % transform)
         scaled-coords)))
    
(defn flat-field-acquire []
  (let [settings (create-acquisition-settings)
        scaled (flat-field-scaled-coords)
        to-and-fro (concat scaled (reverse scaled))]
    (for [coords (flat-field-stage-coords to-and-fro)]
      (acquire-at coords settings))))

(defn flat-field-save [images]
  (let [dir (io/file "flatfield")]
    (.mkdirs dir)
    (dorun
      (loop [images0 (map :proc (flatten images))
             i 0]
        (println i)
        (when-let [image (first images0)]
          (slide-explorer.disk/write-tile dir {:i i} (first images0)))
        (when-let [more (next images0)]
          (recur more (inc i)))))))

;; SAVE AND LOAD SETTINGS


(defn save-settings [dir screen-state]
  (spit (io/file dir "metadata.txt")
        (pr-str (select-keys screen-state [:channels :tile-dimensions
                                           :pixel-size-um]))))

(defn load-settings [dir]
  (read-string (slurp (io/file dir "metadata.txt"))))

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
    (let [settings (if-not new? (load-settings dir) {:tile-dimensions [512 512]})
          acquired-images (atom #{})
          [screen-state memory-tiles panel] (view/show dir acquired-images settings)]
      (println settings)
      (when new?
        (mm/core waitForDevice (mm/core getXYStageDevice))
        (let [acq-settings (create-acquisition-settings)
              affine-stage-to-pixel (origin-here-stage-to-pixel-transform)
              first-seq (acquire-at (affine/inverse-transform
                                      (Point. 0 0) affine-stage-to-pixel)
                                    acq-settings)
              explore-fn #(explore memory-tiles screen-state acquired-images
                                   affine-stage-to-pixel)
              stage (mm/core getXYStageDevice)]
          (.mkdirs dir)
          (def mt memory-tiles)
          (def pnl panel)
          (def affine affine-stage-to-pixel)
          (println "about to get channel luts")
          (user-controls/handle-double-click
            panel
            (partial navigate screen-state affine-stage-to-pixel))
          (user-controls/handle-shift-click
            panel
            (fn [x y] (toggle-position screen-state x y)))
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
                  :dir dir
                  :mode :explore
                  :channels (initial-lut-maps first-seq)
                  :tile-dimensions [(mm/core getImageWidth)
                                    (mm/core getImageHeight)]})
          (add-watch screen-state "explore" (fn [_ _ old new] (when-not (= old new)
                                                                (explore-fn))))
          (explore-fn)))
      (save-settings dir @screen-state)
      (println dir)
      (def ss screen-state)
      (def ai acquired-images)))
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
  (view/add-to-memory-tiles mt {:nx nx
                              :ny ny
                              :nz nz
                              :nt 0
                              :nc nc}
                          (get-tile nil)))

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