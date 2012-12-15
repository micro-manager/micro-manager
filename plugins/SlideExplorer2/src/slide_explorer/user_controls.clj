(ns slide-explorer.user-controls
  (:import (java.awt.event ComponentAdapter KeyEvent KeyAdapter
                           MouseAdapter MouseEvent WindowAdapter)
           (java.awt Window)
           (javax.swing AbstractAction JComponent KeyStroke SwingUtilities)
           (java.util UUID)))

(def MIN-ZOOM 1/256)

(def MAX-ZOOM 1)

(defn window-descendants
  "Returns a depth-first seq of all components contained by window."
  [window]
  (tree-seq (constantly true)
            #(.getComponents %)
            window))

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
  "Make the given window/frame full-screen. Pass nil to return all windows
to normal size."
  [window]
  (when window
    (when-not (@old-bounds window)
      (swap! old-bounds assoc window (.getBounds window)))
    (.dispose window)
    (.setUndecorated window true)
    (.setBounds window (screen-bounds (window-screen window)))
    (.show window)))

(defn exit-full-screen!
  [window]
  (when window
    (.dispose window)
    (.setUndecorated window false)
    (when-let [bounds (@old-bounds window)]
      (.setBounds window bounds)
      (swap! old-bounds assoc window nil))
    (.show window)))

(defn setup-fullscreen [window]
  (bind-window-keys window ["F"] #(full-screen! window))
  (bind-window-keys window ["ESCAPE"] #(exit-full-screen! window)))

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

(defn handle-arrow-pan [component position-atom dist]
  (let [binder (fn [key axis step]
                 (bind-key component key
                           #(pan! position-atom axis step) true))]
    (binder "UP" :y dist)
    (binder "DOWN" :y (- dist))
    (binder "RIGHT" :x (- dist))
    (binder "LEFT" :x dist)))

(defn handle-mode-keys [panel screen-state-atom]
  (let [window (SwingUtilities/getWindowAncestor panel)]
    (bind-window-keys window ["SPACE"]
                      #(swap! screen-state-atom
                              (fn [state]
                                (assoc state :mode
                                       (condp = (:mode state)
                                         :explore :navigate
                                         :navigate :explore)))))))

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
  (bind-window-keys window ["ADD" "CLOSE_BRACKET" "EQUALS"]
                   (fn [] (swap! zoom-atom update-in [:zoom]
                                 #(min (* % 2) MAX-ZOOM))))
  (bind-window-keys window ["SUBTRACT" "OPEN_BRACKET" "MINUS"]
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
                             (= 2 (.getClickCount e))))
                response-fn))

(defn handle-alt-click [panel response-fn]
  (handle-click panel
                (fn [e] (and (= MouseEvent/BUTTON1 (.getButton e))
                             (.isAltDown e)))
                response-fn))
                                     
(defn handle-pointing [component screen-state-atom]
  (.addMouseMotionListener component
                     (proxy [MouseAdapter] []
                       (mouseMoved [e] (update-mouse-position e screen-state-atom))
                       (mouseDragged [e] (update-mouse-position e screen-state-atom)))))
                                   

;(defn handle-open [window]
;  (bind-window-keys window ["S"] create-dir-dialog))

(defn make-view-controllable [panel screen-state-atom]
  (let [handle-arrow-pan-50 #(handle-arrow-pan %1 %2 50)]
    ((juxt handle-drags handle-arrow-pan-50 handle-wheel handle-resize handle-pointing)
           panel screen-state-atom)
    ((juxt handle-zoom handle-dive watch-keys) (.getTopLevelAncestor panel) screen-state-atom)))
    
 