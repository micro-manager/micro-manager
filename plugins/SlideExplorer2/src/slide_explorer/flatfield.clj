(ns slide-explorer.flatfield
  (:require [clojure.java.io :as io]
            [slide-explorer.tile-cache :as tile-cache]
            [slide-explorer.disk :as disk]
            [slide-explorer.image :as image]
            [slide-explorer.store :as store]))

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

(defn gain-image [images]
  (let [sigma-image (image/intensity-projection
                      :standard-deviation images)]
    (image/normalize sigma-image
                    (image/center-pixel sigma-image))))

(defn offset-image [images gain-image]
  (let [mean-image (image/intensity-projection
                     :mean images)
        uncorrected-pixel (image/center-pixel mean-image)]
    (println uncorrected-pixel)
    (->
      (doto (image/combine-processors :add mean-image gain-image)
        (.multiply -1)
        (.add (double uncorrected-pixel)))
      (image/convert-to-type-like mean-image))))

(defn flat-field [images]
  (let [gain (gain-image images)]
    {:offset (image/gaussian-blur (offset-image images gain) 50)
     :gain (image/gaussian-blur gain 50)}))

(defn flat-field-by-channel [cache]
  (->> @cache
       (unzoomed-indices-by-channel)
       (map-vals (partial images-in-cache cache))
       (map-vals flat-field)))

(defn correct-image [image offset gain]
  (let [first-term  (image/convert-to-type-like image
                      (image/combine-processors :divide image gain))]
    (image/combine-processors :add offset (image/convert-to-type first-term :short))))

(defn flatten-image [[index image] flat-field-by-channel]
  (let [{:keys [offset gain]} (flat-field-by-channel (:nc index))]
    [index (correct-image image offset gain)]))

;; Can I continuously update the flat field correction image for each channel,
;; create zoomed-out versions, and then apply correction on the fly as
;; I do with channel overlays? Memoization key here.
;; Correction image can perhaps be generated using the remedian algorithm.
;; See http://web.ipac.caltech.edu/staff/fmasci/home/statistics_refs/Remedian.pdf
;; flattening seems to take around 5 ms on my computer.


;; testing

(defn re-flatten [in-dir out-dir flat-field-by-channel]
  (let [out-cache (tile-cache/create-tile-cache 200 out-dir false)]
    (doseq [[index image] (disk/read-tiles in-dir)]
      (let [[index2 image2] (flatten-image [index image] flat-field-by-channel)]
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
       flat-field-by-channel
       (map-vals image/show)))

(defn transfer [in-cache]
  (let [in-dir (tile-cache/tile-dir in-cache)
        out-dir (io/file in-dir "flat1")]
    (re-flatten in-dir out-dir (flat-field-by-channel in-cache))))
        
        
                      