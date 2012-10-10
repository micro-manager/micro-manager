(ns slide-explorer.core
  (:import (java.awt Graphics Graphics2D RenderingHints)
           (java.awt Color Font Polygon)
           (javax.swing JFrame JPanel)))


(defn enable-anti-aliasing
  "Turn on (off) anti-aliasing for a graphics context."
  ([^Graphics g]
    (enable-anti-aliasing g true))
  ([^Graphics g on]
    (let [graphics2d (cast Graphics2D g)]
      (.setRenderingHint graphics2d
                         RenderingHints/KEY_ANTIALIASING
                         (if on
                           RenderingHints/VALUE_ANTIALIAS_ON
                           RenderingHints/VALUE_ANTIALIAS_OFF)))))

(def default-primitive-params
  {:filled false :color Color/BLACK})

(defn set-style [g2d params]
  (doto g2d
    (.setColor (:color params))))

(defmulti draw-primitive (fn [g2d shape params] shape))

(defmethod draw-primitive :primitive-arc
  [g2d shape {:keys [x y w h filled start-angle arc-angle]}]
  (if filled
    (.fillArc g2d x y w h start-angle arc-angle)
    (.drawArc g2d x y w h start-angle arc-angle)))

(defmethod draw-primitive :primitive-ellipse
  [g2d shape {:keys [x y w h filled]}]
  (if filled
    (.fillOval g2d x y w h)
    (.drawOval g2d x y w h)))

(defmethod draw-primitive :primitive-image
  [g2d shape {:keys [image x y w h]}]
  (.drawImage g2d
              image x y
              (or w (.getWidth image))
              (or h (.getHeight image)) nil))

(defmethod draw-primitive :primitive-line
  [g2d shape {:keys [x y w h filled]}]
  (.drawLine g2d x y (+ x w) (+ x h)))

(defmethod draw-primitive :primitive-polygon
  [g2d shape {:keys [vertices filled closed]}]
  (let [xs (int-array (map :x vertices))
        ys (int-array (map :y vertices))
        n (count vertices)]
    (if filled
      (.fillPolygon g2d xs ys n)
      (if closed
        (.drawPolygon g2d xs ys n)))
        (.drawPolyline g2d xs ys n)))

(defmethod draw-primitive :primitive-rect
  [g2d shape {:keys [x y w h filled]}]
  (if filled
    (.fillRect g2d x y w h)
    (.drawRect g2d x y w h)))

(defmethod draw-primitive :primitive-round-rect
  [g2d shape {:keys [x y w h filled arc-width arc-height]}]
  (if filled
    (.fillRoundRect g2d x y w h arc-width arc-height)
    (.drawRoundRect g2d x y w h arc-width arc-height)))

(defmethod draw-primitive :primitive-text
  [g2d shape  {:keys [text x y font]}]
  (let [{:keys [name bold italic size]} font
        style (bit-or (if bold Font/BOLD 0) (if italic Font/ITALIC 0))
        font-obj (Font. name style size)]
    (.setFont g2d font-obj)
    (.drawString g2d text x y)))

(defn draw-primitives [g2d items]
  (enable-anti-aliasing g2d)
  (dorun
    (for [item (flatten items)]
      (let [params (merge default-primitive-params
                                        (:params item))]
      (set-style g2d params)
      (draw-primitive g2d (:type item) params)))))

(defn paint-canvas-graphics [^Graphics graphics data]
  (draw-primitives graphics data))

(defn canvas [reference]
  (let [panel (proxy [JPanel] []
                (paintComponent [^Graphics graphics]
                                (proxy-super paintComponent graphics)
                                (paint-canvas-graphics graphics @reference)))]
    (add-watch reference panel (fn [_ _ _ _]
                                 (.repaint panel)))
    panel))

;; test

(def temp-data
  [
   {:type :primitive-round-rect
    :params {:x 20 :y 10 :w 120 :h 100
             :arc-width 20 :arc-height 20
             :filled true :color Color/RED}}
   {:type :primitive-ellipse
    :params {:x 25 :y 15 :w 110 :h 90
             :arc-width 20 :arc-height 20
             :filled true :color Color/YELLOW}}
   {:type :primitive-round-rect
    :params {:x 20 :y 10 :w 120 :h 100
             :arc-width 20 :arc-height 20
             :filled false :color Color/BLACK}}
   {:type :primitive-polygon
    :params {:vertices [{:x 100 :y 100}
                        {:x 50 :y 150}
                        {:x 150 :y 150}]
             :filled false
             :closed true}}
   {:type :primitive-text
    :params {:x 80 :y 140 :text "TEST!" :color Color/BLACK
             :font {:name "Arial"
                    :bold true
                    :italic true
                    :size 16}}}
   {:type :primitive-arc
    :params {:x 40 :y 30 :w 100 :h 100
             :start-angle 10 :arc-angle 100 :color Color/GREEN
             :filled true}}
   ])

(def grafix (atom temp-data))

(defn canvas-frame [reference]
  (let [panel (canvas reference)]
    (doto (JFrame. "canvas")
      (.. getContentPane (add panel))
      (.setBounds 10 10 500 500)
      .show)
    panel))

(defn demo-animation [reference]
  (dotimes [i 200]
  (Thread/sleep 10)
  (swap! reference (fn [data]
                     (-> data
                         (assoc-in [0 :params :w] (+ i 100))
                         (assoc-in [0 :params :h] (+ i 100)))
                     ))))

