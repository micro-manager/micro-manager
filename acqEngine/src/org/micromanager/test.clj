(ns org.micromanager.test
  (:import (java.util List)
           (mmcorej TaggedImage)
           (org.json JSONObject)
           (org.micromanager MMStudioMainFrame)
           (org.micromanager.api DataProcessor))
  (:use [org.micromanager.mm :only (load-mm gui mmc)]))

(load-mm (MMStudioMainFrame/getInstance))

(def acq (.getAcquisitionEngine gui))

(defn simple-data-processor
  "Make a DataProcessor whose process method is implemented by
   process-function, which should accept a single image and return
   one image or several images in a sequence. Passes
   through Poison image unchanged."
  [process-function]
  (proxy [DataProcessor] []
    (process []
             (let [img (.poll this)]
               (if (.tags img)
                 (let [result (process-function img)]
                   (if (counted? result)
                     (doseq [image (process-function (.poll this))]
                       (.produce this image))
                     (.produce this result)))
                 (.produce this img))))))
                   

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
      (if (= 0 (.get (.tags img) "ChannelIndex"))
        (let [tags (json-clone (.tags img))]
          (println (.get tags "Channel"))
          (update-tag! tags "ChannelIndex" #(+ 2 %))            
          (update-tag! tags "Channel" #(str % "-2"))
          (println (.get tags "Channel"))
          [img (TaggedImage. (.pix img) tags)])
        img))))

(defn restart-test []
  (remove-all-image-processors!)
  (add-image-processor! (duplicator-proc))
  (add-image-processor! (identity-proc)))