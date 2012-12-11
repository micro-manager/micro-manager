(ns slide-explorer.canvas
  (:import (java.awt AlphaComposite BasicStroke Color Font Graphics Graphics2D Image
                     Polygon RenderingHints Shape)
           (java.awt.font TextAttribute)
           (java.awt.geom Arc2D Arc2D$Double Area Ellipse2D$Double
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

(defn read-image [file]
  (javax.imageio.ImageIO/read (clojure.java.io/file file)))

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

(defn set-color [g2d color]
  (.setColor g2d (color-object color)))

(def degrees-to-radians (/ Math/PI 180))

(defn set-g2d-state [g2d {:keys [alpha color stroke rotate x y
                                 scale scale-x scale-y]}]
    (doto g2d
      (.setColor (color-object (or color :black)))
    (.setComposite
      (let [compound-alpha (* (or alpha 1) (.. g2d getComposite getAlpha))]
        (AlphaComposite/getInstance AlphaComposite/SRC_ATOP compound-alpha)))
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
    (when (or x y rotate scale scale-x scale-y)
      (doto g2d
        (.translate (double (or x 0)) (double (or y 0)))
        (.rotate (* degrees-to-radians (or rotate 0.0)))
        (.scale (or scale-x scale 1.0) (or scale-y scale 1.0))
        )))

(defn intersect-shapes [shape1 shape2]
  (doto (Area. shape1) (.intersect (Area. shape2))))

(defn- with-g2d-clip-fn [g2d clip body-fn]
  (let [original-clip (.getClip g2d)]
    (.setClip g2d (intersect-shapes clip original-clip))
    (body-fn)
    (.setClip g2d original-clip)))

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
    (/ a 2.)))

(defn- twice? [a]
  (when a
    (* a 2)))

(defn complete-coordinates
  [{:keys [l left t top r right b bottom
           x y
           w width h height]
    :as the-map}]
  (let [l (or l left) t (or t top) r (or r right) b (or b bottom)
        w (or (-? r l) (twice? (-? x l)) (twice? (-? r x)) w width)
        h (or (-? b t) (twice? (-? y t)) (twice? (-? b y)) h height)
        l (or l (-? r w) (-? x (half? w)))
        t (or t (-? b h) (-? y (half? h)))
        r (or r (+? l w) (+? x (half? w)))
        b (or b (+? t h) (+? y (half? h)))
        x (or x (+? l (half? w)))
        y (or y (+? t (half? h)))]
    (->> (merge the-map {:w w :h h :l l :t t :r r :b b :x x :y y})
         (filter second)
         (into {}))))
   
(defmulti make-obj (fn [obj-keyword params] obj-keyword) :default :compound)

(defmethod make-obj :arc
  [_ {:keys [l t w h start-angle arc-angle arc-boundary closed]}]
  (let [type (condp = (or arc-boundary closed)
                             true Arc2D/PIE
                             :pie Arc2D/PIE
                             :chord Arc2D/CHORD
                             :open Arc2D/OPEN
                             Arc2D/OPEN)]
      (Arc2D$Double. (- (/ w 2)) (- (/ h 2)) w h start-angle arc-angle type)))

(defmethod make-obj :ellipse
  [_ {:keys [w h]}]
  (Ellipse2D$Double. (- (/ w 2)) (- (/ h 2)) w h))

(defmethod make-obj :rect
  [_ {:keys [w h]}]
  (Rectangle2D$Double. (- (/ w 2)) (- (/ h 2)) w h))

(defmethod make-obj :round-rect
  [_ {:keys [w h arc-radius arc-width arc-height]}]
  (RoundRectangle2D$Double. (- (/ w 2)) (- (/ h 2)) w h
                              (or arc-radius arc-width)
                              (or arc-radius arc-height)))

(defmethod make-obj :line
  [_ {:keys [w h]}]
  (Line2D$Double. (- (/ w 2)) (- (/ h 2)) (/ w 2) (/ h 2)))

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
  (let [width (:width stroke)]
    (or (nil? width) (pos? width))))

(defmulti draw-primitive (fn [g2d obj params & inner-items] (type obj)))

(defmethod draw-primitive Shape
  [g2d shape {:keys [color fill stroke] :as params}]
  (when fill
    (when-let [fill-color (:color fill)]
      (doto g2d
        (set-color fill-color)
        (.fill shape))))
  (when (stroke? stroke)
    (doto g2d
      (set-color (or (:color stroke) color :black))
      (.draw shape))))

(defmethod draw-primitive Image
  [g2d image params]
  (let [w (or (:w params) (.getWidth image))
        h (or (:h params) (.getHeight image))
        params+ (complete-coordinates (assoc params :w w :h h))
        {:keys [l t]} params+]
    (.drawImage g2d image (- (/ w 2)) (- (/ h 2)) w h nil)))

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
                 (float (- (/ width 2)))
                 (float (+ (/ height 2))))))

(defmethod draw-primitive String
  [g2d text {:keys [x y font fill stroke] :as params}]
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
                       (draw-string-center g2d text x y))
      ))

