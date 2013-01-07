(ns slide-explorer.view
  (:import (javax.swing JFrame JPanel JSplitPane)
           (java.awt AlphaComposite BasicStroke Color Graphics
                     Graphics2D Rectangle RenderingHints Window)
           (java.awt.event ComponentAdapter KeyEvent KeyAdapter
                            WindowAdapter)
           (ij.process ByteProcessor ImageProcessor))
  (:require [slide-explorer.reactive :as reactive]
            [slide-explorer.tile-cache :as tile-cache]
            [slide-explorer.tiles :as tiles]
            [slide-explorer.canvas :as canvas]
            [slide-explorer.scale-bar :as scale-bar]
            [slide-explorer.image :as image]
            [slide-explorer.user-controls :as user-controls]
            [slide-explorer.paint :as paint]
            [clojure.core.memoize :as memo]))

(def MIN-ZOOM 1/256)

(def MAX-ZOOM 1)

; Order of operations:
;  Stitch (not done)
;  Crop (not done)
;  Flatten fields (not done)
;  Max intensity projection (z) (not done)
;  Rescale (working)
;  Color Overlay (working)

;; TESTING UTILITIES

(defmacro timer [expr]
  `(let [ret# ~expr] ; (time ~expr)]
    ; (print '~expr)
    ; (println " -->" (pr-str ret#))
     ret#))

;; COORDINATES

(defn pixel-rectangle
  "Converts the screen state coordinates to visible camera pixel coordinates."
  [{:keys [x y width height zoom]}]
  (Rectangle. (- x (/ width 2 zoom))
              (- y (/ height 2 zoom))
              (/ width zoom)
              (/ height zoom)))

(defn screen-rectangle
  "Returns a rectangle centered at x,y."
  [{:keys [x y width height zoom]}]
  (Rectangle. (- (* x zoom) (/ width 2))
              (- (* y zoom) (/ height 2))
              width
              height))

(defn visible-tile-indices
  "Computes visible tile indices for a given channel index."
  [screen-state channel-index]
  (let [visible-tile-positions (tiles/tiles-in-pixel-rectangle
                                 (screen-rectangle screen-state)
                                 (:tile-dimensions screen-state))]
    (for [[nx ny] visible-tile-positions]
      {:nx nx :ny ny :zoom (:zoom screen-state)
       :nc channel-index :nz (screen-state :z) :nt 0})))

(defn needed-tile-indices
  "Computes tile indices that will be needed when a zoom event is finished,
   to allow pre-loading."
  [screen-state channel-index]
  (let [scale (:scale screen-state)]
    (concat (visible-tile-indices screen-state channel-index)
            (when (not= scale 1)
              ;(println scale)
              (let [factor (if (> scale 1) 2 1/2)
                    future-state (update-in screen-state [:zoom] * factor)]
                (visible-tile-indices future-state channel-index))))))

;; TILING

(defn child-index
  "Converts an x,y index to one in a child (1/2x zoom)."
  [n]
  (tiles/floor-int (/ n 2)))

(defn child-indices [indices]
  (-> indices
     (update-in [:nx] child-index)
     (update-in [:ny] child-index)
     (update-in [:zoom] / 2)))

(defn propagate-tile [tile-map-atom child parent]
  (let [child-tile (tile-cache/load-tile tile-map-atom child)
        parent-tile (tile-cache/load-tile tile-map-atom parent)
        new-child-tile (image/insert-half-tile
                         parent-tile
                         [(even? (:nx parent))
                          (even? (:ny parent))]
                         child-tile)]
    (tile-cache/add-tile tile-map-atom child new-child-tile)))

(defn add-to-memory-tiles [tile-map-atom indices tile]
  (let [full-indices (assoc indices :zoom 1)]
    (tile-cache/add-tile tile-map-atom full-indices tile)
    (loop [child (child-indices full-indices)
           parent full-indices]
      (when (<= MIN-ZOOM (:zoom child))
        (propagate-tile tile-map-atom child parent)
        (recur (child-indices child) child)))))

;; CONTRAST

(defn apply-contrast
  "Setup contrast values by using the min and max of a given tile."
  [screen-state [tile-index tile-proc]]
  (update-in screen-state
             [:channels (:nc tile-index)]
             merge
             (image/intensity-range tile-proc)))

(defn copy-settings [pointing-screen-atom showing-screen-atom]
  (swap! showing-screen-atom merge
         (select-keys @pointing-screen-atom
                      [:channels :tile-dimensions
                       :pixel-size-um :xy-stage-position
                       :positions])))

;; OVERLAY

(def overlay-memo (memo/memo-lru image/overlay 100))

(defn multi-color-tile [memory-tiles-atom tile-indices channels-map]
  (let [channel-names (keys channels-map)
        procs (for [chan channel-names]
                (let [tile-index (assoc tile-indices :nc chan)]
                  (tile-cache/load-tile memory-tiles-atom tile-index)))
        lut-maps (map channels-map channel-names)]
    (overlay-memo procs lut-maps)))

;; PAINTING

(comment
(defn draw-test-tile [g x y]
  (doto g
    (.setColor Color/YELLOW)
    (.drawRect (+ 2 x) ( + y 2) 508 508)))
;
(defn show-mouse-pos [graphics screen-state]
  (let [{:keys [x y]} (:mouse screen-state)]
    (when (and x y)
      (doto graphics
        (.setColor Color/BLUE)
        (.drawRect (- x 25) (- y 25) 50 50))))))
  
(defn paint-tiles [^Graphics2D g overlay-tiles-atom screen-state]
  (doseq [tile-index (visible-tile-indices screen-state :overlay)] 
    (when-let [image (tile-cache/get-tile
                       overlay-tiles-atom
                       tile-index)]
      (let [[x y] (tiles/tile-to-pixels
                    [(:nx tile-index) (:ny tile-index)]
                    (screen-state :tile-dimensions) 1)]
        (paint/draw-image g image x y)))))

(defn paint-position [^Graphics2D g screen-state x y color]
  (let [[w h] (:tile-dimensions screen-state)
        zoom (:zoom screen-state)
        scale (:scale screen-state)]
    (when (and x y w h color)
;      (.drawRect g (* zoom x) (* zoom y)
;                        (* zoom w) (* zoom h)))))
      (canvas/draw g
                   [:rect
                    {:l (inc (* zoom x)) :t (inc (* zoom y))
                     :w (* zoom w) :h (* zoom h)
                     :alpha 1
                     :stroke {:color color
                              :width (max 4.0 (* 16 zoom))}}]))))

(defn paint-stage-position [^Graphics2D g screen-state]
  (let [[x y] (:xy-stage-position screen-state)
        [w h] (:tile-dimensions screen-state)
        zoom (:zoom screen-state)]
    (paint-position g screen-state x y :yellow)))

(defn paint-position-list [^Graphics2D g screen-state]
  (let [[w h] (:tile-dimensions screen-state)
        zoom (:zoom screen-state)]
    (doseq [{:keys [x y]} (:positions screen-state)]
      (paint-position g screen-state x y :red))))

(def bar-widget-memo (memo/memo-lru scale-bar/bar-widget 500))

(defn paint-screen [graphics screen-state overlay-tiles-atom]
  (let [original-transform (.getTransform graphics)
        zoom (:zoom screen-state)
        scale (screen-state :scale 1.0)
        x-center (/ (screen-state :width) 2)
        y-center (/ (screen-state :height) 2)
        [tile-width tile-height] (:tile-dimensions screen-state)]
    (doto graphics
      (.setClip 0 0 (:width screen-state) (:height screen-state))
      (.translate (- x-center (int (* (:x screen-state) zoom scale)))
                  (- y-center (int (* (:y screen-state) zoom scale))))
      (.scale scale scale)
      (paint-tiles overlay-tiles-atom screen-state)
      (paint-position-list screen-state)
      (paint-stage-position screen-state)
      paint/enable-anti-aliasing
      (.setTransform original-transform)
      (.setColor Color/WHITE)
      (canvas/draw (when-let [pixel-size (:pixel-size-um screen-state)]
                     (bar-widget-memo (:height screen-state)
                                      (/ pixel-size zoom scale))))
      ;(show-mouse-pos screen-state)
      ;(.drawString (str (select-keys screen-state [:mouse :x :y :z :zoom])) 10 20)
      ;(.drawString (str (user-controls/absolute-mouse-position screen-state)) 10 40)
      )))

;; Loading visible tiles

(defn overlay-loader
  "Creates overlay tiles needed for drawing."
  [screen-state-atom memory-tile-atom overlay-tiles-atom]
  (doseq [tile (needed-tile-indices @screen-state-atom :overlay)]
    (tile-cache/add-tile overlay-tiles-atom
                         tile
                         (multi-color-tile memory-tile-atom tile
                                           (:channels @screen-state-atom)))))

(defn load-visible-only
  "Runs visible-loader whenever screen-state-atom changes."
  [screen-state-atom memory-tile-atom
   overlay-tiles-atom acquired-images]
  (let [load-overlay (fn [_ _]
                       (overlay-loader
                         screen-state-atom
                         memory-tile-atom
                         overlay-tiles-atom))
        agent (agent {})]
    (def agent1 agent)
    (reactive/handle-update
      screen-state-atom
      load-overlay
      agent)
    (reactive/handle-update
      acquired-images
      load-overlay
      agent)))
  
;; MAIN WINDOW AND PANEL

(defn control-panel []
  (doto
    (proxy [JPanel] []
      (paintComponent [^Graphics graphics]
        (proxy-super paintComponent graphics)))
    (.setBackground Color/BLACK)))  

(defn main-panel [screen-state overlay-tiles-atom]
  (doto
    (proxy [JPanel] []
      (paintComponent [^Graphics graphics]
        (proxy-super paintComponent graphics)
        (paint-screen graphics @screen-state overlay-tiles-atom)))
    (.setBackground Color/BLACK)))
    
(defn main-frame []
  (doto (JFrame. "Slide Explorer II")
    (.setBounds 10 10 500 500)
    .show))
    
(defn view-panel [memory-tiles acquired-images settings]
  (let [screen-state (atom (merge
                             (sorted-map :x 0 :y 0 :z 0 :zoom 1 :scale 1
                                         :pixel-size-um 0.3
                                       :width 100 :height 10
                                       :keys (sorted-set)
                                       :channels (sorted-map)
                                         :positions #{}
                                       )
                             settings))
        overlay-tiles (tile-cache/create-tile-cache 100)
        panel (main-panel screen-state overlay-tiles)]
    ;(println overlay-tiles)
    (load-visible-only screen-state memory-tiles
                       overlay-tiles acquired-images)
    (paint/repaint-on-change panel [overlay-tiles screen-state]); [memory-tiles])
    ;(set-contrast-when-ready screen-state memory-tiles)
    [panel screen-state]))

(defn set-position! [screen-state-atom x y]
  (swap! screen-state-atom assoc :x x :y y))

(defn show-position! [showing-screen-atom x y]
  (let [[w h] (:tile-dimensions @showing-screen-atom)]
    (when (and x y w h)
      (set-position! showing-screen-atom (+ x (/ w 2)) (+ y (/ h 2))))))

(defn show-where-pointing! [pointing-screen-atom showing-screen-atom] 
  (copy-settings pointing-screen-atom showing-screen-atom) 
  (let [{:keys [x y]} (user-controls/absolute-mouse-position
                        @pointing-screen-atom)]
    (show-position! showing-screen-atom x y)))

(defn show-stage-position! [main-screen-atom showing-screen-atom]
  (copy-settings main-screen-atom showing-screen-atom) 
  (let [main-screen @main-screen-atom
        [x y] (:xy-stage-position main-screen)]
    (show-position! showing-screen-atom x y)))

(defn handle-change-and-show
  [show-fn! value-to-check-fn
   main-screen-atom showing-screen-atom]
  (reactive/handle-update
    main-screen-atom
    (fn [old new]
      (when (not= (value-to-check-fn old)
                  (value-to-check-fn new))
        (show-fn!
          main-screen-atom showing-screen-atom)))))

(def handle-point-and-show 
  (partial handle-change-and-show
           show-where-pointing!
           user-controls/absolute-mouse-position))

(def handle-stage-move-and-show
  (partial handle-change-and-show
           show-stage-position!
           :xy-stage-position))

(defn show [dir acquired-images settings]
  (let [memory-tiles (tile-cache/create-tile-cache 100 dir)
        memory-tiles2 (tile-cache/create-tile-cache 100 dir)
        frame (main-frame)
        [panel screen-state] (view-panel memory-tiles acquired-images settings)
        [panel2 screen-state2] (view-panel memory-tiles2 acquired-images settings)
        split-pane (JSplitPane. JSplitPane/HORIZONTAL_SPLIT true panel panel2)]
    (doto split-pane
      (.setBorder nil)
      (.setResizeWeight 0.5)
      (.setDividerLocation 0.7))
    (def ss screen-state)
    (def ss2 screen-state2)
    (def pnl panel)
    (def mt memory-tiles)
    (def mt2 memory-tiles2)
    (def f frame)
    (def ai acquired-images)
    (println ss ss2 mt mt2)
    (.add (.getContentPane frame) split-pane)
    (user-controls/setup-fullscreen frame)
    (user-controls/make-view-controllable panel screen-state)
    (user-controls/handle-resize panel2 screen-state2)
    (handle-point-and-show screen-state screen-state2)
    (handle-stage-move-and-show screen-state screen-state2)
    (copy-settings screen-state screen-state2) ; should apply repeatedly
    ;(handle-open frame)
    (.show frame)
    [screen-state memory-tiles panel]))


;; testing

(defn big-region-contrast []
  (swap! ss assoc
         :channels {"Default" {:min 200 :max 800 :gamma 1.0 :color Color/WHITE}})
  (swap! ss2 assoc :channels (:channels @ss)))
