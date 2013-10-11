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

(defn images-in-cache
  "Return a sequence of tiles from cache, corresponding to
   a set of indices."
  [cache indices]
  (map #(tile-cache/get-tile cache % false) indices))

(defn gain-image
  "Compute a gain image from a set of raw images."
  [images]
  (let [sigma-image (image/intensity-projection
                      :standard-deviation images)]
    (image/normalize sigma-image
                    (image/center-pixel sigma-image))))

(defn offset-image
  "Compute an offset image from a set of raw images and
   a gain image."
  [images gain-image]
  (let [mean-image (image/intensity-projection
                     :mean images)
        center-mean (image/center-pixel mean-image)]
    (image/combine-processors :subtract
                              mean-image
                              (image/multiply gain-image center-mean))))

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
      (range n)
      (map #(hash-map :offset %1 :gain %2) offset-series gain-series))))

(defn unzoomed-images [cache]
  (->> @cache
       (unzoomed-indices-by-channel)
       (map-vals (partial images-in-cache cache))))

(defn flatfield-by-channel
  [cache]
  (->> cache
       unzoomed-images
       (map-vals #(mipmap-flatfield-corrections 8 %))))

(defn correct-image
  "Apply a flatfield correction to an image, returning
   the corrected image."
  [image {:keys [offset gain] :as correction}]
  (let [first-term  (image/convert-to-type-like image
                      (image/combine-processors :divide image gain))]
    (image/combine-processors :add offset (image/convert-to-type first-term :short))))

(defn correct-indexed-image [[index image] flatfield-by-channel]
  (let [correction (get flatfield-by-channel (:nc index))]
    [index (correct-image image correction)]))

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
      (let [[index2 image2] (flatten-image [index image] flatfield-by-channel)]
        (store/add-to-memory-tiles out-cache index2 image2 1/256)))))
  
(defn show-ffbc [ffbc]
  (->> ffbc
       vals
       (map vals)
       (apply concat)
       (map image/show)
       doall))

(defn test-generate []
  (->> slide-explorer.view/mt
       ;(mipmap-flatfield-corrections 8)
       flatfield-by-channel
       ;(map-vals image/show)
       ))

(defn transfer [in-cache]
  (let [in-dir (tile-cache/tile-dir in-cache)
        out-dir (io/file in-dir "flat1")]
    (re-flatten in-dir out-dir (flatfield-by-channel in-cache))))
        
        
                      