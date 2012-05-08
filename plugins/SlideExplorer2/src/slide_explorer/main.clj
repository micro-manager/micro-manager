(ns slide-explorer.main
  (:import (java.awt Color Graphics Graphics2D RenderingHints Window)
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
  (:use [org.micromanager.mm :only (core edt mmc load-mm json-to-data)]
        [slide-explorer.view :only (show add-to-available-tiles)]))


(load-mm)

;; hardware communications

(def gui-prefs (Preferences/userNodeForPackage MMStudioMainFrame))

(defn set-stage-to-pixel-transform [^AffineTransform affine-transform]
  (JavaUtils/putObjectInPrefs
    gui-prefs (str "affine_transform_" (core getCurrentPixelSizeConfig))
    affine-transform))

(defn get-stage-to-pixel-transform []
  (JavaUtils/getObjectFromPrefs
    gui-prefs (str "affine_transform_" (core getCurrentPixelSizeConfig))
    nil))

(def angle (atom 0))

(def grab-tagged-image
  "Grab a single image from camera."
  ;(memoize 
    (fn []
      (core snapImage)
      (core getTaggedImage)))
  ;)

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

;; pixels/stage

(defn pixels-to-stage [^AffineTransform pixel-to-stage-transform [x y]]
  (let [p (.transform pixel-to-stage-transform (Point2D$Double. x y) nil)]
    [(.x p) (.y p)]))
    
(defn stage-to-pixels [^AffineTransform pixel-to-stage-transform [x y]]
  (let [p (.inverseTransform pixel-to-stage-transform (Point2D$Double. x y) nil)]
    [(.x p) (.y p)]))
              
;; tile image handling

(defn get-tile [{:keys [nx ny nz nt nc]}]
  (ImageUtils/makeProcessor (grab-tagged-image)))
  ;(slide-explorer.image/try-3-colors false))


;; tests

(defn start []
  (let [available-tiles (agent {})]
    (def at available-tiles)
    (show available-tiles)))

;(defn set-rgb-luts []
;  (swap! ss assoc :luts rgb))

(defn test-tile [nx ny]
  (add-to-available-tiles at {:nx nx
                              :ny ny
                              :nz 0 
                              :nt 0
                              :nc 0}
                          (get-tile nil)))

(defn test-tile [nx ny nz nc]
  (add-to-available-tiles at {:nx nx
                              :ny ny
                              :nz nz
                              :nt 0
                              :nc 0}
                          (get-tile nil)))

(defn test-tiles
  ([n] (test-tiles n n 0 0))
  ([nx ny nz nc]
    (.start (Thread.
              #(doseq [i (range (- nx) (inc nx)) j (range (- ny) (inc ny))
                       k (range (- nz) (inc nz))]
                 ;(Thread/sleep 1000)
                 (test-tile i j k nc))))))


(defn test-rotate []
  (.start (Thread. #(do (dorun (repeatedly 2000 (fn [] (Thread/sleep 10)
                                                  (swap! angle + 0.02))))
                        (reset! angle 0)))))



