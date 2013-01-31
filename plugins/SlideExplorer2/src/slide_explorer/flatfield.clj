(ns slide-explorer.flatfield
  (:require [slide-explorer.tile-cache :as tile-cache]
            [slide-explorer.image :as image]))


(defn map-vals [f m]
  (into {}
        (for [[k v] m]
          [k (f v)])))

(defn unzoomed-indices-by-channel [cache-value]
  (->> cache-value
       keys
       (filter #(= 1 (:zoom %)))
       (group-by :nc)))       

(defn images-in-cache [cache indices]
  (map #(tile-cache/get-tile cache % false) indices))

(defn flat-field [images]
  (-> (image/intensity-projection :median images)
      (image/gaussian-blur 50)
      image/normalize-to-max))

(defn flat-field-by-channel [cache-value]
  (->> cache-value
       (unzoomed-indices-by-channel)
       (map-vals (partial images-in-cache cache))
       (map-vals flat-field)))

       
;(defn run-correction [

;; testing

(defn test-generate []
  (->> @slide-explorer.view/mt
       flat-field-by-channel
       (map-vals image/show)))

(defn test-select []
  (images-in-cache-for-flatfielding slide-explorer.view/mt))
                      
                      
                      

;; flat field determination

(comment 
(def flat-field-positions
  (map #(* 1/8 %) (range -4 5)))

(def flat-field-coords
  (for [x flat-field-positions
        y flat-field-positions]
    [x y]))

(defn flat-field-scaled-coords []
  (let [width (mm/core getImageWidth)
        height (mm/core getImageHeight)]
    (map #(let [[x y] %] (Point. (* width x) (* height y)))
         flat-field-coords))) 

(defn flat-field-stage-coords [scaled-coords]
  (let [transform (origin-here-stage-to-pixel-transform)]
    (map #(affine/inverse-transform % transform)
         scaled-coords)))
    
(defn flat-field-acquire []
  (let [acq-settings (create-acquisition-settings)
        scaled (flat-field-scaled-coords)
        to-and-fro (concat scaled (reverse scaled))]
    (for [coords (flat-field-stage-coords to-and-fro)]
      (acquire-at coords acq-settings))))

(defn flat-field-save [images]
  (let [dir (io/file "flatfield")]
    (.mkdirs dir)
    (dorun
      (loop [images0 (map :proc (flatten images))
             i 0]
        (println i)
        (when-let [image (first images0)]
          (slide-explorer.disk/write-tile dir {:i i} (first images0)))
        (when-let [more (next images0)]
          (recur more (inc i)))))))
  )