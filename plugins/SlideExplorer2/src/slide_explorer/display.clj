(ns slide_explorer.display
  (:import (javax.swing JFrame JLabel)
           (java.awt Canvas Graphics Graphics2D RenderingHints)
           (java.awt.event MouseAdapter WindowAdapter)
           (java.awt.image BufferedImage WritableRaster)
           (java.awt.geom AffineTransform)
           (java.io ByteArrayInputStream)
           (javax.imageio ImageIO)
           (mmcorej TaggedImage)
           (org.micromanager.utils GUIUpdater ImageUtils MDUtils))
  (:use [org.micromanager.mm :only (edt mmc load-mm json-to-data)]))

(load-mm)

(defn reference-viewer [reference key]
  (let [frame (JFrame. key)
        label (JLabel.)]
    (.add (.getContentPane frame) label)
    (add-watch reference key
               (fn [key reference old-state new-state]
                 (edt (.setText label (.toString new-state)))))
    (doto frame
      (.addWindowListener
        (proxy [WindowAdapter] []
          (windowClosing [e]
                         (remove-watch reference key))))
      .show))
  reference)

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
  (doseq [x (range 10 100 10)]
    (.drawOval (.getGraphics canvas)
               x x
               (- (.getWidth canvas) (* 2 x))
               (- (.getHeight canvas) (* 2 x)))))

(defn paint-screen [canvas]
  (enable-anti-aliasing (.getGraphics canvas))
  (paint-tiles canvas))

(defn handle-drags [component position-atom]
  (let [drag-origin (atom nil)
        mouse-adapter
        (proxy [MouseAdapter] []
          (mousePressed [e]
                        (reset! drag-origin {:x (.getX e) :y (.getY e)}))
          (mouseReleased [e]
                         (reset! drag-origin nil))
          (mouseDragged [e]
                        (let [x (.getX e) y (.getY e)]
                          (swap! position-atom update-in [:x]
                                 + (- x (:x @drag-origin)))
                          (swap! position-atom update-in [:y]
                                 + (- y (:y @drag-origin)))
                          (reset! drag-origin {:x x :y y}))))]
    (doto component
      (.addMouseListener mouse-adapter)
      (.addMouseMotionListener mouse-adapter))
    position-atom))

(defn handle-wheel [component z-atom]
  (.addMouseWheelListener component
    (proxy [MouseAdapter] []
      (mouseWheelMoved [e]
                       (swap! z-atom update-in [:z]
                              + (.getWheelRotation e)))))
  z-atom)

(defn main-canvas []
  (let [updater (GUIUpdater.)]
    (doto (proxy [Canvas] []
            (paint [^Graphics g] (paint-screen this))))))
    
(defn main-frame []
  (doto (JFrame. "Slide Explorer II")
    .show
    (.setBounds 10 10 500 500)))

(defn show []
  (let [canvas (main-canvas)
        screen-state (atom (sorted-map :x 0 :y 0 :z 0))]
    (.add (.getContentPane (main-frame)) canvas)
    (handle-drags canvas screen-state)
    (handle-wheel canvas screen-state)))

;;;;;;;;