;  (enable-anti-aliasing g2d)
;  (doseq [[type params & inner-items] items]

(defn draw [g2d [type params & inner-items]]
  (when (= type :graphics)
        (enable-anti-aliasing g2d))
  (let [params+ (complete-coordinates params)]
    (with-g2d-state [g2d params+]
      (if (#{:compound :graphics} type)
        (doseq [inner-item inner-items]
          (draw g2d inner-item))
        (draw-primitive g2d (make-obj type params+) params+)))))

(defn canvas [reference]
  (let [panel (proxy [JPanel] []
                (paintComponent [^Graphics graphics]
                                (proxy-super paintComponent graphics)
                                (draw graphics @reference)))]
    (add-watch reference panel (fn [_ _ _ _]
                                 (.repaint panel)))
    (.setBackground panel Color/BLACK)
    panel))

;; test

(def grafix (atom nil))

(defn canvas-frame [reference]
  (let [panel (canvas reference)]
    (doto (JFrame. "canvas")
      (.. getContentPane (add panel))
      (.setBounds 10 10 500 500)
      (slide-explorer.user-controls/setup-fullscreen)
      .show)
    panel))

(defn demo-canvas []
  (canvas-frame grafix))

(defn demo-animation [reference]
  (dotimes [i 200]
    (Thread/sleep 30)
    (swap! reference (fn [data]
                       (-> data
                           (assoc-in [1 1 :rotate] (* i 15))
                           ))
           )))

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

;(def img (read-image "/Users/arthur/Desktop/flea.png"))

; shape
; stroke color
; fill color/pattern/image
; rotate
; scale
; font
; text
; alpha


(count
(reset!
  grafix 
  [:graphics {}
;   [:rect
;    {:x 250 :y 250 :w 10 :h 300
;     :arc-radius 100
;     :fill {:color :light-gray}
;     :scale 1.0
;     :stroke {:color :gray
;              :cap :round
;              :width 2.0
;              ;:dashes [10 10] :dash-phase 0
;              }
;     :rotate 0}]
;   [:rect
;    {:x 250 :y 200 :w 16 :h 6
;     :arc-radius 100
;     :fill {:color :cyan}
;     :scale 1.0
;     :alpha 1.0
;     :stroke {:color :blue
;              :cap :round
;              :width 2.0
;              ;:dashes [10 10] :dash-phase 0
;              }
;     :rotate 0}]
;   [:round-rect
;    {:x 250 :y 500 :w 100 :h 25
;     :arc-radius 10
;     :fill {:color :light-gray}
;     :scale 1.0
;     :alpha 1.0
;     :stroke {:color :gray
;              :cap :round
;              :width 2.0
;              ;:dashes [10 10] :dash-phase 0
;              }
;     :rotate 0}]
;   [:ellipse
;    {:l 25 :t 15 :w 110 :h 90
;     :fill true :color :yellow :alpha 0.5 :stroke {:width 10 :cap :round :dashes [20 20]}}]
;   [:polyline
;    {:vertices [{:x 100 :y 100}
;                {:x 50 :y 150}
;                {:x 50 :y 220}
;                {:x 160 :y 250}]
;     :fill false
;     :color :black
;     :alpha 0.8
;     :stroke {:width 2
;              :dashes [20 3 10 3 5 3]
;              :cap :butt
;              :join :bevel
;              :miter-limit 10.0}}]
   [:compound {:x 180 :y 300 :scale 1.0 :alpha 0.9 :rotate 0} 
    [:round-rect
     {:y 2 :w 150 :h 30
      :arc-radius 10
      :fill {:color 0x4060D0}
      :scale 1.0
      :alpha 1.0
      :stroke {:width 2 :color :white}
      :rotate 0}]
    [:text
     {:text "Navigate"
      :color :white
      :alpha 0.7
      :rotate 0
      :scale-x 1.0
      :fill true
      :stroke {:width 10 :cap :butt}
      :font {:name "Arial"
             :bold true
             :italic false
             :underline false
             :strikethrough false
             :size 20}}]]
   [:compound
    {:x 150 :y 150 :rotate 0 :scale 0.8 }
    [:ellipse
     {:w 40 :h 40 :fill {:color :pink} :alpha 0.1
      :stroke {:width 4 :color 0xE06060}}]
    [:line
     {:w 18 :h 18 :scale 1.0 :color :red :alpha 1.0
      :stroke {:width 7 :cap :butt}
      }]
    [:line
     {:x 0 :y 0 :w -18 :h 18 :fill false :color :red :alpha 1.0
      :stroke {:width 7 :cap :butt}}]]
;   [:image
;     {:x 200 :t 150 :data img :rotate 171}]
;   [:line
;    {:x 180 :y 220 :w 0 :h 50 :color :red
;     :stroke {:width 10 :cap :round}
;     :alpha 0.7}]
;   [:line
;    {:x 180 :y 220 :w 30 :h 0 :color 0x00AA00
;     :alpha 0.6
;     :stroke {:width 4}}]
;   [:arc
;    {:l 30 :t 20 :w 150 :h 100
;     :start-angle 30 :arc-angle 100 :color 0x004000
;     :fill false
;     :stroke {:width 10}
;     :alpha 0.7}]
   ]))


