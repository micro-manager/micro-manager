(ns org.micromanager.test
  (:import (java.util List)
           (org.micromanager MMStudioMainFrame)
           (org.micromanager.api DataProcessor))
  (:use [org.micromanager.mm :only (load-mm gui mmc)]))

(load-mm (MMStudioMainFrame/getInstance))

(def acq (.getAcquisitionEngine gui))

(defn simple-data-processor
  "Make a DataProcessor whose process method is implemented by
   process-function, which should accept a single image and return
   one image or several images in a sequence."
  [process-function]
  (proxy [DataProcessor] []
    (process []
             (let [result (process-function (.poll this))]
               (if (counted? result)
                 (doseq [image (process-function (.poll this))]
                   (.produce this image))
                 (.produce this result))))))
                   

;A list of image processors that have been attached to
;the acquisition engine.
(defonce image-processors (atom #{}))

(defn add-image-processor!
  "Add a DataProcessor to the acquisition engine's list of image processors."
  [proc]
  (.addImageProcessor acq proc)
  (swap! image-processors conj proc))

(defn remove-image-processor!
  "Remove a DataProcessor from the acquisition engine's list of image processors."
  [proc]
  (.removeImageProcessor acq proc)
  (swap! image-processors disj proc))

(defn remove-all-image-processors!
  "Remove all DataProcessors from the acquisition engine's list of image processors."
  []
  (doseq [proc @image-processors]
    (remove-image-processor proc)))

(def identity-proc
  "A simple data processor that passes images through, but prints out tagged image."
  (simple-data-processor #(do (println %) %)))
