(ns zippy-focus.core
  (:import [org.micromanager.utils PropertyItem]
           [org.micromanager.api Autofocus])
  (:use [org.micromanager.mm
            :only (load-mm gui mmc core double-vector)])
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
  (while (and (. mmc isSequenceRunning)
              (zero? (. mmc getRemainingImageCount)))
    (Thread/sleep 1))
  (core popNextImage))

(defn grab-images []
  (doall
    (for [_ (range) :while (more-images-expected)]
      (pop-burst-image))))

(defn score-image [score-type img]
  (condp = score-type
    "Sum" (apply + img)
    0))

(defn acquire-images [z-drive trigger exposure]
    (core setExposure exposure)
    (core setPosition z-drive (first trigger))
    (core loadStageSequence z-drive (double-vector trigger))
    (core startStageSequence z-drive)
    (core waitForDevice z-drive)
    (core startSequenceAcquisition (count trigger) 0 false)
    (grab-images))

(defn run-autofocus [search-params]
  (let [z-drive (core getFocusDevice)
        current-z (core getPosition z-drive)
        trigger (make-sweep-vector current-z search-params)
        current-exposure (core getExposure)]
    (let [images (acquire-images z-drive trigger (:exposure search-params))
          scores (map #(score-image "Sum" %) images)
          best-index (apply max-key #(nth scores %) (range (count images)))
          best-z (nth trigger best-index)]
      (core setPosition z-drive best-z)
      (core setExposure current-exposure)
      best-z)))
           
(defn run-test []
  (reset! search-params
          {:range 10
           :tolerance 1.00
           :score-type "Sum"
           :exposure 10})
  (run-autofocus @search-params))