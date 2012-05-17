(ns slide-explorer.view
  (:import (javax.swing AbstractAction JComponent JFrame JPanel JLabel KeyStroke)
           (java.awt Color Graphics Graphics2D Rectangle RenderingHints Window)
           (java.util UUID)
           (java.awt.event ComponentAdapter KeyEvent KeyAdapter
                           MouseAdapter WindowAdapter)
           (org.micromanager.utils GUIUpdater))
  (:require [clojure.pprint :as pprint])
  (:use [org.micromanager.mm :only (edt)]
        [slide-explorer.paint :only (enable-anti-aliasing)]
        [slide-explorer.image :only (crop merge-and-scale overlay overlay-memo lut-object)]))

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
        label (JLabel.)
        update-fn #(edt (.setText label (.toString %)))]
    (.add (.getContentPane frame) label)
    (add-watch reference key
               (fn [_ _ _ new-state] (update-fn new-state)))
    (update-fn @reference)
    (doto frame
      (.addWindowListener
        (proxy [WindowAdapter] []
          (windowClosing [e]
                         (remove-watch reference key))))
      .show))
  reference)

(defmacro timer [expr]
  `(let [ret# ~expr] ; (time ~expr)]
    ; (print '~expr)
    ; (println " -->" (pr-str ret#))
     ret#))

;; GUI UTILITIES

(defn window-descendants
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

(defn pixel-rectangle
  "Converts the screen state coordinates to visible camera pixel coordinates."
  [{:keys [x y width height zoom]}]
  (Rectangle. (- x (/ width 2 zoom))
              (- y (/ height 2 zoom))
              (/ width zoom)
              (/ height zoom)))

(defn center-tile [[pixel-center-x pixel-center-y] [tile-width tile-height]]
  [(floor-int (/ pixel-center-x tile-width))
   (floor-int (/ pixel-center-y tile-height))])

(defn group-tiles-by-ring [[center-nx center-ny] tiles]
  (let [abs #(Math/abs %)
        centered (fn [[nx ny]] [(- nx center-nx) (- ny center-ny)])
        ring (fn [[nx ny]] (Math/max nx ny))]
    (group-by #(ring (map abs (centered %))) tiles)))


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

(defn multi-color-tile [available-tiles zoom tile-indices channels-map]
  (let [channel-names (keys channels-map)]
    (overlay-memo
      (for [chan channel-names]
        (get-in available-tiles [zoom (assoc tile-indices :nc chan)]))
      (for [chan channel-names]
        (get-in channels-map [chan :lut])))))

(defn paint-tiles [^Graphics2D g available-tiles screen-state [tile-width tile-height]]
  (let [pixel-rect (.getClipBounds g)]
    (doseq [[nx ny] (tiles-in-pixel-rectangle pixel-rect
                                              [tile-width tile-height])]
      (when-let [image (multi-color-tile available-tiles
                                         (screen-state :zoom)
                                         {:nx nx :ny ny :nt 0
                                          :nz (screen-state :z)}
                                         (:channels screen-state))]
        (let [[x y] (tile-to-pixels [nx ny] [tile-width tile-height] 1)]
          (.drawImage g image x y nil))))))

(defn paint-screen [graphics screen-state available-tiles]
  (let [original-transform (.getTransform graphics)
        zoom (:zoom screen-state)
        x-center (/ (screen-state :width) 2)
        y-center (/ (screen-state :height) 2)
        [tile-width tile-height] [512 512]]
    (doto graphics
      (.setClip 0 0 (:width screen-state) (:height screen-state))
      (.translate (- x-center (int (* (:x screen-state) zoom)))
                  (- y-center (int (* (:y screen-state) zoom))))
      (paint-tiles available-tiles screen-state [tile-width tile-height])
      enable-anti-aliasing
      ;(.setColor (Color. 0x0CB397))
      ;(.fillOval -5 -5
      ;           10 10)
      )
    (when false 
      (let [rect (.getClipBounds graphics)
            pixel-rect (pixel-rectangle screen-state)
            visible-tiles (tiles-in-pixel-rectangle pixel-rect [tile-width tile-height])
            center-tile (center-tile [(:x screen-state) (:y screen-state)] [tile-width tile-height])
            sorted-tiles (time (group-tiles-by-ring center-tile visible-tiles))
            ]
        (when true
        (doto graphics
          (.setTransform original-transform)
          (.setColor (Color. 0xECF2AA))
          (.drawString (pr-str visible-tiles) 10 20)
          (.drawString (pr-str pixel-rect) 10 40)
          (.drawString (pr-str center-tile) 10 60)
          (.drawString (pr-str sorted-tiles) 10 80)
          (.drawString (str (select-keys screen-state [:x :y :z :zoom :keys :width :height]))
                     (int 10)
                     (int (- (screen-state :height) 12)))))))))
  

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
                        (let [x (.getX e) y (.getY e)]
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

(defn handle-dive [window dive-atom]
  (bind-window-keys window ["COMMA"] #(swap! dive-atom update-in [:z] dec))
  (bind-window-keys window ["PERIOD"] #(swap! dive-atom update-in [:z] inc)))

(defn handle-zoom [window zoom-atom]
  (bind-window-keys window ["ADD" "CLOSE_BRACKET"]
                   (fn [] (swap! zoom-atom update-in [:zoom]
                                 #(min (* % 2) MAX-ZOOM))))
  (bind-window-keys window ["SUBTRACT" "OPEN_BRACKET"]
                   (fn [] (swap! zoom-atom update-in [:zoom]
                                 #(max (/ % 2) MIN-ZOOM)))))

(defn watch-keys [window key-atom]
  (let [key-adapter (proxy [KeyAdapter] []
                      (keyPressed [e]
                                  (swap! key-atom update-in [:keys] conj
                                         (KeyEvent/getKeyText (.getKeyCode e))))
                      (keyReleased [e]
                                   (swap! key-atom update-in [:keys] disj
                                          (KeyEvent/getKeyText (.getKeyCode e)))))]
    (doseq [component (window-descendants window)]
      (.addKeyListener component key-adapter))))

(defn handle-pointing [component pointing-atom]
  (.addMouseMotionListener component
                     (proxy [MouseAdapter] []
                       (mouseMoved [e]
                                   (swap! pointing-atom merge {:x (.getX e)
                                                               :y (.getY e)})))))

(defn add-channel [screen-state-atom name color min max gamma]
  (swap! update-in [:channels name] (lut-object color min max gamma)))

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
  (let [screen-state (atom (sorted-map :x 0 :y 0 :z 0 :zoom 1 :width 100 :height 10
                                       :keys (sorted-set)
                                       :channels (sorted-map)))
        panel (main-panel screen-state available-tiles)
        frame (main-frame)
        mouse-position (atom nil)]
    (def at available-tiles)
    (def ss screen-state)
    (def mp mouse-position)
    (def f frame)
    (def pnl panel)
    (.add (.getContentPane frame) panel)
    (setup-fullscreen frame)
    (handle-drags panel screen-state)
    (handle-arrow-pan panel screen-state)
    (handle-wheel panel screen-state)
    (handle-resize panel screen-state)
    (handle-zoom frame screen-state)
    (handle-dive frame screen-state)
    (watch-keys frame screen-state)
    (display-follow panel screen-state)
    (display-follow panel available-tiles)
    (handle-pointing panel mouse-position)
    screen-state))

