(ns slide-explorer.view
  (:import (javax.swing JFrame JPanel JSplitPane)
           (java.awt AlphaComposite BasicStroke Color Graphics
                     Graphics2D Rectangle RenderingHints Window)
           (java.awt.event ComponentAdapter KeyEvent KeyAdapter
                            WindowAdapter)
           (ij.process ByteProcessor ImageProcessor))
  (:require 
    [slide-explorer.flatfield :as flatfield]
    [slide-explorer.reactive :as reactive]
    [slide-explorer.tile-cache :as tile-cache]
    [slide-explorer.tiles :as tiles]
    [slide-explorer.canvas :as canvas]
    [slide-explorer.scale-bar :as scale-bar]
    [slide-explorer.image :as image]
    [slide-explorer.user-controls :as user-controls]
    [slide-explorer.paint :as paint]
    [slide-explorer.utils :as utils]
    [slide-explorer.widgets :as widgets]
    [clojure.core.memoize :as memo]))

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


;; CONTRAST

(defn apply-contrast
  "Setup contrast values by using the min and max of a given tile."
  [screen-state [tile-index tile-proc]]
  (update-in screen-state
             [:channels (:nc tile-index)]
             merge
             (image/intensity-range tile-proc)))

;; OVERLAY

(def overlay-memo (memo/memo-lru image/overlay 100))

(def flatfield-memo (memo/memo-lru flatfield/correct-indexed-image 100))

(defn display-tile
  "Loads the set of tiles specified by tile-indices from 
   memory-tiles-atom, and combines them according to channels-map,
   returning a single display tile as a BufferedImage."
  [memory-tiles-atom tile-indices channels-map]
  (let [corrections (flatfield/get-flatfield-corrections memory-tiles-atom)
        channel-names (keys channels-map)
        flattened-tiles (for [channel-name channel-names]
                          (let [tile-index (assoc tile-indices :nc channel-name)
                                raw-tile (tile-cache/load-tile memory-tiles-atom tile-index)]
                            ;(println tile-index)
                            (flatfield-memo tile-index raw-tile corrections)))
        lut-maps (map channels-map channel-names)]
    ;(println (count flattened-tiles) lut-maps)
    (overlay-memo flattened-tiles lut-maps)))

;; PAINTING
 
(defn paint-tiles [^Graphics2D g display-tiles-atom screen-state]
  (doseq [tile-index (visible-tile-indices screen-state :overlay)]
    (when-let [image (tile-cache/get-tile
                       display-tiles-atom
                       tile-index)]
      (let [[x y] (tiles/tile-to-pixels
                    [(:nx tile-index) (:ny tile-index)]
                    (screen-state :tile-dimensions) 1)]
        (paint/draw-image g image x y)
        ))))

(defn paint-position
  "Paints a rectangle outlining the camera's field of view at a
   given position (x,y). The rectangle contains a stroke of the
   given color, edged by a black background for better visibility."
  [^Graphics2D graphics
   {[w h] :tile-dimensions
    :keys [zoom] :as screen-state}
   x y color]
  (when (and x y w h color)
    (let [rect {:type :rect :l (inc (* zoom x)) :t (inc (* zoom y))
                :w (* zoom w) :h (* zoom h)}]
      (canvas/draw graphics
                   [(assoc rect :color :black :stroke {:width 4})
                    (assoc rect :color color :stroke {:width 3})]))))

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

(defn scale-graphics
  "Rescale subsequent drawing in a graphics object."
  [graphics scale]
  (when (not= scale 1.0)
    (.scale graphics scale scale)))

(defn paint-screen
  "Paints a slide explorer screen, with tiles, scale bar, and when appropriate,
   current stage position and positions from XY-list."
  [graphics screen-state display-tiles-atom]
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
      (scale-graphics scale)
      (paint-tiles display-tiles-atom screen-state)
      (paint-position-list screen-state)
      (paint-stage-position screen-state)
      (.setTransform original-transform)
      paint/enable-anti-aliasing
      (canvas/draw (when-let [pixel-size (:pixel-size-um screen-state)]
                     (bar-widget-memo (:height screen-state)
                                      (/ pixel-size zoom scale)))))))

;; Loading visible tiles

