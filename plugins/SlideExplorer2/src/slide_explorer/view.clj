(ns slide-explorer.view
  (:import (javax.swing JFrame JPanel JSplitPane)
           (java.awt AlphaComposite Color Graphics Graphics2D Rectangle RenderingHints Window)
           (java.awt.event ComponentAdapter KeyEvent KeyAdapter
                            WindowAdapter)
           (ij.process ByteProcessor ImageProcessor))
  (:require [slide-explorer.reactive :as reactive]
            [slide-explorer.tile-cache :as tile-cache]
            [clojure.core.memoize :as memo])
  (:use [org.micromanager.mm :only (edt)]
        [slide-explorer.paint :only (enable-anti-aliasing repaint
                                     draw-image repaint-on-change)]
        [slide-explorer.tiles :only (center-tile floor-int)]
        [slide-explorer.image :only (crop insert-half-tile overlay intensity-range)]
        [slide-explorer.user-controls :only (make-view-controllable
                                              handle-resize
                                              setup-fullscreen)]))

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

;; TILE <--> PIXELS

(defn tile-to-pixels [[nx ny] [tile-width tile-height] tile-zoom]
  [(int (* tile-zoom nx tile-width))
   (int (* tile-zoom ny tile-height))])

(defn tile-in-pixel-rectangle?
  [[nx ny] rectangle [tile-width tile-height]]
  (let [nl (floor-int (/ (.x rectangle) tile-width))
        nr (floor-int (/ (+ -1 (.getWidth rectangle) (.x rectangle)) tile-width))
        nt (floor-int (/ (.y rectangle) tile-height))
        nb (floor-int (/ (+ -1 (.getHeight rectangle) (.y rectangle)) tile-height))]
    (and (<= nl nx nr)
         (<= nt ny nb))))   

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

(defn screen-rectangle
  "Returns a rectangle centered at x,y."
  [{:keys [x y width height zoom]}]
  (Rectangle. (- (* x zoom) (/ width 2))
              (- (* y zoom) (/ height 2))
              width
              height))

;; TILING

(defn child-index
  "Converts an x,y index to one in a child (1/2x zoom)."
  [n]
  (floor-int (/ n 2)))

(defn child-indices [indices]
  (-> indices
     (update-in [:nx] child-index)
     (update-in [:ny] child-index)
     (update-in [:zoom] / 2)))

(defn propagate-tile [tile-map-atom child parent]
  (let [child-tile (.get (tile-cache/load-tile tile-map-atom child))
        parent-tile (.get (tile-cache/load-tile tile-map-atom parent))
        new-child-tile (insert-half-tile
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
             (intensity-range tile-proc)))

;; OVERLAY

(def overlay-memo (memo/memo-lru overlay 100))

(defn multi-color-tile [memory-tiles-atom tile-indices channels-map]
  (let [channel-names (keys channels-map)
        procs (for [chan channel-names]
                (let [tile-index (assoc tile-indices :nc chan)]
                  (tile-cache/get-tile memory-tiles-atom tile-index false)))
        lut-maps (map channels-map channel-names)]
    (overlay-memo procs lut-maps)))

;; PAINTING

(defn absolute-mouse-position [screen-state]
  (let [{:keys [x y mouse zoom width height]} screen-state]
    (when mouse
      (let [mouse-x-centered (- (mouse :x) (/ width 2))
            mouse-y-centered (- (mouse :y) (/ height 2))]
        {:x (long (+ x (/ mouse-x-centered zoom)))
         :y (long (+ y (/ mouse-y-centered zoom)))}))))

(defn draw-test-tile [g x y]
  (doto g
    (.setColor Color/YELLOW)
    (.drawRect (+ 2 x) ( + y 2) 508 508)))

(defn paint-tiles [^Graphics2D g overlay-tiles-atom screen-state [tile-width tile-height]]
  (let [pixel-rect (.getClipBounds g)]
    (doseq [[nx ny] (tiles-in-pixel-rectangle pixel-rect
                                              [tile-width tile-height])]
      (let [tile-index {:nc :overlay
                        :zoom (screen-state :zoom)
                        :nx nx :ny ny :nt 0
                        :nz (screen-state :z)}]
        (let [[x y] (tile-to-pixels [nx ny] [tile-width tile-height] 1)]
          ;  (draw-test-tile g x y)
          )
        (when-let [image (tile-cache/get-tile
                           overlay-tiles-atom
                           tile-index
                           false)]
          (let [[x y] (tile-to-pixels [nx ny] [tile-width tile-height] 1)]
            (draw-image g image x y)
            ))))))
	
(defn paint-screen [graphics screen-state overlay-tiles-atom]
  (let [original-transform (.getTransform graphics)
        zoom (:zoom screen-state)
        x-center (/ (screen-state :width) 2)
        y-center (/ (screen-state :height) 2)
        [tile-width tile-height] [512 512]]
    ;(.printStackTrace (Throwable.))
    (doto graphics
      (.setClip 0 0 (:width screen-state) (:height screen-state))
      (.translate (- x-center (int (* (:x screen-state) zoom)))
                  (- y-center (int (* (:y screen-state) zoom))))
      (paint-tiles overlay-tiles-atom screen-state [tile-width tile-height])
      enable-anti-aliasing
      (.setTransform original-transform)
      (.setColor Color/WHITE)
      (.drawString (str (select-keys screen-state [:mouse :x :y :zoom])) 10 20)
      (.drawString (str (absolute-mouse-position screen-state)) 10 40))))

;; Loading visible tiles

(defn overlay-loader
  [screen-state-atom memory-tile-atom overlay-tiles-atom]
  (let [visible-tile-positions (tiles-in-pixel-rectangle
                                 (screen-rectangle @screen-state-atom)
                                 [512 512])]
    (doseq [[nx ny] visible-tile-positions]
      (let [tile {:nx nx
                  :ny ny
                  :zoom (@screen-state-atom :zoom)
                  :nc :overlay
                  :nz (@screen-state-atom :z)
                  :nt 0}]
          (tile-cache/add-tile overlay-tiles-atom
                               tile
                               (multi-color-tile memory-tile-atom tile
                                                 (:channels @screen-state-atom)))))))

(defn visible-loader
  "Loads tiles needed for drawing."
  [screen-state-atom memory-tile-atom acquired-images]
  (let [visible-tile-positions (tiles-in-pixel-rectangle
                                 (screen-rectangle @screen-state-atom)
                                 [512 512])]
    (doseq [[nx ny] visible-tile-positions
            channel (keys (:channels @screen-state-atom))]
      (let [tile {:nx nx
                  :ny ny
                  :zoom (@screen-state-atom :zoom)
                  :nc channel
                  :nz (@screen-state-atom :z)
                  :nt 0}]
        (tile-cache/load-tile memory-tile-atom tile)))))

(defn load-visible-only
  "Runs visible-loader whenever screen-state-atom changes."
  [screen-state-atom memory-tile-atom
   overlay-tiles-atom acquired-images]
  (let [react-mem-fn (fn [_ _] (visible-loader screen-state-atom memory-tile-atom
                                           acquired-images))
        react-vis-fn (fn [_ _] (overlay-loader screen-state-atom memory-tile-atom
                                               overlay-tiles-atom))
        agent (agent {})]
    (def agent1 agent)
    (reactive/handle-update
      memory-tile-atom
      react-vis-fn
      agent)
    (reactive/handle-update
      screen-state-atom
      react-mem-fn
      agent)
    (reactive/handle-update
      acquired-images
      react-mem-fn
      agent)
    ))
  
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
    .show
    (.setBounds 10 10 500 500)))
    
