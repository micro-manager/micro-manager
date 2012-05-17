(ns slide-explorer.main
  (:import (java.awt Color Graphics Graphics2D Point RenderingHints Window)
           (java.awt.event ComponentAdapter KeyAdapter KeyEvent MouseAdapter
                           WindowAdapter)
           (java.awt.image BufferedImage WritableRaster)
           (java.awt.geom AffineTransform Point2D$Double)
           (java.io ByteArrayInputStream)
           (java.util UUID)
           (java.util.prefs Preferences)
           (javax.imageio ImageIO)
           (ij ImagePlus)
           (ij.process ImageProcessor)
           (mmcorej TaggedImage)
           (org.micromanager AcqEngine MMStudioMainFrame)
           (org.micromanager.utils GUIUpdater ImageUtils JavaUtils MDUtils)
           (org.micromanager.acquisition TaggedImageQueue))
  (:use [org.micromanager.mm :only (core edt mmc gui load-mm json-to-data)]
        [slide-explorer.affine :only (set-destination-origin transform inverse-transform)]
        [slide-explorer.view :only (show add-to-available-tiles)]
        [slide-explorer.image :only (show-image statistics intensity-range lut-object)]))


(load-mm)

;; hardware communications

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

(def angle (atom 0))

(def grab-tagged-image
  "Grabs a single image from camera."
    (fn []
      (core snapImage)
      (core getTaggedImage)))

(def pixel-size (core getPixelSizeUm true))

