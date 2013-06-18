(ns slide-explorer.canvas
  (:import (java.awt AlphaComposite BasicStroke Color Font Graphics 
                     Graphics2D GradientPaint Image
                     Polygon RenderingHints Shape)
           (java.awt.event MouseAdapter)
           (java.awt.font TextAttribute)
           (java.awt.geom AffineTransform
                          Arc2D Arc2D$Double Area Ellipse2D$Double
                          Line2D$Double Path2D$Double
                          Point2D$Double
                          Rectangle2D$Double
                          RoundRectangle2D$Double)
           (javax.swing JFrame JPanel JScrollPane JTextArea)
           (javax.swing.event DocumentListener))
  (:require [slide-explorer.widgets :as widgets]))

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

(def ^:dynamic *canvas-error*)

(defn clip-value
  "Clips a value between min-value and max-value."
  [value min-value max-value]
  (max min-value (min max-value value)))

(defn read-image
  "Read an image from a file."
  [file]
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
   :brown (Color. 0xA52A2A)
   })

(defn color-object
  "Convert a color name (keyword or string) or an
   integer to a color object. Color objects
   are returned unchanged."
  [color]
  (cond
    (integer? color) (Color. color)
    (keyword? color) (colors color)
    (string? color)  (colors (keyword color))
    :else            color))

(defn set-color
  "Set the current color of a Graphics2D instance."
  [g2d color]
  (.setColor g2d (color-object color)))
  
(def degrees-to-radians (/ Math/PI 180))

(defn set-alpha
  "Set the current alpha (transparency) for a Graphics2D instance."
  [g2d alpha]
  (when (and alpha (< alpha 1))
    (.setComposite g2d
                   (let [compound-alpha (* alpha (.. g2d getComposite getAlpha))]
                     (AlphaComposite/getInstance AlphaComposite/SRC_ATOP compound-alpha)))))

(defn set-stroke
  "Set the current stroke for a Graphics2D instance."
  [g2d {:keys [width cap join miter-limit
               dashes dash-phase]
        :or {width 1.0 cap :square
             join :miter miter-limit 10.0
             dashes [] dash-phase 0.0}}]
  (.setStroke g2d
              (let [cap-code (stroke-caps cap)
                    join-code (stroke-joins join)
                    dashes-array (float-array dashes)]
                (if-not (empty? dashes)
                  (BasicStroke. width cap-code join-code miter-limit
                                dashes-array dash-phase)
                  (BasicStroke. width cap-code join-code miter-limit)))))

(defn set-transform!
  "Modifes the affine transform. Can be a Grahpics2D or an
   AffineTransform object."
  [transform-object {:keys [x y rotate scale scale-x scale-y]}]
  (if (or x y rotate scale scale-x scale-y)
    (doto transform-object
      (.translate (double (or x 0)) (double (or y 0)))
      (.rotate (* degrees-to-radians (or rotate 0.0)))
      (.scale (or scale-x scale 1.0) (or scale-y scale 1.0)))
    transform-object))

(defn apply-transform
  "Modifies the affine transform. Returns a new instance,
   so the original instance is not modified."
  [transform {:keys [x y rotate scale scale-x scale-y] :as params}]
  (-> transform
      .clone
      (set-transform! params)))
  
(defn set-g2d-state
  "Modifies the drawing state of the Graphics2D instance."
  [g2d {:keys [alpha color stroke] :as params}]
  (set-color g2d (or color :black))
  (set-alpha g2d alpha)
  (set-stroke g2d stroke)
  (set-transform! g2d params))

(defn intersect-shapes
  "Returns the intersection of two shapes."
  [shape1 shape2]
  (doto (Area. shape1) (.intersect (Area. shape2))))

(defn- with-g2d-clip-fn
  "Runs body-fn while a particular clipping shape is temporarily
   to the Graphics2D instance."
  [g2d clip body-fn]
  (let [original-clip (.getClip g2d)]
    (.setClip g2d (intersect-shapes clip original-clip))
    (body-fn)
    (.setClip g2d original-clip)))