(defn view-panel [memory-tiles acquired-images]
  (let [screen-state (atom (sorted-map :x 0 :y 0 :z 0 :zoom 1
                                       :width 100 :height 10
                                       :keys (sorted-set)
                                       :channels (sorted-map))
                                       :update 0)
        overlay-tiles (tile-cache/create-tile-cache 100)
        panel (main-panel screen-state overlay-tiles)]
    ;(println overlay-tiles)
    (load-visible-only screen-state memory-tiles
                       overlay-tiles acquired-images)
    (repaint-on-change panel [screen-state memory-tiles overlay-tiles])
    ;(set-contrast-when-ready screen-state memory-tiles)
    [panel screen-state]))

(defn set-position! [screen-state-atom position-map]
  (swap! screen-state-atom
         merge position-map))

(defn show-where-pointing! [pointing-screen-atom showing-screen-atom]
  ;(println "swp")
  (set-position! showing-screen-atom
                 (absolute-mouse-position @pointing-screen-atom)))

(defn handle-point-and-show [pointing-screen-atom showing-screen-atom]
  (reactive/handle-update
    pointing-screen-atom
    (fn [_ _]
      (show-where-pointing!
        pointing-screen-atom showing-screen-atom))))

(defn show [dir acquired-images]
  (let [memory-tiles (tile-cache/create-tile-cache 100 dir)
        memory-tiles2 (tile-cache/create-tile-cache 100 dir)
        frame (main-frame)
        [panel screen-state] (view-panel memory-tiles acquired-images)
        [panel2 screen-state2] (view-panel memory-tiles2 acquired-images)
        split-pane (JSplitPane. JSplitPane/HORIZONTAL_SPLIT true panel panel2)]
    (doto split-pane
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
    (setup-fullscreen frame)
    (make-view-controllable panel screen-state)
    (handle-resize panel2 screen-state2)
    (handle-point-and-show screen-state screen-state2)
    ;(make-view-controllable panel2 screen-state2)
    ;(handle-open frame)
    [screen-state memory-tiles]))


;; testing

(defn copy-contrast []
  (swap! ss2 assoc :channels (:channels @ss)))

(defn big-region-contrast []
  (swap! ss assoc
         :channels {"Default" {:min 200 :max 800 :gamma 1.0 :color Color/WHITE}})
  (swap! ss2 assoc :channels (:channels @ss)))



