(ns slide-explorer.canvas
  (:import (java.awt AlphaComposite BasicStroke Color Font Graphics 
                     GraphicsEnvironment
                     Graphics2D GradientPaint Image
                     Polygon RenderingHints Shape)
           (java.awt.event MouseAdapter)
           (java.awt.font TextAttribute)
           (java.awt.image BufferedImage)
           (javax.swing JFrame JPanel JScrollPane JTextArea)
           (javax.swing.event DocumentListener)
           (java.awt.geom AffineTransform
                          Arc2D Arc2D$Double Area Ellipse2D$Double
                          Line2D$Double Path2D$Double
                          Point2D$Double
                          Rectangle2D$Double
                          RoundRectangle2D$Double))
    (:require [slide-explorer.widgets :as widgets]
              [slide-explorer.paint :as paint]
              [slide-explorer.bind :as bind]))

; Create a default font render context, that can be rebound.
; Used by text-primitive widget to get measurements of strings
; on screen.
(def ^:dynamic *font-render-context*
  (.. GraphicsEnvironment getLocalGraphicsEnvironment
      (createGraphics (BufferedImage. 1 1 BufferedImage/TYPE_3BYTE_BGR))
      getFontRenderContext))

(def ^:dynamic *canvas-error*)

(defn show
  "Prints the value x to std out, and returns x."
  [x]
  (do (pr x)
      x))

(defn now
  "Returns the current time in milliseconds"
  []
  (System/currentTimeMillis))

(defn degrees-to-radians
  "Convert degrees to radians."
  [deg]
  (* deg (/ Math/PI 180)))

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

(defn set-transform!
  "Modifes the affine transform. Can be a Grahpics2D or an
   AffineTransform object."
  [transform-object {:keys [x y rotate scale scale-x scale-y]}]
  (if (or x y rotate scale scale-x scale-y)
    (doto transform-object
      (.translate (double (or x 0)) (double (or y 0)))
      (.rotate (degrees-to-radians (or rotate 0.0)))
      (.scale (or scale-x scale 1.0) (or scale-y scale 1.0)))
    transform-object))

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

(defn set-gradient
  "Sets the current gradient fill."
  [g2d {:keys [x1 y1 color1 x2 y2 color2]}]
  (.setPaint g2d (GradientPaint. x1 y1 (color-object color1)
                                 x2 y2 (color-object color2))))
  
(defn set-alpha
  "Set the current alpha (transparency) for a Graphics2D instance."
  [g2d alpha]
  (when (and alpha (< alpha 1))
    (->> (.. g2d getComposite getAlpha)
         (* alpha)
         (AlphaComposite/getInstance AlphaComposite/SRC_ATOP)
         (.setComposite g2d))))

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

(defn stroke?
  "Determines if stroke definition describes a non-zero stroke width."
  [stroke]
  (let [width (:width stroke)]
    (or (nil? width) (pos? width))))

;; Graphics2D state

(defn set-g2d-state
  "Modifies the drawing state of the Graphics2D instance."
  [g2d {:keys [alpha color stroke transform]}]
  (set-color g2d (or color :black))
  (set-alpha g2d alpha)
  (set-stroke g2d stroke)
  (.transform g2d transform))

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

