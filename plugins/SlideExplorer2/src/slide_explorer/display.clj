(ns slide-explorer.display
  (:import (javax.swing AbstractAction JComponent JFrame JLabel KeyStroke)
           (java.awt Canvas Color Graphics Graphics2D RenderingHints Window)
           (java.awt.event ComponentAdapter KeyAdapter KeyEvent MouseAdapter WindowAdapter)
           (java.awt.image BufferedImage WritableRaster)
           (java.awt.geom AffineTransform Point2D$Double)
           (java.io ByteArrayInputStream)
           (java.util UUID)
           (java.util.prefs Preferences)
           (javax.imageio ImageIO)
           (ij ImagePlus)
           (mmcorej TaggedImage)
           (org.micromanager MMStudioMainFrame)
           (org.micromanager.utils GUIUpdater ImageUtils JavaUtils MDUtils))
  (:use [org.micromanager.mm :only (core edt mmc load-mm json-to-data)]))

(load-mm)

;; hardware communications

(defn get-stage-to-pixel-transform []
  (AffineTransform. 2.0 0.0 0.0 2.0 0.0 0.0))
;  (let [prefs (Preferences/userNodeForPackage MMStudioMainFrame)]
;    (JavaUtils/getObjectFromPrefs
;      prefs (str "affine_transform_" (core getCurrentPixelSizeConfig)) nil)))



(defn grab-tagged-image
  "Grab a single image from camera."
  []
  (core snapImage)
  (core getTaggedImage))

(def pixel-size (core getPixelSizeUm true))

(defn get-image-width [^TaggedImage image]
  (MDUtils/getWidth (.tags image)))
  
(defn get-image-height [^TaggedImage image]
  (MDUtils/getWidth (.tags image)))

(defn tile-dimensions [^TaggedImage image]
  (let [w (get-image-width image)
        h (get-image-height image)]
    [(long (* 3/4 w)) (long (* 3/4 h))]))

;; tile/pixels

(defn tile-locations-in-pixels [[nx ny] [tile-width tile-height]]
  [(* nx tile-width) (* ny tile-height)])

(defn round-int
  "Round x to the nearest integer."
  [x]
  (Math/round (double x)))

(defn tiles-in-pixel-rectangle
  "Returns a list of tile indices found in a given pixel rectangle."
  [[l t b r] [tile-width tile-height]]
  (let [nl (round-int (/ l tile-width))
        nr (round-int (/ r tile-width))
        nt (round-int (/ t tile-height))
        nb (round-int (/ b tile-height))]
    (for [nx (range nl (inc nr))
          ny (range nt (inc nb))]
      [nx ny])))

;; pixels/stage

(defn pixels-to-stage [^AffineTransform transform [x y]]
  (let [p (.transform transform (Point2D$Double. x y) nil)]
    [(.x p) (.y p)]))
    
(defn stage-to-pixels [^AffineTransform transform [x y]]
  (let [p (.inverseTransform transform (Point2D$Double. x y) nil)]
    [(.x p) (.y p)]))
              
;; tile image handling

(defn main-window []
  (ImagePlus. "Slide Explorer II"))

(defn awt-image [^TaggedImage tagged-image]
  (.getImage (ImagePlus. "" (ImageUtils/makeProcessor tagged-image))))

(def visible-tiles (agent {}))

(defn get-tile [[nx ny nz nc nt nzoom]]
  (grab-tagged-image))

(defn add-tile [tile-map indices]
  (assoc tile-map indices (get-tile indices)))

(defn add-to-visible-tiles [indices]
  (send-off visible-tiles add-tile indices))

;; gui utilities

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

(defn bind-key
  "Maps an input-key on a swing component to an action,
  such that action-fn is executed when key is pressed."
  [component input-key action-fn global?]
  (let [im (.getInputMap component (if global?
                                     JComponent/WHEN_IN_FOCUSED_WINDOW
                                     JComponent/WHEN_FOCUSED))
        am (.getActionMap component)
        input-event (KeyStroke/getKeyStroke input-key)
        action
          (proxy [AbstractAction] []
            (actionPerformed [e]
                (action-fn)))
        uuid (.. UUID randomUUID toString)]
    (.put im input-event uuid)
    (.put am uuid action)))

