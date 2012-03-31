(ns slide-explorer.main
  (:import [java.awt.geom AffineTransform]))


(defonce tiles-to-stage-transform (atom (AffineTransform.)))

(defn square
  "Get the square of a number."
  [x]
  (* x x))

(defn distance-squared
  "Get the squared distance between to points in cartesian space."
  [[x1 y1] [x2 y2]]
  (+ (square (- x1 x2))
                (square (- y1 y2))))

(defn tile-ring
  "Compute the tiles in a square 'ring' of a given radius around an origin tile."
  [[origin-x origin-y] radius]
  (if (zero? radius)
    (list [origin-x origin-y])
    (let [x-values (range (- origin-x radius) (+ origin-x radius 1))
          y-values (range (- origin-y radius) (+ origin-y radius 1))
          trim #(rest (drop-last %))]
      (concat (map #(vector (apply min x-values) %) y-values)
              (map #(vector (apply max x-values) %) y-values)
              (map #(vector % (apply min y-values)) (trim x-values))
              (map #(vector % (apply max y-values)) (trim x-values))))))

(defn tile-rings
  "Generate a series of concentric square 'rings' around an origin tile."
  [[origin-x origin-y]]
  (map #(tile-ring [origin-x origin-y] %) (range)))

(defn sort-and-partition-by
  "First sort, then partition a collection by a given keyfn."
  ([keyfn coll]
    (partition-by keyfn (sort-by keyfn coll)))
  ([keyfn comp coll]
    (partition-by keyfn (sort-by keyfn comp coll))))

(defn tile-arcs [[center-x center-y] tile-rings]
  (apply concat
         (for [ring tile-rings]
           (sort-and-partition-by #(distance-squared [center-x center-y] %) ring))))

(defn tile-priority-list [[location-x location-y] tile-arcs]
  (apply concat
         (for [arc tile-arcs]
           (sort-by #(distance-squared [location-x location-y] %) arc))))

(defn nearest-tile [[center-x center-y]]
  [(Math/round center-x) (Math/round center-y)])
  
(defn next-tile [[center-x center-y] [location-x location-y] finished-tiles]
  (->> (tile-rings (nearest-tile [center-x center-y]))
       (tile-arcs [center-x center-y])
       (tile-priority-list [location-x location-y])
       (remove #(contains? finished-tiles %))
       first))