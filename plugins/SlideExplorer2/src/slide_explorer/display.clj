(ns slide_explorer.display
  (:import (javax.swing AbstractAction JComponent JFrame JLabel KeyStroke)
           (java.awt Canvas Color Graphics Graphics2D RenderingHints Window)
           (java.awt.event ComponentAdapter KeyAdapter KeyEvent MouseAdapter WindowAdapter)
           (java.awt.image BufferedImage WritableRaster)
           (java.awt.geom AffineTransform)
           (java.io ByteArrayInputStream)
           (java.util UUID)
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

(defn- default-screen-device []
  (->
    (java.awt.GraphicsEnvironment/getLocalGraphicsEnvironment)
    .getDefaultScreenDevice))

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

(defn full-screen! ; adapted from seesaw
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
  (let [g (.getGraphics canvas)]
    (doto g
      enable-anti-aliasing
      (.setColor Color/YELLOW)
      (.fillOval (- (+ (/ (:width @screen-state) 2)
                       (:x @screen-state))
                    30)
                 (- (+ (/ (:height @screen-state) 2)
                       (:y @screen-state))
                    30)
                 60 60)
      (.setColor Color/GRAY)
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

