(ns org.micromanager.test
  (:import (java.util ArrayList List)
           (java.util.concurrent Executors LinkedBlockingQueue ConcurrentLinkedQueue)
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
  (when (or true 
          ;(core isSequenceRunning)
            (pos? (core getRemainingImageCount)))
    (while (zero? (core getRemainingImageCount))
      (Thread/sleep 1))
    (core popNextImage)))

(defn pop-n [n]
  (repeatedly n pop-next))

(defn pop-n-par [n]
  (pmap (fn [_] (pop-next)) (range n)))

(defn test-speed [n]
  (println
    (do (core startSequenceAcquisition n 0 true) ;(Thread/sleep 1000)
        (count (remove nil? (time (doall (repeatedly n pop-next)))))))
  (println (core isBufferOverflowed)))

(defn fill-circular-buffer
  ([n wait?]
    (core startSequenceAcquisition n 0 true)
    (when wait?
      (while (core isSequenceRunning) (Thread/sleep 2))))
  ([n] (fill-circular-buffer n true)))

 
(def pop-lock (Object.))

(defn pop-next-image [image-queue]
  (when-let [image
             (locking pop-lock
                      (while (and (zero? (core getRemainingImageCount))
                                  (not (core isBufferOverflowed)))
                        (Thread/sleep 0))
                      (when (pos? (core getRemainingImageCount))
                        (core popNextImage)))]
    (when (and image image-queue)
      (.put image-queue image))))

(defn single-thread-pop-test [n wait? image-queue]
  (def temp-queue image-queue)
  (System/gc)
  (fill-circular-buffer n wait?)
  (time (do (dotimes [i n]
              (pop-next-image image-queue))
            (while (or (core isSequenceRunning)
                       (pos? (core getRemainingImageCount)))
              (Thread/sleep 1))))
  (when (core isBufferOverflowed)
    (println "Buffer overflowed at" (count image-queue) "images")))

(defn multithread-pop [nthreads n wait? image-queue]
  (let [pop (fn [] (pop-next-image image-queue))]
    (fill-circular-buffer n wait?)
    (time
    (let [pop-service (Executors/newFixedThreadPool nthreads)]
      (dorun
        (dotimes [i n]
          (.submit pop-service ^Runnable pop))
        (while (or (core isSequenceRunning)
                   (and (pos? (core getRemainingImageCount))
                        (pos? (count (.getQueue pop-service)))))
          (Thread/sleep 1)))))
        (when (core isBufferOverflowed) (println "Buffer overflowed"))
    (Thread/sleep 10)
    image-queue))

(defn repeat-with-params [f & more]
  (doseq [[n args] (partition 2 more)]
    (println n args)
    (doall
      (repeatedly n #(apply f args)))))

(defn memory []
  (let [runtime (Runtime/getRuntime)
        ->mb #(/ % 1024 1024.)]
    {:total (->mb (.totalMemory runtime))
     :free (->mb (.freeMemory runtime))
     :max (->mb (.maxMemory runtime))}))

(defn pre-expand-heap
  "Generates a queue of empty image arrays, as a way to pre-expand
   the JVM's heap."
  [image-size n]
  (let [q (LinkedBlockingQueue.)]
    (dotimes [_ n]
      (.add q (byte-array image-size)))))
    
(defn compute-sleep [start-time-ms ms-per-event n]
  (Math/max 0
            (- (+ (* n ms-per-event) start-time-ms)
               (System/currentTimeMillis))))

(defmacro doseq-throttle
  "Like doseq, but throttles the rate to ms-per-event."
  [ms-per-event seq-exprs & body]
  `(let [start# (System/currentTimeMillis)
         count# (atom 0)
         wait-ms# (long ~ms-per-event)]
     (doseq ~seq-exprs
       (Thread/sleep (compute-sleep start# wait-ms# @count#))
       (swap! count# inc)
       ~@body)))

(defn simulate-queue-test
  "Simulates a somewhat realistic image queue, where, after an initial delay,
   images are removed at the exposure rate."
  [n]
  (let [queue (LinkedBlockingQueue.)
        wait-ms (core getExposure)]
    (future (single-thread-pop-test n false queue))
    (Thread/sleep 1000)
    (println "Images taken out | Images in queue")
    (doseq-throttle wait-ms [i (range n)]
      (.take queue)
      (when (zero? (mod i 100))
        (println i "|" (count queue))))))


      
  

