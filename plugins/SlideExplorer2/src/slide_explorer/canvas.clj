(ns slide-explorer.core
  (:import (java.awt AlphaComposite BasicStroke Color Font Graphics Graphics2D Image
                     Polygon RenderingHints Shape)
           (java.awt.font TextAttribute)
           (java.awt.geom Arc2D Arc2D$Double Ellipse2D$Double
                          Line2D$Double Path2D$Double
                          Rectangle2D$Double
                          RoundRectangle2D$Double)
           (javax.swing JFrame JPanel JScrollPane JTextArea)
           (javax.swing.event DocumentListener)))

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
  {:filled false :color :black})

(def stroke-caps
  {:butt BasicStroke/CAP_BUTT
   :round BasicStroke/CAP_ROUND
   :square BasicStroke/CAP_SQUARE})

(def stroke-joins
  {:miter BasicStroke/JOIN_MITER
   :round BasicStroke/JOIN_ROUND
   :bevel BasicStroke/JOIN_BEVEL})

(def colors
  {:red Color/RED
   :blue Color/BLUE
   :green Color/GREEN
   :cyan Color/CYAN
   :magenta Color/MAGENTA
   :yellow Color/YELLOW
   :pink Color/PINK
   :orange Color/ORANGE
   :black Color/BLACK
   :white Color/WHITE
   :gray Color/GRAY
   :grey Color/GRAY
   :dark-gray Color/DARK_GRAY
   :dark-grey Color/DARK_GRAY
   :light-gray Color/LIGHT_GRAY
   :light-grey Color/LIGHT_GRAY
   })

(defn color-object [color]
  (cond
    (integer? color) (Color. color)
    (keyword? color) (colors color)
    (string? color)  (colors (keyword color))
    :else            color))

(def degrees-to-radians (/ Math/PI 180))

(defn set-g2d-state [g2d {:keys [alpha color stroke rotate x y
                                 scale scale-x scale-y]}]
  (doto g2d
    (.setColor (color-object (or color :black)))
    (.setComposite (if (or (not alpha) 
                           (= alpha 1))
                     AlphaComposite/Src
                     (AlphaComposite/getInstance AlphaComposite/SRC_ATOP alpha)))
    (.setStroke (let [{:keys [width cap join miter-limit dashes dash-phase]
                       :or {width 1.0 cap :square
                            join :miter miter-limit 10.0
                            dashes [] dash-phase 0.0}} stroke
                      cap-code (stroke-caps cap)
                      join-code (stroke-joins join)
                      dashes-array (float-array dashes)]
                  (if-not (empty? dashes)
                    (BasicStroke. width cap-code join-code miter-limit
                                  dashes-array dash-phase)
                    (BasicStroke. width cap-code join-code miter-limit)))))
    (when (and x y (or rotate scale scale-x scale-y))
      (doto g2d
        (.translate x y)
        (.rotate (* degrees-to-radians (or rotate 0.0)))
        (.scale (or scale-x scale 1.0) (or scale-y scale 1.0))
        (.translate (- x) (- y)))))

(defn- with-g2d-state-fn [g2d params body-fn]
  (let [color (.getColor g2d)
        composite (.getComposite g2d)
        paint (.getPaint g2d)
        stroke (.getStroke g2d)
        transform (.getTransform g2d)]
    (set-g2d-state g2d params)
    (body-fn)
    (doto g2d
      (.setColor color)
      (.setComposite composite)
      (.setPaint paint)
      (.setStroke stroke)
      (.setTransform transform))))

