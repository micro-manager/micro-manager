(ns slide-explorer.contrast
  (require [slide-explorer.canvas :as canvas]
           [slide-explorer.image :as image]
           [slide-explorer.reactive :as reactive]
           [slide-explorer.utils :as utils]))


(def data-atom (atom nil))

(def channel-atom (atom {:name "Cy3" :color :red :min 100 :max 200}))

(def allowed-colors [:red :green :blue :cyan :magenta :yellow :white])

(defn next-color [current-color]
  (let [color-cycle (conj allowed-colors (first allowed-colors))]
    (->> color-cycle
         (drop-while #(not= current-color %))
         second)))

(defn cycle-color []
  (swap! channel-atom update-in [:color] next-color))

(defn bar-graph-vertices [data width height]
  (let [peak (apply max data)
        data-vec (vec data)
        n (count data)
        dx (/ width n)]
    (concat
      [{:x 0 :y 0}]
      (apply concat
             (for [i (range n)]
               (let [y (* height (/ (data-vec i) (double peak)))]
                 [{:x (* dx i)
                   :y y}
                  {:x (* dx (inc i))
                   :y y}])))
      [{:x width :y 0}])))

(defn color-picker [side]
  [:compound
   {:x 10 :y 41}
   (let [n (count allowed-colors)]
     [:compound {:x 0 :y 0 :w 50 :h (* side (inc n))}
      (for [i (range n)]
        [:rect {:t (* i side) :l 0 :h side :w side
                :stroke {:width 0}
                :fill {:color (allowed-colors i)}}])])
   [:rect {:l 0 :t 0 :w 30 :h 210 :color :black :stroke {:width 1}}]
   [:rect {:l 4 :t 4 :w 30 :h 210 :color :black :stroke {:width 0}
           :fill {:color 0x303030}}]])

(defn color-slider [id pos fill-color]
  {:type :polygon 
   :id id
   :vertices [{:x pos :y 0}
              {:x (- pos 7) :y 16}
              {:x (+ pos 7) :y 16}]
   :color :gray
   :stroke {:width 2}
   :fill {:color fill-color}})
      
(defn update-limit [{:keys [min max] :as channel-map}
                    handle val]
  (let [val (canvas/clip-value val 0 255)]
    (if (= handle :min)
      (assoc channel-map
             :min val
             :max (canvas/clip-value (inc val) max 255))
      (assoc channel-map
             :max val
             :min (canvas/clip-value (dec val) 0 min)))))           

(defn update-limit! [handle val]
  (swap! channel-atom update-limit handle val))

(defn contrast-graph [data width height {:keys [name color min max]}]
  (let [n (count data)
        xmin (* width (/ min n))
        xmax (* width (/ max n))]    
    [{:type :compound :t 0 :l 0 :w 1000 :h 1000 :fill :dark-gray
      :children
      [{:type :compound :l 100 :t 25 :w 100 :h 100 :scale-y -1
        :children
        [{:type :polygon
          :id :graph
          :vertices (bar-graph-vertices data width height)
          :fill {:gradient
                 {:color1 :black :x1 xmin :y1 0
                  :color2 color :x2 xmax :y2 0}}
          :color :dark-gray
          :stroke {:width 0 :cap :butt}}
         {:type :compound :t height :h 20 :l 0 :w width :scale-y 1
          :children [(color-slider :max-handle xmax color)]}
         {:type :compound :b 0 :h 20 :l 0 :w width :scale-y -1
          :children [(color-slider :min-handle xmin :black)]}]}
       {:type :text :text name :l 45 :y 25 :color :white :font {:size 18}}
       {:type :compound :l 10 :t 50 :w 0 :h 0
        :children
        [{:type :text :text (str "Min: " (int min))
          :l 0 :t 0
          :color :white :font {:size 12}}
         {:type :text :text (str "Max: " (int max))
          :l 0 :t 15
          :color :white :font {:size 12}}]}
       {:type :rect :l 10 :t 10 :w 30 :h 30 :fill {:color color}
        :stroke {:width 1 :color :white}}]}]))

(def simple 
  [{:type :text :t 100 :l 100 :w 100 :h 100 
    :text "Hello"
    :rotate 0}])

(def test-graphics (atom nil))

(defn test-data []
  (repeatedly 256 #(long (rand 256))))  

(defn update-contrast-graph [_ _]
  (reset! test-graphics (contrast-graph @data-atom 512 100
                                        @channel-atom)))

(defn gaussian [x x0 sigma A]
  (* A (Math/exp (- (/ (Math/pow (- x x0) 2) (Math/pow sigma 2))))))

(defn handle-settings []
  (reactive/handle-update data-atom update-contrast-graph)
  (reactive/handle-update channel-atom update-contrast-graph)) 

(defn test-frame []
  (let [panel (canvas/canvas-frame test-graphics)]
    (handle-settings)
    (reset! data-atom (test-data))))

(defn random-test [secs]
  (future
    (let [t0 (System/currentTimeMillis)]
    (while (< (- (System/currentTimeMillis) t0)
              (int (* 1000 secs)))
      (Thread/sleep 10)
      (reset! data-atom (test-data))))))