(defn- with-g2d-state-fn
  "Runs body-fn while applying temporary graphics settings to
   the Grahpics2D instance."
  [g2d params body-fn]
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

(defn set-gradient [g2d {:keys [x1 y1 color1
                                x2 y2 color2]}]
  (.setPaint g2d (GradientPaint. x1 y1 (color-object color1)
                                 x2 y2 (color-object color2))))

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

(defmethod make-obj :point
  [_ _]
  (Line2D$Double. 0 0))

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

(defmethod make-obj :graphics
  [_ _]
  nil)

(defmethod make-obj :compound
  [_ _]
  nil)

(defmethod make-obj nil
  [_ _]
  nil)

(defn stroke? [stroke]
  (let [width (:width stroke)]
    (or (nil? width) (pos? width))))

(defmulti draw-primitive (fn [g2d obj params & inner-items] (type obj)))

(defmethod draw-primitive nil [_ _ _])

(defmethod draw-primitive Shape
  [g2d shape {:keys [color fill stroke id input] :as params}]
  (when fill
    (when-let [fill-color (:color fill)]
      (doto g2d
        (set-color fill-color)
        (.fill shape))))
    (when-let [gradient (:gradient fill)]
      (doto g2d
        (set-gradient gradient)
        (.fill shape)))
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
    (with-g2d-state [g2d params+]
      (.drawImage g2d image (- (/ w 2)) (- (/ h 2)) w h nil))))

(defmethod draw-primitive String
  [g2d text {:keys [x y font fill stroke]:as params}]
  (let [{:keys [name bold italic underline strikethrough size]
         :or {name Font/SANS_SERIF size 18}} font
        style (bit-or (if bold Font/BOLD 0) (if italic Font/ITALIC 0))
        font1 (Font. name style size)
        attributes (.getAttributes font1)]
    (doto attributes
      (.put TextAttribute/STRIKETHROUGH
            (if strikethrough TextAttribute/STRIKETHROUGH_ON false))
      (.put TextAttribute/UNDERLINE
            (if underline TextAttribute/UNDERLINE_ON -1)))
    (.setFont g2d (Font. attributes))
    (let [context (.getFontRenderContext g2d)
          string-bounds (.. g2d
                            getFontMetrics
                            (getStringBounds text g2d))
          delta-x (.x string-bounds)
          delta-y (.y string-bounds)
          height (.height string-bounds)
          width (.width string-bounds)
          params+ (complete-coordinates (merge params {:w width :h height}))]
     ; (println "a" delta-x width (select-keys params+ [:t :l :r :b :x :y]))
      (.drawString g2d text
                   (int (- (params+ :l) delta-x))
                   (int (- (params+ :t) delta-y))))))
  
