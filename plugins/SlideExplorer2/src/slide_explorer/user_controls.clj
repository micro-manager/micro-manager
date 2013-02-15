(ns slide-explorer.user-controls
  (:import (java.awt.event ComponentAdapter KeyEvent KeyAdapter
                           MouseAdapter MouseEvent WindowAdapter
                           ActionListener)
           (java.awt Window)
           (javax.swing AbstractAction JComponent KeyStroke SwingUtilities
                        JButton)
           (java.util UUID)
           (org.micromanager.utils JavaUtils)))

(def MIN-ZOOM 1/256)

(def MAX-ZOOM 1)

(defn window-descendants
  "Returns a depth-first seq of all components contained by window."
  [window]
  (tree-seq (constantly true)
            #(.getComponents %)
            window))

;; widgets

(defn button [text press-fn]
  (doto (JButton. text)
    (.addActionListener
      (proxy [ActionListener] []
        (actionPerformed [e]
                         (press-fn))))))

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

(defn- screen-devices []
  (seq (.. java.awt.GraphicsEnvironment
      getLocalGraphicsEnvironment
      getScreenDevices)))

(defn- screen-bounds [screen]
  (.. screen getDefaultConfiguration getBounds))

(defn- overlap-area [rect1 rect2]
  (let [intersection (.intersection rect1 rect2)]
    (* (.height intersection) (.width intersection))))

(defn- window-screen [window]
  (when window
    (let [window-bounds (.getBounds window)
          screens (screen-devices)]
      (apply max-key #(overlap-area window-bounds
                                    (screen-bounds %)) screens))))

(def old-bounds (atom {}))

(defn full-screen!
  "Make the given window/frame full-screen."
  [window]
  (when (and window (not (@old-bounds window)))
    (when-not (@old-bounds window)
      (swap! old-bounds assoc window (.getBounds window)))
    (.dispose window)
    (.setUndecorated window true)
    (let [screen (window-screen window)]
      (if (JavaUtils/isMac)
        (.setFullScreenWindow screen window)
        (.setBounds window (screen-bounds screen))))
    (.repaint window)
    (.show window)))

(defn exit-full-screen!
  "Restore the given full-screened window to its previous
   (non-full-screen) bounds."
  [window]
  (when (and window (@old-bounds window))
    (.dispose window)
    (.setUndecorated window false)
    (when (JavaUtils/isMac)
      (.setFullScreenWindow (window-screen window) nil))
    (when-let [bounds (@old-bounds window)]
      (.setBounds window bounds)
      (swap! old-bounds dissoc window))
    (.repaint window)
    (.show window)))

(defn toggle-full-screen!
  "Turn full screen mode on and off for a given window."
  [window]
  (when window
    (if (@old-bounds window)
      (exit-full-screen! window)
      (full-screen! window))))

(defn setup-fullscreen [window]
  (bind-window-keys window ["F"] #(toggle-full-screen! window))
  (bind-window-keys window ["ESCAPE"] #(exit-full-screen! window)))

;; window positioning

(defn show-window-center
  ([window width height parent-window]
    (let [bounds (screen-bounds
                   (or (window-screen parent-window)
                       (first (screen-devices))))
          x (+ (.x bounds) (/ (- (.width bounds) width) 2))
          y (+ (.y bounds) (/ (- (.height bounds) height) 2))]
      (doto window
        (.setBounds x y width height)
        .show)))
  ([window width height]
    (show-window-center window width height nil)))

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
                 (bind-key component key
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
    (bind-window-keys window [\ ] ; space bar
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
  (bind-window-keys window ["COMMA"] #(swap! dive-atom update-in [:z] dec))
  (bind-window-keys window ["PERIOD"] #(swap! dive-atom update-in [:z] inc)))

(def scale-delta 0.04)

(defn run-zoom! 
  "Smoothly zoom :in or :out by one factor of 2, asynchronously."
  [zoom-atom direction]
  (future
    (let [in? (= direction :in)
          {:keys [zoom scale]} @zoom-atom
          factor (if in? 2 1/2)]
      (when (and (= 1 scale)
                 (if in?
                   (< zoom (- MAX-ZOOM 0.001))
                   (> zoom (+ MIN-ZOOM 0.001))))
        (doseq [scale (map #(Math/pow factor %)
                           (range scale-delta 1 scale-delta))]
          (swap! zoom-atom assoc :scale scale)
          (Thread/sleep 10))
        (swap! zoom-atom assoc
               :scale 1
               :zoom (* (@zoom-atom :zoom) factor))))))

(defn handle-zoom [window zoom-atom]
  (bind-window-keys window ["ADD" "CLOSE_BRACKET" "EQUALS"]
    (fn [] (run-zoom! zoom-atom :in)))
  (bind-window-keys window ["SUBTRACT" "OPEN_BRACKET" "MINUS"]
    (fn [] (run-zoom! zoom-atom :out))))
  
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

(defn update-mouse-position [e screen-state-atom]
  (swap! screen-state-atom update-in [:mouse]
         merge {:x (.getX e) :y (.getY e)}))

(defn handle-click [panel event-predicate response-fn]
  (.addMouseListener panel
                     (proxy [MouseAdapter] []
                       (mouseClicked [e]
                                     (when (event-predicate e)
                                       (response-fn (.getX e) (.getY e)))))))

(defn handle-double-click [panel response-fn]
  (handle-click panel
                (fn [e] (and (= MouseEvent/BUTTON1 (.getButton e))
                             (not (.isShiftDown e))
                             (= 2 (.getClickCount e))))
                response-fn))

(defn handle-shift-click [panel response-fn]
  (handle-click panel
                (fn [e] (and (= MouseEvent/BUTTON1 (.getButton e))
                             (.isShiftDown e)))
                response-fn))
                                     
(defn handle-pointing [component screen-state-atom]
  (.addMouseMotionListener component
    (proxy [MouseAdapter] []
      (mouseMoved [e] (update-mouse-position e screen-state-atom))
      (mouseDragged [e] (update-mouse-position e screen-state-atom)))))
                                   
(defn absolute-mouse-position [screen-state]
  (let [{:keys [x y z mouse zoom scale width height tile-dimensions]} screen-state]
    (when mouse
      (let [mouse-x-centered (- (mouse :x) (/ width 2))
            mouse-y-centered (- (mouse :y) (/ height 2))
            [w h] tile-dimensions]
        {:x (long (+ x (/ mouse-x-centered zoom scale) (/ w -2)))
         :y (long (+ y (/ mouse-y-centered zoom scale) (/ h -2)))
         :z z}))))

(defn handle-reset [window screen-state-atom]
  (bind-window-keys window ["shift R"]
               #(swap! screen-state-atom
                       assoc :x 0 :y 0 :z 0 :zoom 1)))


;(defn handle-open [window]
;  (bind-window-keys window ["S"] create-dir-dialog))

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
    (.remove (.getLeftComponent split-pane))
    (.remove (.getRightComponent split-pane)))
  (doto content-pane
    (.remove split-pane)
    (.add left-panel))
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
  (bind-window-keys (:frame widgets) ["1"] #(toggle-1x-view widgets)))

(defn make-view-controllable [widgets screen-state-atom]
  (let [panel (:left-panel widgets)]
    ((juxt handle-drags handle-arrow-pan handle-wheel
           handle-resize handle-pointing)
           panel screen-state-atom)
    ((juxt handle-reset handle-zoom handle-dive) ; watch-keys)
           (.getTopLevelAncestor panel) screen-state-atom)
    (handle-toggle-split widgets)))
    
 