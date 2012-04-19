(ns slide_explorer.display
  (:import (javax.swing JFrame)
           (java.awt Canvas Graphics Graphics2D RenderingHints)
           (java.awt.event MouseAdapter)
           (java.awt.image BufferedImage WritableRaster)
           (java.awt.geom AffineTransform)
           (java.io ByteArrayInputStream)
           (javax.imageio ImageIO)
           (mmcorej TaggedImage)
           (org.micromanager.utils GUIUpdater ImageUtils MDUtils))
  (:use [org.micromanager.mm :only (mmc load-mm json-to-data)]))

(load-mm)

(defrecord screen-properties [x y z width height zoom])  

(def screen-state (atom nil))

(defn snap-image []
  (. mmc snapImage)
  (. mmc getTaggedImage))

(defn image-data [^TaggedImage tagged-image]
  {:pixels (.pix tagged-image)
   :tags (json-to-data (.tags tagged-image))})

(defn get-image-dimensions [^TaggedImage tagged-image]
  [(MDUtils/getWidth (.tags tagged-image))
   (MDUtils/getHeight (.tags tagged-image))])

(defn buffered-image
  ([[w h] pixels]
    (let [image (ImageIO/read (ByteArrayInputStream. pixels))
          raster (cast WritableRaster (.getData image))]
      (.setPixels raster 0 0 w h pixels)
      image))
  ([^TaggedImage tagged-image]
    (buffered-image (get-image-dimensions tagged-image) (.pix tagged-image))))

(defn enable-anti-aliasing
  ([^Graphics g]
    (enable-anti-aliasing g true))
  ([^Graphics g on]
    (let [graphics2d (cast Graphics2D g)]
      (.setRenderingHint graphics2d
                         RenderingHints/KEY_ANTIALIASING
                         (if on RenderingHints/VALUE_ANTIALIAS_ON
                           RenderingHints/VALUE_ANTIALIAS_OFF)))))
       
(defn draw-tile [^Graphics2D g image-data]
  (.drawImage g (buffered-image [512 512] (:pixels image-data))
              (AffineTransform.) nil))

(defn paint-tiles [canvas]
  (println (java.util.Date.))
  (doseq [x (range 10 100 10)]
    (.drawOval (.getGraphics canvas)
               x x
               (- (.getWidth canvas) (* 2 x))
               (- (.getHeight canvas) (* 2 x)))))

(defn paint-screen [canvas]
  (enable-anti-aliasing (.getGraphics canvas))
  (paint-tiles canvas)
  (println (.getBounds canvas)))

(defn handle-drags [component position-atom]
  (let [drag-origin (atom nil)
        mouse-adapter
        (proxy [MouseAdapter] []
          (mousePressed [e]
                        (println e)
                        (reset! drag-origin {:x (.getX e) :y (.getY e)})
                        (println @drag-origin))
          (mouseReleased [e]
                         (println e)
                         (reset! drag-origin nil)
                         (println @drag-origin))
          (mouseDragged [e]
                        (println e)
                        (let [x (.getX e) y (.getY e)]
                          (swap! position-atom update-in [:x]
                                 + (- x (:x @drag-origin)))
                          (swap! position-atom update-in [:y]
                                 + (- y (:y @drag-origin)))
                          (reset! drag-origin {:x (.getX e) :y (.getY e)}))
                        (println @position-atom)))]
    (doto component
      (.addMouseListener mouse-adapter)
      (.addMouseMotionListener mouse-adapter))))

(defn main-canvas []
  (let [updater (GUIUpdater.)]
    (doto (proxy [Canvas] []
            (paint [^Graphics g] (paint-screen this))))))
    
(defn main-frame []
  (doto (JFrame. "Slide Explorer II")
    .show
    (.setBounds 10 10 500 500)))

(defn show []
  (let [canvas (main-canvas)]
    (.add (.getContentPane (main-frame)) canvas)
    canvas))

;;;;;;;;

