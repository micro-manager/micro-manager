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
     [:compound {:x 20 :y (+ 20 height) :scale-y -1}
      [:polygon
       {:vertices (bar-graph-vertices data width height)
        :fill {:gradient
               {:color1 :black
                :x1 xmin
                :y1 0
                :color2 color
                :x2 xmax
                :y2 0}}
        :color :dark-gray
        :stroke {:width 1.0
                 :cap :butt}}]
      [:rect {:x xmin :y -6 :w 6 :h 6
              :color :gray
              :fill {:color :black}}]
      [:rect {:x xmax :y -6 :w 6 :h 6
              :color :gray
              :fill {:color color}}]]]))

(defonce test-graphics (atom nil))

(defn test-data []
  (repeatedly 256 #(long (rand 256))))  

(defonce data-atom (atom nil))

(defonce channel-atom (atom nil))

(defn update-contrast-graph [_ _]
  (reset! test-graphics (contrast-graph @data-atom 512 100
                                        @channel-atom)))

(defn handle-settings []
  (reactive/handle-update data-atom update-contrast-graph)
  (reactive/handle-update channel-atom update-contrast-graph)) 

(defn test-frame []
  (canvas/canvas-frame test-graphics)
  (handle-settings))

(defn random-test [n]
  (dotimes [_ n]
    (Thread/sleep 10)
    (reset! data-atom (test-data))))