(defn load-display-tiles
  "Creates display tiles needed for drawing."
  [screen-state-atom memory-tile-atom display-tiles-atom]
  (doseq [tile (needed-tile-indices @screen-state-atom :overlay)]
    (tile-cache/add-tile display-tiles-atom
                         tile
                         (display-tile memory-tile-atom tile
                                           (:channels @screen-state-atom)))))

(defn load-visible-tiles
  "Runs loads dislpay tiles needed for rendering whenever
   screen-state-atom changes."
  [screen-state-atom
   memory-tile-atom
   display-tiles-atom]
  (let [load-display-tiles (fn [_ _]
                       (load-display-tiles
                         screen-state-atom
                         memory-tile-atom
                         display-tiles-atom))
        agent (agent {})]
    (def agent1 agent)
    (reactive/handle-update
      screen-state-atom
      load-display-tiles
      agent)
    (tile-cache/add-tile-listener!
      memory-tile-atom
      #(reactive/send-off-update agent load-display-tiles nil))))
  
;; MAIN WINDOW AND PANEL

(defn paint-screen-buffered [graphics screen-state-atom display-tiles-atom]
  ;(paint-screen graphics @screen-state-atom display-tiles-atom)
  (paint/paint-buffered graphics
                       #(paint-screen %
                                      @screen-state-atom
                                      display-tiles-atom)))

(defn create-panel [screen-state-atom display-tiles-atom]
  (doto
    (proxy [JPanel] []
      (paintComponent [^Graphics graphics]
        (proxy-super paintComponent graphics)
        (paint-screen-buffered graphics screen-state-atom display-tiles-atom)))
    (.setBackground Color/BLACK)
))
    
(defn main-frame
  "Creates an empty JFrame."
  [filename]
  (doto (JFrame. (str "Slide Explorer II: " filename))
    (.setBounds 10 10 500 500)
    (.setDefaultCloseOperation JFrame/DISPOSE_ON_CLOSE)
    .show))
    
(def default-settings
  (sorted-map :x 0 :y 0 :z 0 :zoom 1 :scale 1
              :width 100 :height 10
              :keys (sorted-set)
              :channels (sorted-map)
              :positions #{}))

(defn view
  "Creates a view panel that obeys the settings in screen-state."
  [memory-tile-atom settings]
  (let [screen-state-atom (atom (merge default-settings
                                  settings))
        display-tiles-atom (tile-cache/create-tile-cache 100)
        panel (create-panel screen-state-atom display-tiles-atom)]
    (load-visible-tiles screen-state-atom
                        memory-tile-atom
                        display-tiles-atom)
    (paint/repaint-on-change panel [display-tiles-atom screen-state-atom])
    (alter-meta! screen-state-atom assoc ::panel panel)
    screen-state-atom))

(defn panel [screen-state-atom]
  (::panel (meta screen-state-atom)))

(defn create-split-pane
  "Creates a JSplitPane in a parent component with left and right child components."
  [parent left-panel right-panel]
  (.add parent
        (doto (JSplitPane. JSplitPane/HORIZONTAL_SPLIT true
                           left-panel right-panel)
          (.setBorder nil)
          (.setResizeWeight 0.5)
          (.setDividerLocation 0.7))))

(defn show
  "Show the two-view Slide Explorer window."
  [memory-tile-atom settings]
  (let [frame (main-frame (.getAbsolutePath (tile-cache/tile-dir memory-tile-atom)))
        screen-state-atom (view memory-tile-atom settings)
        screen-state-atom2 (view memory-tile-atom settings)
        panel1 (panel screen-state-atom)
        panel2 (panel screen-state-atom2)
        split-pane (create-split-pane (.getContentPane frame) panel1 panel2)
        widgets {:frame frame
                 :left-panel panel1
                 :right-panel panel2
                 :split-pane split-pane
                 :content-pane (.getContentPane frame)}]     
    (widgets/setup-fullscreen frame)
    (user-controls/make-view-controllable widgets screen-state-atom)
    (user-controls/handle-resize panel2 screen-state-atom2)
    (user-controls/handle-1x-view screen-state-atom screen-state-atom2)
    (user-controls/handle-window-closing frame screen-state-atom screen-state-atom2 memory-tile-atom)
    (.show frame)
    (when @utils/testing
      (def w widgets)
      (def ss screen-state-atom)
      (def ss2 screen-state-atom2)
      (def mt memory-tile-atom))
    screen-state-atom))

(defn ae1 []
  (.printStackTrace (agent-error agent1)))