(defn bind-window-key
  [window input-key action-fn]
  (bind-key (.getContentPane window) input-key action-fn true))

(defn- default-screen-device [] ; borrowed from see-saw
  (->
    (java.awt.GraphicsEnvironment/getLocalGraphicsEnvironment)
    .getDefaultScreenDevice))

(defn full-screen!
  "Make the given window/frame full-screen. Pass nil to return all windows
to normal size."
  ([^java.awt.GraphicsDevice device window]
    (if window
      (do
        (.dispose window)
        (.setUndecorated window true)
        (.setFullScreenWindow device window)
        (.show window)
        )
      (let [window (.getFullScreenWindow device)]
        (.setFullScreenWindow device nil)
        (when window
          (.dispose window)
          (.setUndecorated window false)
          (.show window))))
    window)
  ([window]
    (full-screen! (default-screen-device) window)))

(defn setup-fullscreen [window]
  (bind-window-key window "F" #(full-screen! window))
  (bind-window-key window "ESCAPE" #(full-screen! nil)))

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
                         (if on
                           RenderingHints/VALUE_ANTIALIAS_ON
                           RenderingHints/VALUE_ANTIALIAS_OFF)))))
       
(defn draw-tile [^Graphics2D g image-data]
  (.drawImage g (buffered-image [512 512] (:pixels image-data))
              (AffineTransform.) nil))

(defn paint-tiles [canvas screen-state]
  (let [g (.getGraphics canvas)
        image @slide-explorer.image/image]
    (doto g
      enable-anti-aliasing
      (.drawImage image (:x @screen-state) (:y @screen-state) nil)
      (.drawImage image (+ 513 (:x @screen-state)) (:y @screen-state) nil)
      (.drawImage image (:x @screen-state) (+ 513 (:y @screen-state)) nil)
      (.drawImage image (+ 513 (:x @screen-state)) (+ 513 (:y @screen-state)) nil)
      (.setColor (Color. 0x00A08F))
      (.fillOval (- (+ (/ (:width @screen-state) 2)
                       (:x @screen-state))
                    30)
                 (- (+ (/ (:height @screen-state) 2)
                       (:y @screen-state))
                    30)
                 60 60)
      (.setColor Color/YELLOW)
      (.drawString (str @screen-state)
                   (int 0)
                   (int (- (:height @screen-state) 10))))))
  
(defn paint-screen [canvas screen-state]
  (paint-tiles canvas screen-state))

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

(defn handle-resize [component size-atom]
  (let [update-size #(let [bounds (.getBounds component)]
                       (swap! size-atom merge
                              {:width (.getWidth bounds)
                               :height (.getHeight bounds)}))]
    (update-size)
    (.addComponentListener component
      (proxy [ComponentAdapter] []
        (componentResized [e]
                          (update-size)))))
  size-atom)

(defn reflect-changes [canvas screen-state]
  (add-watch screen-state "display"
    (fn [_ _ _ new-state]
      (.repaint canvas))))     

(defn main-canvas [screen-state]
  (doto
    (proxy [Canvas] []
      (paint [^Graphics g] (paint-screen this screen-state)))
    (.setBackground Color/BLACK)))
    
(defn main-frame []
  (doto (JFrame. "Slide Explorer II")
    .show
    (.setBounds 10 10 500 500)))

(defn show []
  (let [screen-state (atom (sorted-map :x 0 :y 0 :z 0))
        canvas (main-canvas screen-state)
        frame (main-frame)]
    (.add (.getContentPane frame) canvas)
    (setup-fullscreen frame)
    (handle-drags canvas screen-state)
    (handle-wheel canvas screen-state)
    (handle-resize canvas screen-state)
    (reflect-changes canvas screen-state)
    canvas))

;;;;;;;;