;  (enable-anti-aliasing g2d)
;  (doseq [[type params & inner-items] items]

(defn- de-nest [inner-items]
  (if (seq? (first inner-items))
    (first inner-items)
    inner-items))

(defn draw
  "Draw in the Graphics2D reference, according to the data
   structure in the second argument"
  [g2d [type params & inner-items]]
  (when-not (:hidden params)
    (when (= type :graphics)
      (enable-anti-aliasing g2d)
      (when-let [fill (params :fill)]
        (doto g2d
          (.setColor (color-object fill))
          (.fill (.getClipRect g2d)))))
    (let [params+ (complete-coordinates params)]
      (with-g2d-state [g2d params+]
                      (if (#{:compound :graphics} type)
                        (doseq [inner-item (de-nest inner-items)]
                          (draw g2d inner-item))
                        (draw-primitive g2d (make-obj type params+) params+))))))

(defn canvas
  "Create a blank JPanel \"canvas\" for automatic painting. The
  canvas is automatically updated with the graphics description
  in the reference."
  [reference]
  (let [meta-atom (atom nil)
        panel (proxy [JPanel clojure.lang.IReference] []
                (paintComponent [^Graphics graphics]
                  (proxy-super paintComponent graphics)
                  (try
                    (draw graphics @reference)
                    (catch Throwable e (println (.printStackTrace e)))))
                (alterMeta [alter args]
                  (apply swap! meta-atom alter args))
                (resetMeta [m]
                  (reset! meta-atom m))
                (meta []
                  @meta-atom))]
    (add-watch reference panel (fn [_ _ _ _]
                                 (.repaint panel)))
    (alter-meta! panel assoc ::data-source reference)
    panel))

(defn get-shape [transform type params]
  (when-let [obj (make-obj type params)]
    (when (instance? Shape obj)
      (.createTransformedShape transform obj))))

(defn get-shapes
  ([[type params & inner-items :as data]]
    (get-shapes (AffineTransform.) data))
  ([transform [type params & inner-items]]
    (let [params+ (complete-coordinates params)
          new-transform (apply-transform transform params+)]
      (vec (concat
             [(get-shape new-transform type params+)
              params+]
             (for [inner-item (de-nest inner-items)]
               (get-shapes new-transform inner-item)))))))

(defn component-seq
  [[type params & inner-items]]
  (cons [type params] (mapcat component-seq (de-nest inner-items))))

(defn create-action [[shape widget-map] action-type]
  (let [offset-x (.. shape getBounds getX)
        offset-y (.. shape getBounds getY)]
    (fn [x y] ((widget-map action-type)
                 (- x offset-x) (- y offset-y)))))

(defn mouse-actions [action-type [x y] data]
  (->> data
       get-shapes
       component-seq
       (filter #(-> % second action-type))
       (filter #(-> % first (.contains x y)))
       (map #(create-action % action-type))))

(defn mouse-respond [e action-type data]
  (let [x (.getX e) y (.getY e)]                    
    (doseq [action (mouse-actions action-type [x y] data)]
      (action x y))))

(defn activate-dragging! [e data canvas]
  (let [x (.getX e) y (.getY e)]
    (alter-meta! canvas assoc ::dragging-action
                 (last (mouse-actions :on-mouse-drag [x y] data)))))

(defn stop-dragging! [canvas]
  (alter-meta! canvas dissoc ::dragging-action))

(defn continue-dragging [e canvas]
  (let [x (.getX e) y (.getY e)]
    (when-let [action ((meta canvas) ::dragging-action)]
      (action x y))))

(defn interactive-canvas
  [reference]
  (let [canvas (canvas reference)
        data @reference
        mouse-adapter
        (proxy [MouseAdapter] []
          (mouseClicked [e] (mouse-respond e :on-mouse-click data))
          (mousePressed [e] (activate-dragging! e data canvas)
                            (mouse-respond e :on-mouse-down data))
          (mouseReleased [e] (stop-dragging! canvas)
                             (mouse-respond e :on-mouse-up data))
          (mouseEntered [e] (mouse-respond e :on-mouse-enter data))
          (mouseExited [e] (mouse-respond e :on-mouse-exit data))
          (mouseDragged [e] (continue-dragging e canvas)))]
    (doto canvas
      (.addMouseListener mouse-adapter)
      (.addMouseMotionListener mouse-adapter))))

;(defn trigger-mouse-events [{:keys [type x y]}]
  
  

;; interaction


;; test

(def grafix (atom nil))

(defn canvas-frame [reference]
  (let [panel (interactive-canvas reference)]
    (doto (JFrame. "canvas")
      (.. getContentPane (add panel))
      (.setBounds 10 10 500 500)
      (widgets/setup-fullscreen)
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
                 (catch Exception e nil)))
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
    [:text
     {:text "Navigate"
      :color :blue
      :x 100 :y 10
}]
   [:compound {:x 180 :y 300 :scale 1.0 :alpha 0.9 :rotate 0} 
;    [:round-rect
;     {:y 2 :w 150 :h 30
;      :arc-radius 10
;      :fill {:color 0x4060D0}
;      :scale 1.0
;      :alpha 1.0
;      :stroke {:width 2 :color :white}
;      :rotate 0}]
    ]
   [:compound
    {:x 150 :y 150 :rotate 0 :scale 2 }
    [:ellipse
     {:w 41 :h 41 :fill {:color :pink} :alpha 0.9
      :x 0 :y 0
      :mouse #(swap! grafix update-in [4 1] merge {:x %1 :y %2})
      :stroke {:width 4 :color 0xE06060}}]
    [:line
     {:w 19 :h 19 :scale 1.0 :color :red :alpha 1.0
      :stroke {:width 7 :cap :butt}
      }]
    [:line
     {:x 0 :y 0 :w -19 :h 19 :fill false :color :red :alpha 1.0
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