(defmacro with-g2d-state [[g2d params] & body]
  `(with-g2d-state-fn ~g2d ~params (fn [] ~@body)))

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
   
(defmulti make-obj (fn [obj-keyword params] obj-keyword))

(defmethod make-obj :arc
  [_ {:keys [l t w h start-angle arc-angle arc-boundary closed]}]
  (let [type (condp = (or arc-boundary closed)
                             true Arc2D/PIE
                             :pie Arc2D/PIE
                             :chord Arc2D/CHORD
                             :open Arc2D/OPEN
                             Arc2D/OPEN)]
      (Arc2D$Double. l t w h start-angle arc-angle type)))

(defmethod make-obj :ellipse
  [_ {:keys [l t w h]}]
  (Ellipse2D$Double. l t w h))

(defmethod make-obj :rect
  [_ {:keys [l t w h]}]
  (Rectangle2D$Double. l t w h))

(defmethod make-obj :round-rect
  [_ {:keys [l t w h arc-radius arc-width arc-height]}]
  (RoundRectangle2D$Double. l t w h
                              (or arc-radius arc-width)
                              (or arc-radius arc-height)))

(defmethod make-obj :line
  [_ {:keys [l t r b]}]
  (Line2D$Double. l t r b))

(defmethod make-obj :polygon
  [_ {:keys [vertices]}]
  (let [xs (int-array (map :x vertices))
        ys (int-array (map :y vertices))
        n (count vertices)]
    (Polygon. xs ys n)))

(defmethod make-obj :polyline
  [_ {:keys [vertices]}]
  (let [path (Path2D$Double.)]
    (let [{:keys [x y]} (first vertices)]
      (.moveTo path x y))
    (doseq [{:keys [x y]} (rest vertices)]
      (.lineTo path x y))
    path))

(defmethod make-obj :text
  [_ {:keys [text]}]
  text)

(defmethod make-obj :image
  [_ {:keys [data]}]
  data)

(defn stroke? [stroke]
  (and stroke (pos? (:width stroke))))

(defmulti draw-primitive (fn [g2d obj params] (type obj)))

(defmethod draw-primitive Shape
  [g2d shape {:keys [fill stroke]}]
  (when fill
    (.fill g2d shape))
  (when (stroke? stroke)
    (.draw g2d shape)))

(defmethod draw-primitive Image
  [g2d image {:keys [l t w h]}]
  (.drawImage g2d image
              l t
              (or w (.getWidth image))
              (or h (.getHeight image)) nil))

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

(defmethod draw-primitive String
  [g2d text {:keys [x y font fill stroke]}]
  (let [{:keys [name bold italic underline strikethrough size]} font
        style (bit-or (if bold Font/BOLD 0) (if italic Font/ITALIC 0))
        font1 (Font. name style size)
        attributes (.getAttributes font1)]
    (doto attributes
      (.put TextAttribute/STRIKETHROUGH
            (if strikethrough TextAttribute/STRIKETHROUGH_ON false))
      (.put TextAttribute/UNDERLINE
            (if underline TextAttribute/UNDERLINE_ON -1)))
    (let [font2 (Font. attributes)
          ;context (.getFontRenderContext g2d)
          ;obj (.getOutline (.createGlyphVector font2 context text))
          ]
      ;(.translate g2d x y)
      ;(draw-shape g2d obj fill stroke)
      ;(.translate g2d (- x) (- y))
      (.setFont g2d font2)
      (draw-string-center g2d text x y)
      )))

(defn draw-primitives [g2d items]
  (enable-anti-aliasing g2d)
  (doseq [[type params inner] items]
    (when type
    (let [params+ (complete-coordinates params)]
      (with-g2d-state [g2d params+]
                      (draw-primitive g2d (make-obj type params+) params+))))))

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

(def grafix (atom nil))

(defn canvas-frame [reference]
  (let [panel (canvas reference)]
    (doto (JFrame. "canvas")
      (.. getContentPane (add panel))
      (.setBounds 10 10 500 500)
      .show)
    panel))

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

(defn handle-data-changed [text-area changed-fn]
  (let [doc (.getDocument text-area)]
    (.addDocumentListener doc
      (proxy [DocumentListener] []
        (changedUpdate [_] (changed-fn))
        (insertUpdate [_] (changed-fn))
        (removeUpdate [_] (changed-fn))))))
        

(defn data-editor [data-atom]
  (let [text-area (JTextArea.)
        scroll-pane (JScrollPane. text-area)]
    (handle-data-changed text-area
                         #(try (reset! data-atom
                                 (read-string (.getText text-area)))
                                    (catch Exception e )))
    (.setText text-area (with-out-str (clojure.pprint/pprint @data-atom)))
    (doto (JFrame.)
      (.. getContentPane (add scroll-pane))
      .show)))

(reset!
  grafix 
  [
   [:round-rect
    {:l 20 :t 10 :w 300 :h 300
     :arc-radius 100
     :fill true :color :pink
     :rotate 50}]
   [:round-rect
    {:l 20 :t 10 :w 300 :h 300
     :arc-radius 100 :rotate 50
     :fill false :color 0xE06060
     :stroke {:width 5
              :cap :round
              :dashes [10 10] :dash-phase 0}}]
   [:ellipse
    {:l 25 :t 15 :w 110 :h 90
     :fill true :color :yellow :alpha 0.5 :stroke nil}]
   [:polyline
    {:vertices [{:x 100 :y 100}
                {:x 50 :y 150}
                {:x 50 :y 220}
                {:x 160 :y 250}]
     :fill false
     :color :black
     :alpha 0.8
     :stroke {:width 2
              :dashes [20 3 10 3 5 3]
              :cap :butt
              :join :bevel
              :miter-limit 10.0}}]
   [:text
    {:x 180 :y 120 :text "Testing!!!"
     :color :white
     :alpha 0.7
     :rotate 0.2
     :scale 1.1
     :fill true
     :stroke {:width 10 :cap :butt}
     :font {:name "Arial"
            :bold true
            :italic false
            :underline false
            :strikethrough false
            :size 60}}]
   (comment
   [:ellipse
    {:x 350 :y 350 :w 40 :h 40 :fill true :color :pink
     :stroke nil}]
   [:ellipse
    {:x 350 :y 350 :w 40 :h 40 :fill false :color :red
     :stroke {:width 4}}]
   [:line
    {:x 350 :y 350 :w 18 :h 18 :fill false :color :white :alpha 0.8
     :stroke {:width 7 :cap :butt}}]
   [:line
    {:x 350 :y 350 :w -18 :h 18 :fill false :color :white :alpha 0.8
     :stroke {:width 7 :cap :butt}}])
   [:line
    {:x 180 :y 220 :w 0 :h 50 :color :red
     :stroke {:width 10 :cap :round}
     :alpha 0.7}]
   [:line
    {:x 180 :y 220 :w 30 :h 0 :color 0x00AA00
     :alpha 0.6
     :stroke {:width 4}}]
   [:arc
    {:l 30 :t 20 :w 150 :h 100
     :start-angle 30 :arc-angle 100 :color 0x004000
     :fill false
     :stroke {:width 10}
     :alpha 0.7}]
   ])


