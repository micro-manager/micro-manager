(ns slide-explorer.user-controls
  (:import (java.awt.event ComponentAdapter KeyEvent KeyAdapter
                           MouseAdapter MouseEvent WindowAdapter
                           ActionListener)
           (java.awt Toolkit Window)
           (javax.swing SwingUtilities)
           (org.micromanager.utils JavaUtils))
  (:require [slide-explorer.reactive :as reactive]
            [slide-explorer.widgets :as widgets]))

(def MIN-ZOOM 1/256)

(def MAX-ZOOM 1)

(defn window-descendants
  "Returns a depth-first seq of all components contained by window."
  [window]
  (tree-seq (constantly true)
            #(.getComponents %)
            window))

;; other user controls

(defn pan! [position-atom axis distance]
  (let [{:keys [zoom scale]} @position-atom]
    (swap! position-atom update-in [axis]
           - (/ distance zoom scale))))

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

(def PAN-STEP-COUNT 10)
(def PAN-DISTANCE 50)

;TODO :: rewrite with a smoother algorithm (don't rely on key repeats)
(defn run-pan!
  [position-atom axis direction]
  (when-not (:panning @position-atom)
    (future
      (swap! position-atom assoc :panning true)
      (let [step-size (direction (/ PAN-DISTANCE PAN-STEP-COUNT))]
        (dotimes [_ PAN-STEP-COUNT]
          (pan! position-atom axis step-size)
          (Thread/sleep 5)))
      (swap! position-atom assoc :panning false))))  

(defn handle-arrow-pan [component position-atom]
  (let [binder (fn [key axis direction]
                 (widgets/bind-key component key
                                   #(run-pan! position-atom axis direction) true))]
    (binder "UP" :y +)
    (binder "DOWN" :y -)
    (binder "RIGHT" :x -)
    (binder "LEFT" :x +)))

(defn toggle-mode [screen-state]
  (assoc screen-state :mode
         (condp = (:mode screen-state)
           :explore :navigate
           :navigate :explore
              :navigate)))

(defn handle-mode-keys [panel screen-state-atom]
  (let [window (SwingUtilities/getWindowAncestor panel)]
    (widgets/bind-window-keys window [\ ] ; space bar
                      #(swap! screen-state-atom toggle-mode))))
                                

(defn handle-wheel [component z-atom]
  (let [last-move (atom 0)]
    (def lm last-move)
    (.addMouseWheelListener component
      (proxy [MouseAdapter] []
        (mouseWheelMoved [e]
          (let [t (System/currentTimeMillis)]
            (when (< 250 (- t @last-move))
              (reset! last-move t)
              (swap! z-atom update-in [:z]
                     (if (pos? (.getWheelRotation e)) inc dec))))))))
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
  (widgets/bind-window-keys window ["COMMA"] #(swap! dive-atom update-in [:z] dec))
  (widgets/bind-window-keys window ["PERIOD"] #(swap! dive-atom update-in [:z] inc)))

(def zoom-steps 25)

(defn run-zoom! 
  "Smoothly zoom :in or :out by one factor of 2, asynchronously."
  [zoom-atom direction {mx :x my :y}]
  (future
    (let [in? (= direction :in)
          {:keys [zoom scale x y]} @zoom-atom
          factor (if in? 2 1/2)
          scale-delta (/ 1. zoom-steps)
          dx (/ mx zoom 1)
          dy (/ my zoom 1)
          zoom? (if in?
                   (< zoom (- MAX-ZOOM 0.001))
                   (> zoom (+ MIN-ZOOM 0.001)))]
      (when (= 1 scale)
        (doseq [f (map #(/ % zoom-steps) (range 1 zoom-steps))]
          (swap! zoom-atom assoc
                 :scale (if zoom? (Math/pow factor f) 1)
                 :x (+ x (* f dx))
                 :y (+ y (* f dy)))
          (Thread/sleep 10))
        (let [old-zoom (@zoom-atom :zoom)]
          (swap! zoom-atom assoc
                 :scale 1
                 :zoom (if zoom? (* old-zoom factor) old-zoom)))))))

(defn handle-zoom [window zoom-atom]
  (widgets/bind-window-keys window ["ADD" "CLOSE_BRACKET" "EQUALS"]
    (fn [] (run-zoom! zoom-atom :in {:x 0 :y 0})))
  (widgets/bind-window-keys window ["SUBTRACT" "OPEN_BRACKET" "MINUS"]
    (fn [] (run-zoom! zoom-atom :out {:x 0 :y 0}))))
  
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

(defn apply-centered-mouse-position [screen-state screen-x screen-y]
  (let [{:keys [width height]} screen-state]
    (update-in screen-state [:mouse] assoc
               :x (- screen-x (/ width 2))
               :y (- screen-y (/ height 2)))))

(defn update-mouse-position [e screen-state-atom]
  (swap! screen-state-atom
         apply-centered-mouse-position
         (.getX e) (.getY e)))

(defn handle-click [panel event-predicate response-fn]
  (.addMouseListener panel
                     (proxy [MouseAdapter] []
                       (mouseClicked [e]
                                     (when (event-predicate e)
                                       (response-fn (.getX e) (.getY e)))))))

(defn menu-accelerator-down? [mouse-event]
  (pos? (bit-and (.. Toolkit getDefaultToolkit getMenuShortcutKeyMask)
                 (.getModifiers mouse-event))))

(defn handle-double-click [panel response-fn]
  (handle-click panel
                (fn [e] (and (= MouseEvent/BUTTON1 (.getButton e))
                             (not (or (.isAltDown e) (menu-accelerator-down? e)))
                             (= 2 (.getClickCount e))))
                response-fn))

(defn handle-alt-click [button panel response-fn]
  (handle-click panel
                (fn [e] (and (.isAltDown e)
                             (= (button {:left MouseEvent/BUTTON1
                                         :right MouseEvent/BUTTON3})
                                (.getButton e))))
                response-fn))

(defn handle-mouse-zoom [panel zoom-atom]
  (handle-alt-click :left panel
    (fn [_ _] (run-zoom! zoom-atom :in (:mouse @zoom-atom))))
  (handle-alt-click :right panel
    (fn [_ _] (run-zoom! zoom-atom :out (:mouse @zoom-atom)))))

(defn handle-control-click [panel response-fn]
  (handle-click panel
                (fn [e] (and (= MouseEvent/BUTTON1 (.getButton e))
                             (menu-accelerator-down? e)))
                response-fn))
                                     
(defn handle-pointing [component screen-state-atom]
  (.addMouseMotionListener component
    (proxy [MouseAdapter] []
      (mouseMoved [e] (update-mouse-position e screen-state-atom))
      (mouseDragged [e] (update-mouse-position e screen-state-atom)))))
                                   
(defn absolute-mouse-position [screen-state]
  (let [{:keys [x y z mouse zoom scale width height tile-dimensions]} screen-state]
    (when mouse
      (let [[w h] tile-dimensions]
        {:x (long (+ x (/ (mouse :x) zoom scale) (/ w -2)))
         :y (long (+ y (/ (mouse :y) zoom scale) (/ h -2)))
         :z z}))))

(defn handle-reset [window screen-state-atom]
  (widgets/bind-window-keys window ["shift R"]
               #(swap! screen-state-atom
                       assoc :x 0 :y 0 :z 0 :zoom 1)))

;; Primary and secondary screens

(defn copy-settings [pointing-screen-atom showing-screen-atom]
  (swap! showing-screen-atom merge
         (select-keys @pointing-screen-atom
                      [:channels :tile-dimensions
                       :pixel-size-um :xy-stage-position
                       :positions :z])))

(defn set-position! [screen-state-atom x y]
  (swap! screen-state-atom assoc :x x :y y))

(defn show-position! [showing-screen-atom x y]
  (let [[w h] (:tile-dimensions @showing-screen-atom)]
    (when (and x y w h)
      (set-position! showing-screen-atom (+ x (/ w 2)) (+ y (/ h 2))))))

(defn show-where-pointing! [pointing-screen-atom showing-screen-atom] 
  (let [{:keys [x y]} (absolute-mouse-position
                        @pointing-screen-atom)]
    (show-position! showing-screen-atom x y)))

(defn show-stage-position! [main-screen-atom showing-screen-atom]
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
           absolute-mouse-position))

(def handle-stage-move-and-show
  (partial handle-change-and-show
           show-stage-position!
           :xy-stage-position))

(def handle-display-change-and-show
  (partial handle-change-and-show
           copy-settings
           #(select-keys % [:positions :channels :xy-stage-position :z])))

(defn handle-1x-view
  "Take the region around the mouse pointer in the first screen,
   and show it in the second screen at 1x zoom."
  [screen-state-atom1 screen-state-atom2]
  (handle-point-and-show screen-state-atom1 screen-state-atom2)
  (handle-display-change-and-show screen-state-atom1 screen-state-atom2)
  ;(handle-stage-move-and-show screen-state screen-state2) ; deactivate; make this optional?
  (copy-settings screen-state-atom1 screen-state-atom2))

;; toggling split pane

(defn redraw-frame [frame]
  (doto (.getContentPane frame)
    .revalidate
    .repaint))  

(def divider-locations (atom {}))

(defn remember-divider-location! [split-pane]
  (swap! divider-locations assoc split-pane
         (.getDividerLocation split-pane)))

(defn restore-divider-location! [split-pane]
  (when-let [divider-loc (@divider-locations split-pane)]
    (.setDividerLocation split-pane divider-loc)))

(defn hide-1x-view [{:keys [frame content-pane split-pane left-panel right-panel]}]
  (doto split-pane
    remember-divider-location!
    (.remove left-panel)
    (.remove right-panel))
  (doto content-pane
    (.remove split-pane)
    (.add left-panel))
  (.setBounds right-panel 0 0 0 0) ; ensures redraw on restoration
  (redraw-frame frame))  

(defn show-1x-view [{:keys [frame content-pane split-pane left-panel right-panel]}]
  (doto content-pane
    (.remove left-panel)
    (.add split-pane))
  (doto split-pane
    (.setLeftComponent left-panel)
    (.setRightComponent right-panel)
    restore-divider-location!)
  (redraw-frame frame))

(defn toggle-1x-view [{:keys [split-pane] :as widgets}]
  (if (.getParent split-pane)
    (hide-1x-view widgets)
    (show-1x-view widgets)))

(defn handle-toggle-split [widgets]
  (widgets/bind-window-keys (:frame widgets) ["1"] #(toggle-1x-view widgets)))

(defn handle-window-closing [frame
                            screen-state-atom
                            screen-state-atom2
                            memory-tile-atom]
  (.addWindowListener frame
    (proxy [WindowAdapter] []
      (windowClosing [e]
        (swap! screen-state-atom assoc :mode :closed)
        (reactive/remove-watches screen-state-atom)
        (reactive/remove-watches screen-state-atom2)))))

;; constraints

(defn within? [x [a b]]
  (<= a x b))
  
(defn clip [x [a b]]
  (-> x (max a) (min b)))

(defn constrain [screen-state-atom axis]
  (let [pos (get @screen-state-atom axis)
        range (get-in @screen-state-atom [:range axis])]
    (when (and pos range (not (within? pos range)))
      (swap! screen-state-atom update-in [axis] clip range))))

(defn enforce-constraints [screen-state-atom]
  (reactive/add-watch-simple
    screen-state-atom
    (fn [_ _] (dorun (map #(constrain screen-state-atom %)
                          [:x :y :z])))))


;; main function enabling controls of the primary screen

(defn make-view-controllable
  [{:keys [left-panel frame] :as widgets}
   screen-state-atom]
  ((juxt handle-drags handle-arrow-pan handle-wheel
         handle-resize handle-pointing handle-mouse-zoom)
         left-panel screen-state-atom)
  ((juxt handle-reset handle-zoom handle-dive) ; watch-keys)
         frame screen-state-atom)
  (handle-toggle-split widgets)
  (enforce-constraints screen-state-atom))
    
 