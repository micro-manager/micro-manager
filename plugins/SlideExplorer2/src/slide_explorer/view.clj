(ns slide-explorer.view
  (:import (javax.swing AbstractAction JComponent JFrame JPanel JLabel JTextArea KeyStroke)
           (java.awt AlphaComposite Color Graphics Graphics2D Rectangle RenderingHints Window)
           (java.util UUID)
           (java.awt.event ComponentAdapter KeyEvent KeyAdapter
                           MouseAdapter WindowAdapter)
           (ij.process ByteProcessor ImageProcessor))
  (:require [clojure.pprint :as pprint]
            [slide-explorer.disk :as disk]
            [slide-explorer.reactive :as reactive]
            [slide-explorer.cache :as cache]
            [clojure.core.memoize :as memo])
  (:use [org.micromanager.mm :only (edt)]
        [slide-explorer.paint :only (enable-anti-aliasing repaint repaint-on-change)]
        [slide-explorer.tiles :only (center-tile floor-int)]
        [slide-explorer.image :only (crop merge-and-scale overlay)]))

; Order of operations:
;  Stitch/crop
;  Flatten fields
;  Max intensity projection (z)
;  Rescale
;  Color Overlay

(def MIN-ZOOM 1/256)

(def MAX-ZOOM 1)

;; TESTING UTILITIES

(defn reference-viewer
  "Creates a small window that shows the value of a reference
   and upates as that value changes."
  [reference key]
  (let [frame (JFrame. key)
        label (JLabel.)
        update-fn #(edt (.setText label (str "<html><pre>" 
                                             (with-out-str (pprint/pprint %)
                                                       ) "</pre></html>")))]
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
  [{:keys [x y width height zoom]}]
  (Rectangle. (- (* x zoom) (/ width 2))
              (- (* y zoom) (/ height 2))
              width
              height))

;; TILING

(def q (agent nil))

(defn cache-assoc [m k v]
  (-> m (dissoc k)
        (assoc k v)))

(defn evict-oldest
  "Takes an atom containing a map and adds a watch such that whenever an item is added,
   the oldest item is removed to keep the number of items less than or equal to limit."
  [atom limit]
  (let [tick (ref 0)
        priority (ref {})]
    (reactive/handle-added-items
      atom
      (fn react [[key val]]
        (when-let [oldies
                   (dosync
                     (alter tick inc)
                     (alter priority assoc key @tick)
                     (loop [oldies nil]
                       (if (> (count @priority) limit)
                         (let [oldest (apply min-key @priority (keys @priority))]
                           (alter priority dissoc oldest)
                           (recur (conj oldies oldest)))
                         oldies)))]
          (apply swap! atom dissoc oldies))))))

(defn propagate-tile [tile-map-atom {:keys [zoom nx ny nz nt nc] :as indices}]
  ;(println indices)
  (let [nx- (* 2 nx)
        ny- (* 2 ny)
        nx+ (inc nx-)
        ny+ (inc ny-)
        zoom-parent (* zoom 2)
        get-parent-tile (fn [[nx ny]]
                          (let [dir (disk/tile-dir tile-map-atom)
                                tile-index (assoc indices :zoom zoom-parent :nx nx :ny ny)]
                            (or (get @tile-map-atom tile-index) 
                                (disk/read-tile dir tile-index)
                                )))
        abcd (map get-parent-tile [[nx- ny-]
                                   [nx+ ny-]
                                   [nx- ny+]
                                   [nx+ ny+]])]
    (disk/add-tile tile-map-atom indices (apply merge-and-scale abcd))))

(defn child-index [n]
  (floor-int (/ n 2)))

(defn child-indices [indices]
  (-> indices
     (update-in [:nx] child-index)
     (update-in [:ny] child-index)
     (update-in [:zoom] / 2)))

