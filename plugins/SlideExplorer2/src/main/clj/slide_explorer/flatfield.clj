(ns slide-explorer.flatfield
  (:require [clojure.java.io :as io]
            [slide-explorer.tile-cache :as tile-cache]
            [slide-explorer.disk :as disk]
            [slide-explorer.image :as image]
            [slide-explorer.store :as store]))

(defn map-vals
  "Apply function f 'in place' to each value in map."
  [f m]
  (into {}
        (for [[k v] m]
          [k (f v)])))

(defn unzoomed-indices-by-channel
  "Get all tile indices in memory cache that have
   zoom-level of 1. Return grouped by channel index."
  [cache-value]
  (->> cache-value
       keys
       (filter #(= 1 (:zoom %)))
       (group-by :nc)))       

(defn gain-image
  "Compute a gain image from a set of raw images."
  [images]
  (let [sigma-image (image/intensity-projection
                      :standard-deviation images)]
    (image/divide sigma-image
                    (image/center-pixel sigma-image))))

(defn offset-image
  "Compute an offset image from a set of raw images and
   a gain image."
  [images gain-image]
  (let [mean-image (image/intensity-projection
                     :mean images)
        center-mean (image/center-pixel mean-image)]
    (image/convert-to-type-like
      (image/combine-processors :subtract
                                mean-image
                                (image/multiply gain-image center-mean))
      mean-image)))

(defn flatfield-corrections
  "Take a sequence of images at 1x zoom, all from the same channel,
   and compute an offset and gain image."
  [images]
  (let [gain-image (gain-image images)]
    {:gain (image/gaussian-blur gain-image 50)
     :offset (image/gaussian-blur (offset-image images gain-image) 50)}))

(defn mipmap-step
  "Zoom out a processor by a factor of two, and then produce
   a repeating 2x2 grid from the image."
  [processor]
  (let [dest (image/black-processor-like processor)]
    (doseq [a [false true] b [false true]]
      (image/insert-half-tile! dest processor [a b]))
    dest))

(defn mipmap-series
  "Make a series of flatfield correction images for use with
   mipmapped tiles."
  [n processor]
  (vec (take n (iterate mipmap-step processor))))

(defn mipmap-flatfield-corrections
  [n images]
  (let [{:keys [offset gain]} (flatfield-corrections images)
        offset-series (mipmap-series n offset)
        gain-series (mipmap-series n gain)]
    (zipmap
      (take n (iterate (partial * 1/2) 1))
      (map #(hash-map :offset %1 :gain %2) offset-series gain-series))))

(defn correct-image
  "Apply a flatfield correction to an image, returning
   the corrected image."
  [image {:keys [offset gain] :as correction}]
  (let [numerator  
        (if offset
          (image/combine-processors
            :subtract image offset)
          image)]
    (if gain
      (image/convert-to-type-like
        (image/combine-processors :divide numerator gain)
        image)
      numerator)))

(defn correct-indexed-image
  "Correct an image with given tile index, using the flatfield-corrections-map."
  [{:keys [zoom nc] :as index} image flatfield-corrections-map]
  (correct-image image (get-in flatfield-corrections-map [nc zoom])))

;; interacting with the cache

(defn images-in-cache
  "Return a sequence of tiles from cache, corresponding to
   a set of indices."
  [cache indices]
  (map #(tile-cache/get-tile cache % false) indices))

(defn unzoomed-images
  "Returns a sequence of unzoomed tiles from the cache."
  [cache]
  (->> @cache
       (unzoomed-indices-by-channel)
       (map-vals (partial images-in-cache cache))))

(defn compute-flatfield-corrections
  [cache]
  (->> cache
       unzoomed-images
       (map-vals #(mipmap-flatfield-corrections 8 %))))

(defn update-flatfield-corrections
  [cache]
  (alter-meta! cache assoc ::corrections (compute-flatfield-corrections cache)))

(defn get-flatfield-corrections
  "Get the corrections map associated with raw tile cache."
  [cache]
  (::corrections (meta cache)))

;; Can I continuously update the flatfield correction image for each channel,
;; create zoomed-out versions, and then apply correction on the fly as
;; I do with channel overlays? Memoization key here.
;; Correction image can perhaps be generated using the remedian algorithm.
;; See http://web.ipac.caltech.edu/staff/fmasci/home/statistics_refs/Remedian.pdf
;; flattening seems to take around 5 ms on my computer.


;; testing

(defn re-flatten [in-dir out-dir flatfield-by-channel]
  (let [out-cache (tile-cache/create-tile-cache 200 out-dir false)]
    (doseq [[index image] (disk/read-tiles in-dir)]
      (let [[index2 image2] (correct-indexed-image [index image] flatfield-by-channel)]
        (store/add-to-memory-tiles out-cache index2 image2 1/256)))))
  
(defn show-ffbc [ffbc]
  (->> ffbc
       vals
       (map vals)
       (apply concat)
       (map image/show)
       doall))

;(defn test-generate []
;  (->> slide-explorer.view/mt
;       ;(mipmap-flatfield-corrections 8)
;       compute-flatfield-corrections
;       ;(map-vals image/show)
;       ))

(defn transfer [in-cache]
  (let [in-dir (tile-cache/tile-dir in-cache)
        out-dir (io/file in-dir "flat1")]
    (re-flatten in-dir out-dir (compute-flatfield-corrections in-cache))))
        
        
                      