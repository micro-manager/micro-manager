(ns org.micromanager.test
  (:import (java.util ArrayList List)
           (java.util.concurrent Executors LinkedBlockingQueue
                                 ConcurrentLinkedQueue TimeUnit)
           (mmcorej TaggedImage)
           (org.json JSONArray JSONObject)
           (org.micromanager MMStudio)
           (org.micromanager.api DataProcessor)
           (java.nio ByteBuffer ByteOrder)
           (java.io RandomAccessFile)
           (org.micromanager.utils ShortWriter)
           (org.micromanager.acquisition TaggedImageStorageMultipageTiff))
  (:require [org.micromanager.mm :as mm]
            [clojure.java.io :as io])
  (:use [org.micromanager.mm :only (edt load-mm core gui mmc)]))

(load-mm (MMStudio/getInstance))

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
  (do (dotimes [i n]
              (pop-next-image image-queue))
            (while (or (core isSequenceRunning)
                       (pos? (core getRemainingImageCount)))
              (Thread/sleep 1)))
  (when (core isBufferOverflowed)
    (println "Buffer overflowed")))

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
  (core stopSequenceAcquisition)
  (let [queue (LinkedBlockingQueue.)
        wait-ms 0];(core getExposure)]
    (future (single-thread-pop-test n false queue))
    (Thread/sleep 1000)
    (println "Images taken out | Images in queue")
    (doseq [i (range n) :when (not (core isBufferOverflowed))]
      (.take queue)
      (when (zero? (mod i 100))
        (println i "|" (core getRemainingImageCount) "|" (count queue))))))

(defn image-test-summary [filename num-frames]
  (mm/to-json
    {"SlicesFirst" true
     "TimeFirst" true
     "NumSlices" 1
     "NumPositions" 1
     "NumChannels" 1
     "NumFrames" num-frames
     "Positions" 1
     "PixelType" (mm/get-pixel-type)
     "Width" (core getImageWidth)
     "Height" (core getImageHeight)
     "Prefix" filename
     "ChColors" [1]
     "ChNames" ["ch"]
     "ChMins" [0]
     "ChMaxes" [100]}))
   
(defn save-images-fast [image n]
  (let [tags (.tags image)
        summary (image-test-summary "test1" 1)
        storage (TaggedImageStorageMultipageTiff.
                  "D:/acquisitions/" true summary false true)]
    (doto tags
      (.put "Slice" 0)
      (.put "ChannelIndex" 0)
      (.put "PositionIndex" 0)
      (.put "PixelType" "GRAY16"))
    (time
      (dotimes [i n]
        (.put tags "Frame" i)
        (.putImage storage image)
        ))
    (doto storage .finished .close)))
 
 (defn fast-image-saving-test [n]
   (fill-circular-buffer 1 true)
   (let [image (core popNextTaggedImage)]
     (println "Start saving procedure...")
       (save-images-fast image n)))

;; tests of: 
;; 1. converting shorts to bytes
;; 2. writing them to disk

(def native-order (ByteOrder/nativeOrder))

(defn image-size []
  (* (core getBytesPerPixel)
                    (core getImageWidth)
                    (core getImageHeight)))

(defn byte-buffer [size]
  (.. ByteBuffer (allocateDirect size)
      (order native-order)))

(defn allocation-test [n size]
  (let [q (LinkedBlockingQueue.)]
    (dotimes [i n]
      (when (zero? (mod i 100))
        (println i))
      (.add q (byte-buffer size)))))

(defn image-to-byte-buffer [pix buffer]
  (.. buffer asShortBuffer (put pix))
  buffer)

(defn image-test [m n]
  (let [pix (core getImage)
        size (* 2 (count pix))]
    (time
      (dotimes [_ m]
        (dotimes [_ n]
          (let [buf (byte-buffer size)]
            (image-to-byte-buffer pix buf)))))))

(defn byte-buffer-test [n]
  (let [img (core getImage)]
    (apply pcalls
        (repeat n #(image-to-byte-buffer
                         img (byte-buffer (* (count img) 2)))))))

(defn byte-buffer-queue [nthreads nimages]
  (let [image-queue (LinkedBlockingQueue. 1)]
    (future (single-thread-pop-test nimages false image-queue))
    (let [queue (LinkedBlockingQueue. 1)
          service (Executors/newFixedThreadPool nthreads)]
      (future (dotimes [_ nimages]
        (let [submission  #(let [img (.take image-queue)]
                             (.put queue
                                 (image-to-byte-buffer
                                   img
                                   (byte-buffer (* (count img) 2)))))]
          (.submit service ^Runnable submission))))
      queue)))

(defn simple-byte-buffer-queue [nimages]
  (let [image-queue (LinkedBlockingQueue. 1)]
    (future (single-thread-pop-test nimages false image-queue))
    (let [queue (LinkedBlockingQueue. 1)]
      (future (dotimes [_ nimages]
                (let [img (.take image-queue)
                      n (count img)]
                  (.put queue
                        (image-to-byte-buffer
                          img
                          (byte-buffer (* n 2)))
                        )
                  )))
      queue)))

(defn shorts-test [n]
  (let [img (core getImage)
        img-size (image-size)
        filename (str "E:/acquisition/test" (rand-int 100000) ".dat")
        writer (ShortWriter. (io/file filename))]
    (println filename)
    (dorun
      (repeatedly n
                  #(.write writer img)))
    (.close writer)
    ))


(defn writing-speed-test [n]
  (let [
        filename (str "E:/acquisition/test" (rand-int 100000) ".dat")
        file (RandomAccessFile. filename "rw")
        channel (.getChannel file)buf (byte-buffer (image-size))]
    (dotimes [i n]
      (.write channel (byte-buffer (image-size)))
      (when (zero? (mod i 100))
          (future (.force channel true))))
    (.force channel false)
    (.close file)))
  

(defn acquire-and-save-test [n]
  (let [buffer-queue (time (simple-byte-buffer-queue n))
        filename (str "E:/acquisition/test" (rand-int 100000) ".dat")
        file (RandomAccessFile. filename "rw")
        channel (.getChannel file)
        t0 (System/currentTimeMillis)]
    (println filename)
    (dotimes [i n]
      (when (zero? (mod i 100))
        (println i (- (System/currentTimeMillis) t0)))
      (let [buffer (.take buffer-queue)]
        (.write channel buffer)
        ))
    (.close file)
    filename))

(defn acquire-and-store-in-ram [n]
  (let [buffer-queue (time (simple-byte-buffer-queue n))
       storage-queue (LinkedBlockingQueue.)
       t0 (System/currentTimeMillis)]
   (dotimes [i n]
     (when (zero? (mod i 100))
       (println i (- (System/currentTimeMillis) t0)))
     (let [buffer (.take buffer-queue)]
       (.put storage-queue buffer)))
   (count storage-queue)))

(defn run-in-parallel [n f]
  (doall (apply pcalls (repeat n f))))

(defmacro time-ms [& body]
  `(let [start# (System/currentTimeMillis)]
     ~@body
     (- (System/currentTimeMillis) start#)))

(defn time-per-run [nthreads f]
  (/ (time-ms (dorun (run-in-parallel nthreads f)))
     (double nthreads)))

(defn parallel-titration [reps f]
  (doseq [i (range 1 17)]
    (dotimes [_ reps]
      (println i "\t" (time-per-run i f)))))