(defn tiles-for-updating [tile-index-coll]
  (->> tile-index-coll
       (map child-indices)
       set
       (sort-by #(- (% :zoom)))))

(defn add-to-memory-tiles [tile-map-atom indices tile]
  ;(println "asdf")
  (let [full-indices (assoc indices :zoom 1)]
    (disk/add-tile tile-map-atom full-indices tile)
    (loop [new-indices (child-indices full-indices)]
      (when (<= MIN-ZOOM (:zoom new-indices))
        (propagate-tile tile-map-atom new-indices)
        (recur (child-indices new-indices))))))

;; OVERLAY

(def overlay-memo (memo/memo-lru overlay 300))

(defn multi-color-tile [memory-tiles-atom tile-indices channels-map]
  #_(clojure.pprint/pprint channels-map)
  (let [channel-names (keys channels-map)
        procs (for [chan channel-names]
                (let [tile-index (assoc tile-indices :nc chan)]
                  (swap! memory-tiles-atom cache/hit-item tile-index)
                  (get @memory-tiles-atom tile-index)))
        lut-maps (map channels-map channel-names)]
    #_(println procs lut-maps)
    #_(println (map #(.hashCode %) procs)
              (map #(.hashCode %) lut-maps))
    (overlay-memo procs lut-maps)))

;; PAINTING

(defn draw-image [^Graphics2D g image x y]
  (.drawImage g image x y nil))

(defn paint-tiles [^Graphics2D g overlay-tiles-atom screen-state [tile-width tile-height]]
  (let [pixel-rect (.getClipBounds g)]
    (doseq [[nx ny] (tiles-in-pixel-rectangle pixel-rect
                                              [tile-width tile-height])]
      (let [tile-index {:nc :overlay
                         :zoom (screen-state :zoom)
                         :nx nx :ny ny :nt 0
                         :nz (screen-state :z)}]
      (when-let [image (get
                         @overlay-tiles-atom
                         tile-index)]
        (swap! overlay-tiles-atom cache/hit-item tile-index)
        (let [[x y] (tile-to-pixels [nx ny] [tile-width tile-height] 1)]
          (draw-image g image x y)))))))
	

(defn paint-screen [graphics screen-state overlay-tiles-atom]
  (let [original-transform (.getTransform graphics)
        zoom (:zoom screen-state)
        x-center (/ (screen-state :width) 2)
        y-center (/ (screen-state :height) 2)
        [tile-width tile-height] [512 512]]
    (doto graphics
      (.setClip 0 0 (:width screen-state) (:height screen-state))
      (.translate (- x-center (int (* (:x screen-state) zoom)))
                  (- y-center (int (* (:y screen-state) zoom))))
      (paint-tiles overlay-tiles-atom screen-state [tile-width tile-height])
      enable-anti-aliasing
      ;(.setColor (Color. 0x0CB397))
      ;(.fillOval -5 -5
      ;           10 10)
      )
    (when false 
      (let [pixel-rect (pixel-rectangle screen-state)
            visible-tiles (tiles-in-pixel-rectangle pixel-rect
                                                    [tile-width tile-height])
            ;center-tile (center-tile [(:x screen-state) (:y screen-state)]
            ;                         [tile-width tile-height])
            ;sorted-tiles (time (group-tiles-by-ring center-tile visible-tiles))
            ]
        (when true
        (doto graphics
          (.setTransform original-transform)
          (.setColor Color/GREEN)
          (.drawString (pr-str pixel-rect) 10 20)
          ;(.drawString (pr-str visible-tiles) 10 40)
          ;(.drawString (pr-str center-tile) 10 60)
          ;(.drawString (pr-str sorted-tiles) 10 80)
          (.drawString (str (select-keys screen-state [:x :y :z :zoom :keys :width :height]))
                     (int 10)
                     (int (- (screen-state :height) 12)))))))))

;; Loading visible tiles

(defn visible-loader
  "Loads tiles needed for drawing."
  [screen-state-atom memory-tile-atom overlay-tiles-atom acquired-images]
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
        (disk/load-tile memory-tile-atom tile)
        (swap! overlay-tiles-atom
               cache/add-item 
               (assoc tile :nc :overlay)
               (multi-color-tile memory-tile-atom tile
                                 (:channels @screen-state-atom)))))))

(defn load-visible-only
  "Runs visible-loader whenever screen-state-atom changes."
  [screen-state-atom memory-tile-atom
   overlay-tiles-atom acquired-images]
  (let [react-fn (fn [_ _] (visible-loader screen-state-atom memory-tile-atom
                                           overlay-tiles-atom acquired-images))
        agent (agent {})]
    (def agent1 agent)
    (reactive/handle-update
      screen-state-atom
      react-fn
      agent)
    (reactive/handle-update
      acquired-images
      react-fn
      agent)))
  

;; USER INPUT HANDLING

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

;; other user controls

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

;; MAIN WINDOW AND PANEL

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

(defn show [memory-tiles acquired-images]
  (let [screen-state (atom (sorted-map :x 0 :y 0 :z 0 :zoom 1 :width 100 :height 10
                                       :keys (sorted-set)
                                       :channels (sorted-map))
                                       :update 0)
        ;overlay-tiles (atom {})
        overlay-tiles (atom (cache/empty-lru-map 300))
        panel (main-panel screen-state overlay-tiles)
        frame (main-frame)
        mouse-position (atom nil)]
   ; (edt (println (str @overlay-tiles)))
    (def mt memory-tiles)
    (def ss screen-state)
    (def mp mouse-position)
    (def f frame)
    (def pnl panel)
    (def ot overlay-tiles)
    (def ai acquired-images)
    (.add (.getContentPane frame) panel)
    (setup-fullscreen frame)
    ((juxt handle-drags handle-arrow-pan handle-wheel handle-resize)
      panel screen-state)
    ((juxt handle-zoom handle-dive watch-keys) frame screen-state)
    (load-visible-only screen-state memory-tiles
                       overlay-tiles acquired-images)
    (repaint-on-change panel screen-state)
    (repaint-on-change panel memory-tiles)
    (repaint-on-change panel overlay-tiles)
    (handle-pointing panel mouse-position)
    screen-state))