(defn tagged-image-sequence []
  (let [q (.runSilent (AcqEngine.))]
    (take-while #(not= % TaggedImageQueue/POISON)
                (repeatedly #(.take q)))))

(defn tagged-image-to-processor [tagged-image]
  {:proc (ImageUtils/makeProcessor tagged-image)
   :tags (json-to-data (.tags tagged-image))})

(defn processor-sequence []
  (map tagged-image-to-processor (tagged-image-sequence)))

(defn get-channel-index [raw]
  (-> raw :tags (get "ChannelIndex")))

(defn get-frame-index [raw]
  (-> raw :tags (get "FrameIndex")))

(defn get-slice-index [raw]
  (-> raw :tags (get "SliceIndex")))

;; stage communications

(defn get-xy-position []
  (core waitForDevice (core getXYStageDevice))
  (.getXYStagePosition gui))

(defn set-xy-position [^Point2D$Double position]
  (core waitForDevice (core getXYStageDevice))
  (core setXYPosition (core getXYStageDevice) (.x position) (.y position)))
  
;; image properties

(defn get-image-width [^TaggedImage image]
  (MDUtils/getWidth (.tags image)))
  
(defn get-image-height [^TaggedImage image]
  (MDUtils/getWidth (.tags image)))

(defn tile-dimensions [^TaggedImage image]
  (let [w (get-image-width image)
        h (get-image-height image)]
    [(long (* 3/4 w)) (long (* 3/4 h))]))

(defn image-data [^TaggedImage tagged-image]
  {:pixels (.pix tagged-image)
   :tags (json-to-data (.tags tagged-image))})

(defn get-image-dimensions [^TaggedImage tagged-image]
  [(MDUtils/getWidth (.tags tagged-image))
   (MDUtils/getHeight (.tags tagged-image))])

(defn stack-colors
  "Gets the channel colors from a tagged-processor-sequence."
  [tagged-processor-sequence]
  (let [summary (-> tagged-processor-sequence first :tags (get "Summary"))]
    (zipmap (summary "ChNames") (map #(Color. %) (summary "ChColors")))))

;; pixels/stage

(defn pixels-to-stage [^AffineTransform pixel-to-stage-transform [x y]]
  (let [p (.transform pixel-to-stage-transform (Point2D$Double. x y) nil)]
    [(.x p) (.y p)]))
    
(defn stage-to-pixels [^AffineTransform pixel-to-stage-transform [x y]]
  (let [p (.inverseTransform pixel-to-stage-transform (Point2D$Double. x y) nil)]
    [(.x p) (.y p)]))      

;; tiles/pixels

(defn tiles-to-pixels [tile-width [x y]]
  [(* x tile-width) (* y tile-width)])

(defn pixels-to-tiles [tile-width [x y]]
  [(/ x tile-width) (/ y tile-width)])

;; tile image handling

(defn get-tile [{:keys [nx ny nz nt nc]}]
  (ImageUtils/makeProcessor (grab-tagged-image)))

;; run using acquisitions

(defn initial-channel-display-settings [tagged-image-processors]
  (merge-with merge
              (into {}
                    (for [[chan color] (stack-colors tagged-image-processors)]
                      [chan {:color color}]))
              (into {}
                    (for [[chan images] (group-by #(get-in % [:tags "Channel"]) tagged-image-processors)]
                      [chan (assoc (intensity-range (map :proc images)) :gamma 1.0)]))))

(defn initial-lut-objects [tagged-image-processors]
  (into {}
        (for [[chan lut-map] (initial-channel-display-settings tagged-image-processors)]
          [chan {:lut (lut-object lut-map)}])))

(defn acquire-at
  ([x y]
    (acquire-at (Point2D$Double. x y)))
  ([^Point2D$Double stage-pos]
    (let [xy-stage (core getXYStageDevice)]
      (set-xy-position stage-pos)
      (core waitForDevice xy-stage)
      (processor-sequence))))

(defn origin-here-stage-to-pixel-transform []
  (set-destination-origin
    (get-stage-to-pixel-transform)
    (.getXYStagePosition gui)))

(defn add-tiles-at [available-tiles [nx ny] affine-stage-to-pixel]
  (doseq [image (acquire-at (inverse-transform (Point. (* 512 nx) (* 512 ny)) affine-stage-to-pixel))]
    (add-to-available-tiles available-tiles
                            {:nx nx :ny ny :nz (get-in image [:tags "SliceIndex"]) :nt 0
                             :nc (get-in image [:tags "Channel"])}
                            (image :proc))))



(defn go []
  (core waitForDevice (core getXYStageDevice))
  (let [available-tiles (agent {})
        xy-stage (core getXYStageDevice)
        affine-stage-to-pixel (origin-here-stage-to-pixel-transform)
        first-seq (acquire-at (inverse-transform (Point. 0 0) affine-stage-to-pixel))]
    (def at available-tiles)
    (def ss (show available-tiles))
    (doseq [image first-seq]
      (add-to-available-tiles available-tiles
                              {:nx 0 :ny 0 :nz (get-in image [:tags "SliceIndex"]) :nt 0
                               :nc (get-in image [:tags "Channel"])}
                              (image :proc)))
    (swap! ss assoc :channels (initial-lut-objects first-seq))
    (doseq [nx (range -4 5) ny (range -4 5)]
      (add-tiles-at available-tiles [nx ny] affine-stage-to-pixel))))

  

;; tests

(defn start []
  (let [available-tiles (agent {})
        xy-stage (core getXYStageDevice)]
    (def at available-tiles)
    (def ss (show available-tiles))))

(def test-channels
  {"DAPI" {:lut (lut-object Color/BLUE  0 255 1.0)}
   "GFP"  {:lut (lut-object Color/GREEN 0 255 1.0)}
   "Cy5"  {:lut (lut-object Color/RED   0 255 1.0)}})

(defn test-start []
  (def ss (start))
  (swap! ss assoc :channels test-channels))

(defn test-tile [nx ny nz nc]
  (add-to-available-tiles at {:nx nx
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


(defn test-rotate []
  (.start (Thread. #(do (dorun (repeatedly 2000 (fn [] (Thread/sleep 10)
                                                  (swap! angle + 0.02))))
                        (reset! angle 0)))))



