(ns slide-explorer.core
  (:import (java.awt Color Font Graphics Graphics2D Polygon RenderingHints)
           (java.awt.font TextAttribute)
           (javax.swing JFrame JPanel)))

;; possible 
; draggable
; clickable
; hoverable
; rotatable
; resizable
; shearable
;
; gradients
; affine transforms

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

(defn- -? [a b]
  (when (and a b)
    (- a b)))

(defn- +? [a b]
  (when (and a b)
    (+ a b)))

(defn- half? [a]
  (when a
    (/ a 2)))

(defn- twice? [a]
  (when a
    (* a 2)))

(defn complete-coordinates
  [{:keys [l left t top r right b bottom
           x y
           w width h height]
    :as the-map}]
  (let [l (or l left) t (or t top) r (or r right) b (or b bottom)
        w (or w width (-? r l) (twice? (-? x l)) (twice? (-? r x)))
        h (or h height (-? b t) (twice? (-? y t)) (twice? (-? b y)))
        l (or l (-? r w) (-? x (half? w)))
        t (or t (-? b h) (-? y (half? h)))
        r (or r (+? l w) (+? x (half? w)))
        b (or b (+? t h) (+? y (half? h)))
        x (or x (+? l (half? w)))
        y (or y (+? t (half? h)))]
    (merge the-map {:w w :h h :l l :t t :r r :b b :x x :y y})))
   
(defmulti draw-primitive (fn [g2d shape params] shape))

(defmethod draw-primitive :primitive-arc
  [g2d shape {:keys [l t w h filled start-angle arc-angle]}]
  (if filled
    (.fillArc g2d l t w h start-angle arc-angle)
    (.drawArc g2d l t w h start-angle arc-angle)))

(defmethod draw-primitive :primitive-ellipse
  [g2d shape {:keys [l t w h filled]}]
  (if filled
    (.fillOval g2d l t w h)
    (.drawOval g2d l t w h)))

(defmethod draw-primitive :primitive-image
  [g2d shape {:keys [image l t w h]}]
  (.drawImage g2d image
              l t
              (or w (.getWidth image))
              (or h (.getHeight image)) nil))

(defmethod draw-primitive :primitive-line
  [g2d shape {:keys [l t r b filled]}]
  (.drawLine g2d l t r b))

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
  [g2d shape {:keys [l t w h filled]}]
  (if filled
    (.fillRect g2d l t w h)
    (.drawRect g2d l t w h)))

(defmethod draw-primitive :primitive-round-rect
  [g2d shape {:keys [l t w h filled arc-width arc-height]}]
  (if filled
    (.fillRoundRect g2d l t w h arc-width arc-height)
    (.drawRoundRect g2d l t w h arc-width arc-height)))

(defn draw-string-center
  "Draw a string centered at position x,y."
  [^Graphics2D graphics ^String text x y]
  (let [context (.getFontRenderContext graphics)
        height (.. graphics
                   getFont (createGlyphVector context text)
                   getVisualBounds
                   getHeight)
        width (.. graphics
                  getFontMetrics
                  (getStringBounds text graphics)
                  getWidth)]
    (.drawString graphics text
                 (float (- x (/ width 2)))
                 (float (+ y (/ height 2))))))

(defmethod draw-primitive :primitive-text
  [g2d shape  {:keys [text x y font]}]
  (let [{:keys [name bold italic underline strikethrough size]} font
        style (bit-or (if bold Font/BOLD 0) (if italic Font/ITALIC 0))
        font1 (Font. name style size)
        attributes (.getAttributes font1)]
    (doto attributes
      (.put TextAttribute/STRIKETHROUGH
            (if strikethrough TextAttribute/STRIKETHROUGH_ON false))
      (.put TextAttribute/UNDERLINE
            (if underline TextAttribute/UNDERLINE_ON -1)))
    (let [font2 (Font. attributes)]
      (.setFont g2d font2)
      (draw-string-center g2d text x y))))

(defn draw-primitives [g2d items]
  (enable-anti-aliasing g2d)
  (dorun
    (for [item (flatten items)]
      (let [params (merge default-primitive-params
                                        (:params item))]
      (set-style g2d params)
      (draw-primitive g2d (:type item) (complete-coordinates params))))))

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
    :params {:l 20 :t 10 :w 140 :h 140
             :arc-width 20 :arc-height 20
             :filled true :color Color/RED}}
   {:type :primitive-ellipse
    :params {:l 25 :t 15 :w 110 :h 90
             :arc-width 20 :arc-height 20
             :filled true :color Color/YELLOW}}
   {:type :primitive-round-rect
    :params {:x 90 :y 80 :r 160 :b 150
             :arc-width 20 :arc-height 20
             :filled false :color Color/BLACK}}
   {:type :primitive-polygon
    :params {:vertices [{:x 100 :y 100}
                        {:x 50 :y 150}
                        {:x 160 :y 160}]
             :filled false
             :closed true}}
   {:type :primitive-text
    :params {:x 180 :y 280 :text "TEST"
             :color Color/BLUE
             :font {:name "Courier New"
                    :bold true
                    :italic false
                    :underline true
                    :strikethrough false
                    :size 100}}}
   {:type :primitive-line
    :params {:x 180 :y 280 :w 0 :h 300 :color Color/RED}}
   {:type :primitive-line
    :params {:x 180 :y 280 :w 10 :h 0 :color Color/RED}}
   {:type :primitive-arc
    :params {:l 40 :t 30 :w 100 :h 100
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

(defn demo-var []
  (canvas-frame (var temp-data)))

(defn demo-atom []
  (canvas-frame grafix))

(defn demo-animation [reference]
  (dotimes [i 200]
  (Thread/sleep 30)
  (swap! reference (fn [data]
                     (-> data
                         (assoc-in [0 :params :w] (+ i 100))
                         (assoc-in [0 :params :h] (+ i 100)))
                     ))))

