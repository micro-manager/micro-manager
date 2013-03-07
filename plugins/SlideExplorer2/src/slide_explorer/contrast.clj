(ns slide-explorer.contrast
  (require [slide-explorer.canvas :as canvas]
           [slide-explorer.reactive :as reactive]))

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

(defn contrast-graph [data width height {:keys [color min max]}]
  (let [n (count data)
        xmin (* width (/ min n))
        xmax (* width (/ max n))]
    [:graphics {}
     [:compound {:x 100 :y (+ 10 height) :scale-y -1}
      [:rect {:fill {:color :dark-gray}
              :l -150 :t -100
              :width (+ 200 width) :height (+ 200 height)}]
      [:polygon
       {:id :graph
        :vertices (bar-graph-vertices data width height)
        :fill {:gradient
               {:color1 :black
                :x1 xmin
                :y1 0
                :color2 color
                :x2 xmax
                :y2 0}}
        :color :dark-gray
        :stroke {:width 0
                 :cap :butt}}]
      [:rect {:id :min-handle
              :x xmin :y -6 :w 8 :h 8
              :color :gray
              :stroke {:width 1}
              :fill {:color :black}}]
      [:rect {:id :max-handle
              :x xmax :y -6 :w 8 :h 8
              :color :gray
              :stroke {:width 1}
              :fill {:color color}}]]
     [:text {:text "Cy3" :l 50 :t 12 :color :white}]
     [:rect {:l 10 :t 10 :w 30 :h 30 :fill {:color color}
             :stroke {:width 2 :color :white}}]]))

(defonce test-graphics (atom nil))

(defn test-data []
  (repeatedly 256 #(long (rand 256))))  

(defonce data-atom (atom nil))

(def channel-atom (atom {:color :red :min 100 :max 200}))

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
    (handle-settings)))

(defn random-test [n]
  (dotimes [_ n]
    (Thread/sleep 10)
    (reset! data-atom (test-data))))
