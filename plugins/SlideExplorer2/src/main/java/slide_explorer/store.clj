(ns slide-explorer.store
  (:require [slide-explorer.image :as image]
            [slide-explorer.tiles :as tiles]
            [slide-explorer.tile-cache :as tile-cache]))

(defn child-index
  "Converts an x,y index to one in a child (1/2x zoom)."
  [n]
  (tiles/floor-int (/ n 2)))

(defn child-indices [indices]
  (-> indices
     (update-in [:nx] child-index)
     (update-in [:ny] child-index)
     (update-in [:zoom] / 2)))

(defn propagate-tile [memory-tiles-atom child parent]
  (let [child-tile (tile-cache/load-tile memory-tiles-atom child)
        parent-tile (tile-cache/load-tile memory-tiles-atom parent)
        new-child-tile (image/insert-half-tile
                         parent-tile
                         [(even? (:nx parent))
                          (even? (:ny parent))]
                         child-tile)]
    (tile-cache/add-tile memory-tiles-atom child new-child-tile)))

(defn add-to-memory-tiles [memory-tiles-atom indices tile min-zoom]
  (let [full-indices (assoc indices :zoom 1)]
    (tile-cache/add-tile memory-tiles-atom full-indices tile)
    (loop [child (child-indices full-indices)
           parent full-indices]
      (when (<= min-zoom (:zoom child))
        (propagate-tile memory-tiles-atom child parent)
        (recur (child-indices child) child)))))