(defmacro with-g2d-state [[g2d params] & body]
  `(with-g2d-state-fn ~g2d ~params (fn [] ~@body)))

;; widgets ;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn update-in-if
  "Like update-in, but only applies the
   function if the keys are present. Otherwise
   nothing happens."
  [m ks f & args]
  (if (get-in m ks)
    (apply update-in m ks f args)
    m))

(defn map-vals
  "Updates each value v in a map, where
   v -> (apply f v args)."
  [f m & args]
  (reduce (fn [m1 k] (apply update-in m1 [k] f args))
          m (keys m)))

(defn update-tree
  [data ks f & args]
  (cond (empty? data)
          data
        (sequential? data)
          (map #(apply update-tree % ks f args) data)
        (map? data)
          (apply update-in-if ks f args)))

(def primitive-widgets #{:shape :image :text-primitive})

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

(def complete-coordinates
  (fn [{:keys [l left t top r right b bottom
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
           (into {})))))

;; Little widget language

(defmulti widget :type)

(defn expand
  "Expand all widget data into primitive widgets."
  [data]
  (cond (empty? data)
          nil
        (sequential? data)
          (map expand data)
        (map? data)
          (first (drop-while #(-> % :type primitive-widgets not)
                     (iterate widget data)))))

(defn memoize-simple
  "Memoizes the last result of a function. If a subsequent call
   to a function provides new arguments, then the memoized value
   is updated."
  [function]
  (let [last-computation-atom (atom [::nothing nil])]
    (fn [& args]
      (let [[last-args last-result] @last-computation-atom]
        (if (identical? args last-args)
          last-result
          (let [result (apply function args)]
            (reset! last-computation-atom [args result])
            result))))))

(def expand-memo (memoize-simple expand))
        
(defn affine-transform
  "Generates an AffineTransform object from a map containing
   various geometric descriptions."
  [{:keys [transform l t x y w h
                         scale-x scale-y scale rotate] :as m}]
   (if (or x y w h rotate scale scale-x scale-y)
    (doto (.clone (or transform (AffineTransform.)))
      (.translate (double (or x 0))
                  (double (or y 0)))
      (.rotate (degrees-to-radians (or rotate 0.0)))
      (.scale (or scale-x scale 1.0)
              (or scale-y scale 1.0))
      (.translate (double (/ (or w 0) -2))
                  (double (/ (or h 0) -2))))
    transform))

(defn shape
  [{:keys [transform l t x y
           scale-x scale-y scale rotate] :as m} shape-obj]
  (let [m+ (complete-coordinates m)
        new-transform (affine-transform m+)]
    (-> m+
        (assoc :transform new-transform
               :type :shape
               :shape shape-obj)
        (update-in-if [:children]
                   (fn [children]
                     (->> children
                          (map #(assoc % :transform new-transform))
                          expand))))))

;; basic widgets

(defmethod widget :arc
  [{:keys [w h start-angle arc-angle arc-boundary] :as m}]
  (shape m (Arc2D$Double. 0 0 w h start-angle arc-angle
                          ({:closed Arc2D/PIE
                            true Arc2D/PIE
                            :pie Arc2D/PIE
                            :chord Arc2D/CHORD
                            :open Arc2D/OPEN}
                                    (or arc-boundary :open)))))

(defmethod widget :ellipse
  [{:keys [w h] :as m}]
  (shape m (Ellipse2D$Double. 0 0 w h)))

(defmethod widget :line
  [{:keys [w h] :as m}]
  (shape m (Line2D$Double. 0 0 w h)))

(defmethod widget :point
  [m]
  (shape m (Line2D$Double. 0 0)))

(defmethod widget :polygon
  [{:keys [vertices] :as m}]
  (let [xs (int-array (map :x vertices))
        ys (int-array (map :y vertices))
        n (count vertices)]
    (shape m (Polygon. xs ys n))))

(defmethod widget :polyline
  [{:keys [vertices] :as m}]
  (let [path (Path2D$Double.)]
    (let [{:keys [x y]} (first vertices)]
      (.moveTo path x y))
    (doseq [{:keys [x y]} (rest vertices)]
      (.lineTo path x y))
    (shape m path)))

(defmethod widget :rect
  [{:keys [w h] :as m}]
  (shape m (Rectangle2D$Double. 0 0 w h)))

(defmethod widget :round-rect
  [{:keys [w h arc-radius arc-width arc-height] :as m}]
  (shape m (RoundRectangle2D$Double.
                 0 0 w h
                 (or arc-radius arc-width)
                 (or arc-radius arc-height))))

(defn font-object
  "Create a java font object with various properties."
  [{:keys [name size bold italic underline strikethrough]
    :or {name Font/SANS_SERIF size 18} :as font}]
  (let [style (bit-or (if bold Font/BOLD 0)
                      (if italic Font/ITALIC 0))
        font-temp (Font. name style size)
        attributes (.getAttributes font-temp)]
    (doto attributes
      (.put TextAttribute/STRIKETHROUGH
            (if strikethrough TextAttribute/STRIKETHROUGH_ON false))
      (.put TextAttribute/UNDERLINE
            (if underline TextAttribute/UNDERLINE_ON -1)))
    (Font. attributes)))

(defn string-bounds [font-object text]
  "Measures the true bounds of a string."
  (-> font-object
    (.createGlyphVector *font-render-context* text)
    .getVisualBounds))

(defn string-metrics
  "Measure the bounds of a string."
  [font-obj text]
  (let [bounds-M (string-bounds font-obj "M")
        bounds (string-bounds font-obj text)]
    {:h (.getHeight bounds-M)
     :w (.getWidth bounds)
     :shape-obj bounds}))

(def string-metrics-memo (memoize string-metrics))

(defmethod widget :text
  [{:keys [text font] :as m}]
  (let [font-obj (font-object font)
        m+ (merge m (string-metrics font-obj text))
        m++ (complete-coordinates m+)
        new-transform (affine-transform m++)]
    (-> m++
        (assoc :type :text-primitive
               :transform new-transform
               :text text
               :font-obj font-obj)
        (update-in-if [:children]
                   (fn [children]
                     (->> children
                          (map #(assoc % :transform new-transform))
                          expand))))))

;; compound widgets

(defmethod widget :square
  [{:keys [side w h] :as m}]
  (let [s (or side w h)]
    (assoc m :type :rect :w s :h s)))

(defmethod widget :compound
  [m]
  (assoc m :type :rect :stroke {:width 0}))

;; nested children

(defn children
  "Produce a sequence of children, defined in a map
   or in a sequence."
  [widget]
  (when-let [children (:children widget)]
    (if (map? children)
      (vals children)
      (seq children))))

(defn denest
  "De-nest all the children."
  [widgets]
  (mapcat
    (fn [widget]
      (cons (dissoc widget :children)
            (denest (children widget))))
    widgets))

;; drawing

(defmulti draw-primitive (fn [g2d params] (:type params)))

(defmethod draw-primitive nil [_ _ ])

(defmethod draw-primitive :shape
  [g2d {:keys [shape color fill stroke id input] :as widget}]
  (when-let [fill-color (if (keyword? fill)
                          fill
                          (:color fill))]
    (doto g2d
      (set-color fill-color)
      (.fill shape)))
  (when-let [gradient (:gradient fill)]
    (doto g2d
      (set-gradient gradient)
      (.fill shape)))
  (when (stroke? stroke)
    (doto g2d
      (set-color (or (:color stroke) color :black))
      (.draw shape))))

(defmethod draw-primitive :image
  [g2d {:keys [image] :as params}]
  (let [w (or (:w params) (.getWidth image))
        h (or (:h params) (.getHeight image))]
   (with-g2d-state [g2d params]
      (.drawImage g2d image (- (/ w 2)) (- (/ h 2)) w h nil))))

(defmethod draw-primitive :text-primitive
  [g2d {:keys [text font-obj h] :as params}]
  (.setFont g2d font-obj)
  ;(println h)
  (.drawString g2d text (float 0) (float h)))

(defn draw
  "Draw in the Graphics2D reference, according to the data
   structures in the second argument"
  [g2d widgets]
  (enable-anti-aliasing g2d)
  (binding [*font-render-context* (.getFontRenderContext g2d)]
    (dorun
      (map #(with-g2d-state [g2d %]
                            (draw-primitive g2d %))
           (denest (expand-memo widgets))))))

(defn draw-error
  "Takes an error object, and draws it on the graphics object."
  [graphics e]
  (draw graphics
        [{:type :text
          :left 0
          :top 20
          :text (.getMessage e)}]))  

(defn canvas
  "Create a blank JPanel \"canvas\" for automatic painting. The
  canvas is automatically updated with the graphics description
  in the reference."
  [reference]
  (let [meta-atom (atom nil)
        panel (proxy [JPanel clojure.lang.IReference] []
                (paintComponent [^Graphics graphics]
                  (proxy-super paintComponent graphics)
                  (paint/paint-buffered graphics
                    #(try
                       (draw % @reference)
                       (catch Throwable e (draw-error % e)))))
                (alterMeta [alter args]
                  (apply swap! meta-atom alter args))
                (resetMeta [m]
                  (reset! meta-atom m))
                (meta []
                  @meta-atom))]
    (add-watch reference panel (fn [_ _ old-val new-val]
                                 (when (not= old-val new-val)
                                   (.repaint panel))))
    (alter-meta! panel assoc ::data-source reference)
    panel))

;; mouse inputs

(defn create-action [{:keys [shape] :as widget} action-type]
  (let [offset-x (.. shape getBounds getX)
        offset-y (.. shape getBounds getY)]
    (fn [x y] ((widget action-type)
                 (- x offset-x) (- y offset-y)))))

(defn shapes-containing-point
  "Returns the shapes in data that contain the
   point [x y]."
  [[x y] data]
  (->> data
       (filter #(-> (.createTransformedShape
                       (% :transform) (% :shape))
                    (.contains x y)))))

(defn shape-by-action [action-type [x y] data]
  (->> data
       expand-memo
       (filter action-type)
       (shapes-containing-point [x y])))

(defn run-mouse-actions [e action-type reference]
  (let [x (.getX e) y (.getY e)]                    
    (doseq [shape (shape-by-action action-type [x y] @reference)]
      ((:action-type shape) x y))))

(defn mouse-is-over
  "Returns the shapes that contain the point [x y] and
   expect a hovering mouse."
  [[x y] data]
  (->> data
       expand-memo
       (remove #(empty? (select-keys % [:mouse-in :mouse-out])))
       (shapes-containing-point [x y])))

(defn handle-mouse-move [e reference canvas]
  (let [data @reference
        last-widgets-under-mouse ((meta canvas) ::last-widgets-under-mouse)
        widgets-under-mouse (set (map #(select-keys % [:mouse-in :mouse-out :shape])
                                      (mouse-is-over [(.getX e) (.getY e)] data)))
        in (clojure.set/difference widgets-under-mouse last-widgets-under-mouse)
        out (clojure.set/difference last-widgets-under-mouse widgets-under-mouse)]
    (alter-meta! canvas assoc ::last-widgets-under-mouse
                 widgets-under-mouse)
    (doseq [action (map :mouse-in in)] (when action (action)))
    (doseq [action (map :mouse-out out)] (when action (action)))))
    
(defn initiate-dragging!
  "Initiates a dragging session."
  [e reference canvas]
  (let [x0 (.getX e) y0 (.getY e)
        dragee (last (shape-by-action :on-mouse-drag [x0 y0] @reference))
        {:keys [x y]} dragee
        dx (- x x0) dy (- y y0)]
    (alter-meta! canvas assoc
                 ::dragging-action
                 (:on-mouse-drag dragee)
                 ::dragging-offset [dx dy])))

(defn continue-dragging!
  "Updates a dragging session."
  [e canvas]
  (let [x (.getX e) y (.getY e)
        canvas-meta (meta canvas)
        [dx dy] (::dragging-offset canvas-meta)
        xnew (+ x dx) ynew (+ y dy)]
    (when-let [action (canvas-meta ::dragging-action)]
      (action xnew ynew))))

(defn finish-dragging!
  "Halts a dragging session."
  [canvas]
  (alter-meta! canvas dissoc ::dragging-action))

(defn interactive-canvas
  [reference]
  (let [canvas (canvas reference)
        data @reference
        mouse-adapter
        (proxy [MouseAdapter] []
          (mouseClicked [e] (run-mouse-actions e :mouse-click reference))
          (mousePressed [e] (initiate-dragging! e reference canvas)
                            (run-mouse-actions e :mouse-down reference))
          (mouseReleased [e] (finish-dragging! canvas)
                             (run-mouse-actions e :mouse-up reference))
          (mouseMoved [e] (handle-mouse-move e reference canvas))
          (mouseDragged [e] 
                        (continue-dragging! e canvas)
                        (handle-mouse-move e reference canvas)))]
    (doto canvas
      (.addMouseListener mouse-adapter)
      (.addMouseMotionListener mouse-adapter))))

;; animation

(defn animate!
  "Slowly alters reference at value-address, linearly
   with time from min-val to max-val."
  [reference value-address duration min-val max-val]
  (let [start-time (now)]
    (loop []
      (let [elapsed (- (now) start-time)
            value (+ min-val
                     (* (- max-val min-val)
                        (min 1.0 (/ elapsed (double duration)))))]
        (swap! reference (fn [data]
                           (assoc-in data value-address value)))
        (when (< elapsed duration)
          (Thread/sleep 15)
          (recur))))))
  
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
              (eval (read-string (.getText text-area))))
                 (catch Exception e nil)))
    (.setText text-area (with-out-str (clojure.pprint/pprint @data-atom)))
    (doto (JFrame. "Data Editor")
      (.setBounds 10 10 400 400)
      (.. getContentPane (add scroll-pane))
      .show)))

(def test-data
  [{:type :square
    :fill :green
    :children [{:type :rect :l 80 :t 70 :w 10 :h 4 :fill :blue}]
    :l 320
    :t 100
    :arc-radius 5
    :rotate 0
    :scale-y 2
    :side 100}
   {:type :rect
    :x 100
    :y 100
    :w 100
    :h 100 :children
   [{:type :text :rotate 0
    :text "Hello"
     :font {:size 24}
    :color :red
    :l 0 :y 0
    }
      {:type :arc
       :fill :pink
       :alpha 0.6
    :mouse-in #(swap! grafix assoc-in [2 :fill] :red)
    :mouse-out #(swap! grafix assoc-in [2 :fill] :pink)
    :on-mouse-drag (fn [x y]
                     (swap! grafix
                            #(-> %
                                 (assoc-in [2 :x] x)
                                 (assoc-in [2 :y] y))))
    :scale 3
      :mouse-up (fn [x y] )
     :x 200
     :y 200
     :w 50
     :h 70
     :start-angle 20
     :arc-angle 140
     :arc-boundary :pie}]}
   ])

(defn test-binding []
  (bind/bind-range [grafix [2 :x] [100 500]] [grafix [1 :y] [100 300]])
  (bind/bind-range [grafix [2 :y] [100 300]] [grafix [1 :rotate] [0 180]])
  (bind/bind-linear [grafix [2 :x]] [0.1 -50] [grafix [0 :rotate]])
  (bind/follow-function [grafix [2 :x]] #(str "x=" (int (+ 0.5 %))) [grafix [1 :text]])
  (bind/bind-map [grafix [2 :fill]] {:red :blue :pink :cyan} [grafix [1 :color]])
  (bind/follow-function [grafix [2 :y]] #(/ % 10) [grafix [0 :children 0 :w]])
  (bind/follow-function [grafix [2 :x]] #(/ % 25) [grafix [0 :scale-y]]))
  

(defn run-test []
  (reset! grafix test-data)
  (canvas-frame grafix)
  (add-watch (var test-data) :update (fn [_ _ _ value]
                                 (reset! grafix test-data))))