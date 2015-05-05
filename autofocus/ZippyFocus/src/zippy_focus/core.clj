(ns zippy-focus.core
  (:import [org.micromanager.utils ImageUtils PropertyItem]
           [org.micromanager.api Autofocus]
           [ij ImagePlus])
  (:use [org.micromanager.mm :only (load-mm gui mmc core double-vector)])
  (:gen-class
    :name org.micromanager.ZippyFocus
    :implements [org.micromanager.api.Autofocus]))

(defn triggerable-focus? []
  (core isStageSequenceable (core getFocusDevice)))

(defonce search-params (atom nil))

(defn make-sweep-vector [z search-params]
  (let [half-range (/ (search-params :range) 2)
        tol (search-params :tolerance)]
    (vec
      (range (- z half-range)
             (+ z half-range tol)
             tol))))
  
(defn more-images-expected []
  (or (pos? (core getRemainingImageCount))
      (core isSequenceRunning)))

(defn pop-burst-image []
  (while (and (zero? (. mmc getRemainingImageCount)))
    (Thread/sleep 1))
  (core popNextImage))

(defn grab-images [n]
  (for [_ (range n) :while (more-images-expected)]
    (pop-burst-image)))

(defn image-stat [img]
  (.. ImageUtils (makeProcessor mmc img) getStatistics))

(defn image-mean [img]
  (.mean (image-stat img)))

(defn image-std-dev [img]
  (let [stat (image-stat img)]
    (/ (.stdDev stat) (.mean stat))))

(defn image-sharpness [img]
  (let [proc (ImageUtils/makeProcessor mmc img)
        mean (.. proc getStatistics mean)
        mean-edge (do (.findEdges proc) (.. proc getStatistics mean))]
    (/ mean-edge mean)))

(defn computeScore [score-type img]
  (condp = score-type
    "Mean" (image-mean img)
    "StdDev" (image-std-dev img)
    "Sharpness" (image-sharpness img)
    0))

(defn acquire-images [z-drive trigger exposure]
  (core setExposure exposure)
  (core setPosition z-drive (first trigger))
  (core loadStageSequence z-drive (double-vector trigger))
  (core startStageSequence z-drive)
  (core startSequenceAcquisition (count trigger) 0 false)
  (grab-images (count trigger)))

(defn run-autofocus [search-params]
  (let [z-drive (core getFocusDevice)
        current-z (core getPosition z-drive)
        trigger (make-sweep-vector current-z search-params)
        current-exposure (core getExposure)]
    (let [images (acquire-images z-drive trigger (:exposure search-params))
          scores (map #(computeScore (:score-type search-params) %) (seque images))
          best-index (apply max-key #(nth scores %) (range (count images)))
          best-z (nth trigger best-index)]
      (core stopSequenceAcquisition)
      (core stopStageSequence z-drive)
      (core setPosition z-drive best-z)
      (core setExposure current-exposure)
      best-z)))
           
(defn run-test []
  (reset! search-params
          {:range 5
           :tolerance 0.25
           :score-type "Sharpness"
           :exposure 2})
  (run-autofocus @search-params)
  (. gui snapSingleImage))