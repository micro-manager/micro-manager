(ns org.micromanager.test
  (:import (java.util List)
           (mmcorej TaggedImage)
           (org.json JSONObject)
           (org.micromanager MMStudioMainFrame)
           (org.micromanager.api DataProcessor))
  (:use [org.micromanager.mm :only (edt load-mm core gui mmc)]))

(load-mm (MMStudioMainFrame/getInstance))

(def acq (.getAcquisitionEngine gui))

(defn print-chan [prefix image]
  (when (.tags image)
    (edt (println prefix (.get (.tags image) "Channel")))))

(defn produce-results [data-processor results]
  (if (counted? results)
    (doseq [result results]
      (.produce data-processor result))
    (.produce data-processor results)))
      
(defn simple-data-processor
  "Make a DataProcessor whose process method is implemented by
   process-function, which should accept a single image and return
   one image or several images in a sequence. Passes
   through Poison image unchanged."
  [process-function]
  (proxy [DataProcessor] []
    (process []
             (let [img (.poll this)
                   result (if (.tags img) (process-function img) img)]
               (produce-results this result)))))
                   
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
    (remove-image-processor! proc)))

(defn identity-proc []
  "A simple data processor that passes images through, but prints out tagged image."
  (simple-data-processor #(do (println (.get (.tags %) "Channel")) %)))

(defn json-clone
  "Clone a JSONObject."
  [json-data]
  (-> json-data .toString (JSONObject.)))

(defn update-tag!
  "Destructive update: changes a json tag at key by applying (update-fn val args)."
  [tags key update-fn & args]
  (let [original-value (.get tags key)]
    (.put tags key (apply update-fn original-value args))))

(defn duplicator-proc []
  "Duplicates channels"
  (simple-data-processor
    (fn [img]
        (let [tags (json-clone (.tags img))]
          (update-tag! tags "ChannelIndex" #(+ 2 %))            
          (update-tag! tags "Channel" #(str % "-2"))
          [img (TaggedImage. (.pix img) tags)]))))

(defn restart-test []
  (remove-all-image-processors!)
  (add-image-processor! (duplicator-proc))
  (add-image-processor! (identity-proc)))


;; popNextImage speed tests

(def pop-lock (Object.))

(defn pop-next []
  (locking pop-lock
           (when (or (core isSequenceRunning)
                     (pos? (core getRemainingImageCount)))
             (while (zero? (core getRemainingImageCount))
               (Thread/sleep 10))
             (core popNextImage))))

(defn pop-n [n]
  (repeatedly n pop-next))

(defn pop-n-par [n]
  (pmap (fn [_] (pop-next)) (range n)))

(defn test-speed [n]
  (do (core startSequenceAcquisition n 0 true)
      (time (def q (doall (take-while identity (pop-n n))))))
  (println (count q) (core isBufferOverflowed)))

(defn simple-test [n]
  (core startSequenceAcquisition n 0 true)
  (while (core isSequenceRunning) (Thread/sleep 10))
  (time (dotimes [i n]
          (core popNextImage))))

