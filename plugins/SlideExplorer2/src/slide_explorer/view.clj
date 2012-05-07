(ns slide-explorer.view
  (:import (javax.swing AbstractAction JComponent JFrame JPanel JLabel KeyStroke)
           (java.awt Color Graphics Graphics2D Rectangle RenderingHints Window)
           (java.util UUID)
           (java.awt.event ComponentAdapter KeyEvent KeyAdapter
                           MouseAdapter WindowAdapter)
           (org.micromanager.utils GUIUpdater))
  (:use [org.micromanager.mm :only (edt)]
        [slide-explorer.image :only (crop merge-and-scale overlay lut-object)]))


; Order of operations:
;  Stitch/crop
;  Flatten fields
;  Max intensity projection (z)
;  Rescale
;  Color Overlay

(def MIN-ZOOM 1/256)

(def MAX-ZOOM 1)

(def display-updater (GUIUpdater.))

;; TESTING UTILITIES

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

(defmacro timer [expr]
  `(let [ret# (time ~expr)]
     (println '~expr)
     ret#))

;; GUI UTILITIES

(defn descendants
  "Returns a depth-first seq of all components contained by window."
  [window]
  (tree-seq (constantly true)
            #(.getComponents %)
            window))


;; TILE <--> PIXELS

(defn floor-int [x]
  (long (Math/floor x)))

(defn tile-to-pixels [[nx ny] [tile-width tile-height] tile-zoom]
  [(int (* tile-zoom nx tile-width))
   (int (* tile-zoom ny tile-height))])

(defn tiles-in-pixel-rectangle
  "Returns a list of tile indices found in a given pixel rectangle."
  [rectangle [tile-width tile-height]]
  (let [nl (floor-int (/ (.x rectangle) tile-width))
        nr (floor-int (/ (+ -1 (.getWidth rectangle) (.x rectangle)) tile-width))
        nt (floor-int (/ (.y rectangle) tile-height))
        nb (floor-int (/ (+ -1 (.getHeight rectangle) (.y rectangle)) tile-height))]
    (for [nx (range nl (inc nr))
          ny (range nt (inc nb))]
      [nx ny])))


;; TILING

(defn add-tile [tile-map tile-zoom indices tile]
  (assoc-in tile-map [tile-zoom indices] tile))

(defn propagate-tiles [tile-map zoom {:keys [nx ny nz nt nc] :as indices}]
  (when-let [parent-layer (tile-map (* zoom 2))]
    (let [nx- (* 2 nx)
          ny- (* 2 ny)
          nx+ (inc nx-)
          ny+ (inc ny-)
          a (parent-layer (assoc indices :nx nx- :ny ny-))
          b (parent-layer (assoc indices :nx nx+ :ny ny-))
          c (parent-layer (assoc indices :nx nx- :ny ny+))
          d (parent-layer (assoc indices :nx nx+ :ny ny+))]
      (add-tile tile-map zoom
                (assoc indices :nx nx :ny ny)
                (merge-and-scale a b c d)))))

(defn child-index [n]
  (floor-int (/ n 2)))

(defn child-indices [indices]
  (-> indices
     (update-in [:nx] child-index)
     (update-in [:ny] child-index)))

(defn add-and-propagate-tiles [tile-map indices tile]
  (loop [tile-map (add-tile tile-map 1 indices tile)
         new-indices (child-indices indices)
         zoom 1/2]
    (if (<= MIN-ZOOM zoom)
      (recur (propagate-tiles tile-map zoom new-indices)
             (child-indices new-indices)
             (/ zoom 2))
      tile-map)))

(defn add-to-available-tiles [tile-map-agent indices tile]
  (send tile-map-agent add-and-propagate-tiles indices tile))

;; PAINTING

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

(defn paint-tiles [^Graphics2D g available-tiles screen-state [tile-width tile-height]]
  (let [pixel-rect (.getClipBounds g)]
    (doseq [[nx ny] (tiles-in-pixel-rectangle pixel-rect [tile-width tile-height])]
      (when-let [proc (get-in available-tiles [(screen-state :zoom) {:nx nx :ny ny :nz (screen-state :z) :nt 0 :nc 0}])]
        (let [[x y] (tile-to-pixels [nx ny] [tile-width tile-height] 1)]
          (.drawImage g (.createImage proc) x y nil))))))

(defn paint-screen [graphics screen-state available-tiles]
  (let [original-transform (.getTransform graphics)
        zoom (:zoom screen-state)
        x-center (/ (screen-state :width) 2)
        y-center (/ (screen-state :height) 2)]
    (doto graphics
      (.setClip 0 0 (:width screen-state) (:height screen-state))
      (.translate (- x-center (int (* (:x screen-state) zoom)))
                  (- y-center (int (* (:y screen-state) zoom))))
      (paint-tiles available-tiles screen-state [512 512])
      enable-anti-aliasing
      (.setColor Color/YELLOW)
      (.fillOval -5 -5
                 10 10)
      (.setTransform original-transform)
      (.setColor Color/YELLOW)
      (.drawString (str screen-state)
                   (int 0)
                   (int (- (:height screen-state) 10))))))
  

;; USER INPUT HANDLING

(defn display-follow [panel reference]
  (add-watch reference "display"
             (fn [_ _ _ _]
                (.post display-updater #(.repaint panel)))))

;; key binding

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

(defn bind-keys
  [component input-keys action-fn global?]
  (dorun (map #(bind-key component % action-fn global?) input-keys)))

(defn bind-window-keys
  [window input-keys action-fn]
  (bind-keys (.getContentPane window) input-keys action-fn true))

;; full screen

(defn- default-screen-device [] ; borrowed from see-saw
  (->
    (java.awt.GraphicsEnvironment/getLocalGraphicsEnvironment)
    .getDefaultScreenDevice))

(defn full-screen!
  "Make the given window/frame full-screen. Pass nil to return all windows
to normal size."
  ([^java.awt.GraphicsDevice device window]
    (if window
      (when (not= (.getFullScreenWindow device) window)
        (.dispose window)
        (.setUndecorated window true)
        (.setFullScreenWindow device window)
        (.show window))
      (when-let [window (.getFullScreenWindow device)]
        (.dispose window)
        (.setFullScreenWindow device nil)
        (.setUndecorated window false)
        (.show window)))
    window)
  ([window]
    (full-screen! (default-screen-device) window)))

(defn setup-fullscreen [window]
  (bind-window-keys window ["F"] #(full-screen! window))
  (bind-window-keys window ["ESCAPE"] #(full-screen! nil)))

;; positional controls

(defn pan! [position-atom axis distance]
  (let [zoom (@position-atom :zoom)]
    (swap! position-atom update-in [axis]
           - (/ distance zoom))))

(defn handle-drags [component position-atom]
  (let [drag-origin (atom nil)
        mouse-adapter
        (proxy [MouseAdapter] []
          (mousePressed [e]
                        (reset! drag-origin {:x (.getX e) :y (.getY e)}))
          (mouseReleased [e]
                         (reset! drag-origin nil))
          (mouseDragged [e]
                        (let [zoom (@position-atom :zoom)
                              x (.getX e) y (.getY e)]
                          (pan! position-atom :x (- x (:x @drag-origin)))
                          (pan! position-atom :y (- y (:y @drag-origin)))
                          (reset! drag-origin {:x x :y y}))))]
    (doto component
      (.addMouseListener mouse-adapter)
      (.addMouseMotionListener mouse-adapter))
    position-atom))

(defn handle-arrow-pan [component position-atom]
  (let [binder (fn [key axis step]
                 (bind-key component key
                           #(pan! position-atom axis step) true))]
    (binder "UP" :y 50)
    (binder "DOWN" :y -50)
    (binder "RIGHT" :x -50)
    (binder "LEFT" :x 50)))

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

(defn handle-zoom [window zoom-atom]
  (bind-window-keys window ["ADD" "CLOSE_BRACKET"]
                   (fn [] (swap! zoom-atom update-in [:zoom]
                                 #(min MAX-ZOOM (* % 2)))))
  (bind-window-keys window ["SUBTRACT" "OPEN_BRACKET"]
                   (fn [] (swap! zoom-atom update-in [:zoom]
                                 #(max MIN-ZOOM (/ % 2))))))

(defn watch-keys [window key-atom]
  (let [key-adapter (proxy [KeyAdapter] []
                      (keyPressed [e]
                                  (swap! key-atom update-in [:keys] conj
                                         (KeyEvent/getKeyText (.getKeyCode e))))
                      (keyReleased [e]
                                   (swap! key-atom update-in [:keys] disj
                                          (KeyEvent/getKeyText (.getKeyCode e)))))]
    (doseq [component (descendants window)]
      (.addKeyListener component key-adapter))))

;; MAIN WINDOW AND PANEL

(defn main-panel [screen-state available-tiles]
  (doto
    (proxy [JPanel] []
      (paintComponent [^Graphics graphics]
        (proxy-super paintComponent graphics)
        (paint-screen graphics @screen-state @available-tiles)))
    (.setBackground Color/BLACK)))
    
(defn main-frame []
  (doto (JFrame. "Slide Explorer II")
    .show
    (.setBounds 10 10 500 500)))

(defn show [available-tiles]
  (let [screen-state (atom (sorted-map :x 0 :y 0 :z 0 :zoom 1 :keys (sorted-set)))
        panel (main-panel screen-state available-tiles)
        frame (main-frame)]
    (def at available-tiles)
    (def ss screen-state)
    (def f frame)
    (def pnl panel)
    (.add (.getContentPane frame) panel)
    (setup-fullscreen frame)
    (handle-drags panel screen-state)
    (handle-arrow-pan panel screen-state)
    (handle-wheel panel screen-state)
    (handle-resize panel screen-state)
    (handle-zoom frame screen-state)
    (watch-keys frame screen-state)
    (display-follow panel screen-state)
    (display-follow panel available-tiles)
    frame))

