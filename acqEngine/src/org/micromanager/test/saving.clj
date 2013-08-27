(ns org.micromanager.test.saving
  (:import (java.util.concurrent LinkedBlockingQueue)
           (mmcorej TaggedImage)
           (java.nio ByteBuffer ByteOrder)
           (java.io FileOutputStream RandomAccessFile)
           (java.nio.channels FileChannel$MapMode))
  (:require [org.micromanager.mm :as mm])
  (:use [org.micromanager.mm :only (core)]))

(defn stop []
  (core stopSequenceAcquisition))

(defn status []
  {:running (core isSequenceRunning)
   :remaining-images (core getRemainingImageCount)
   :overflow (core isBufferOverflowed)
   :exposure (core getExposure)
   :camera (core getCameraDevice)
   :width (core getImageWidth)
   :height (core getImageHeight)
   :bytes-per-pixel (core getBytesPerPixel)
   :image-size (core getImageBufferSize)
   :roi (mm/get-camera-roi)})

;; bucket brigade

(defn bucket-node [input-queue function output-queue]
  (future
    (loop []
      (let [item (.take input-queue)]
        (if (= item input-queue)
          (.put output-queue output-queue)
          (do (.put output-queue (function item))
              (recur)))))))

(defn first-node [n function output-queue]
  (future
    (dotimes [i n]
      (.put output-queue (function)))
    (.put output-queue output-queue)))

(defn last-node [input-queue function]
  (loop []
    (let [item (.take input-queue)]
      (when (not= item input-queue)
        (function item)
        (recur)))))
        

(defn bucket-brigade [n & functions]
  (let [first-queue (LinkedBlockingQueue.)
        first-node (first-node n (first functions) first-queue)]
    (loop [remaining-functions (rest functions) input-queue first-queue]
      (let [f (first remaining-functions)]
        (if-let [n (next remaining-functions)]
          (let [output-queue (LinkedBlockingQueue.)]
            (bucket-node input-queue f output-queue)
            (recur n output-queue))
          (last-node input-queue f))))))

(defn queuify
  "Runs zero-arg function n times on another thread. Returns
   a queue that will eventually receive n return values.
   Will block whenever queue reaches queue-size."
  [n queue-size function]
  (let [queue (LinkedBlockingQueue. queue-size)]
    (future (dotimes [i n]
              (.put queue (function))))
    queue))
      
;; acquiring and popping images

(defn run-sequence-acquisition
  "Start a sequence acquisition and, optionally, wait for it."
  ([n wait?]
    (core startSequenceAcquisition n 0 true)
    (when wait?
      (while (core isSequenceRunning) (Thread/sleep 1))))
  ([n] (run-sequence-acquisition n true)))

(def pop-lock (Object.))

(defn pop-next-image
  "Pop the next image from the circular buffer. Blocks
   until an image is available or acquisition has ended."
  []
  (locking pop-lock
           (while (and (zero? (core getRemainingImageCount))
                       (not (core isBufferOverflowed)))
             (Thread/sleep 1))
           (if (pos? (core getRemainingImageCount))
             (core popNextTaggedImage)
             (when (core isBufferOverflowed)
               (throw (Exception. "Circular buffer overflowed."))))))

(def pop-next-image-memo (memoize pop-next-image))

;; converting to byte buffer
  
(defn byte-buffer
  "Create a direct byte buffer with native order."
  [size-bytes]
  (.. ByteBuffer (allocateDirect size-bytes)
      (order (ByteOrder/nativeOrder))))

(defn pixels-to-byte-buffer
  "Store 16-bit image pixel array data in a
   pre-existing byte buffer instance."
  ([pix buffer]
  (doto buffer
    .rewind
    (.. asShortBuffer (put pix))))
  ([pix]
    (if (instance? ByteBuffer pix)
      pix
      (pixels-to-byte-buffer pix (byte-buffer (* 2 (count pix)))))))


;; writing to disk

(defn write-buf [channel buffer]
  ;(println "write")
  (.rewind buffer)
  (.write channel buffer))

(def corePop-memo (memoize #(core popNextImage)))

(defn acquire-and-write-to-disk [num-images]
  (let [filename (str "G:/acquisition/deleteMe" (rand-int 100000) ".dat")]
    (-> filename java.io.File. .getParentFile .mkdirs)
    (run-sequence-acquisition num-images false)
    (with-open [file (RandomAccessFile. filename "rw")]
      (time (let [channel (.getChannel file)
                  image-queue (queuify num-images 50 pop-next-image)
                  filled-buffer-queue (queuify num-images 50 #(pixels-to-byte-buffer
                                                               (.pix (.take image-queue))))]
                       ;(println image-queue filled-buffer-queue)
                       (dotimes [_ num-images]
                         (write-buf channel (.take filled-buffer-queue))))))))

(defn acquire-and-write-parallel [num-images]
  (let [filename (str "E:/acquisition/deleteMe" (rand-int 100000) ".dat")]
    (println filename)
    ;(run-sequence-acquisition num-images false)
    (with-open [file (RandomAccessFile. filename "rw")]
               (time (let [channel (.getChannel file)
                           n (count (core getImage))
                           image-queue (queuify num-images 5 #(core getImage))
                           empty-buffer-queue (queuify num-images 5 #(byte-buffer (* 2 n)))
                           filled-buffer-queue (queuify num-images 5 #(pixels-to-byte-buffer
                                                                        (.pix (.take image-queue))
                                                                        (.take empty-buffer-queue)))
                           ]
                       ;(println image-queue empty-buffer-queue filled-buffer-queue)
                       (dotimes [_ num-images]
                         (write-buf channel (.take filled-buffer-queue))))))))
        
  

;; other tests

(defn test-write [num-images]
  (let [buffer (byte-buffer (* 2560 2160 2))
        filename (str "D:/AcquisitionData/test" (rand-int 100000) ".dat")
        file (RandomAccessFile. filename "rw")
        channel (.getChannel file)]
    (println filename)
    (try
      (dotimes [i num-images]; 100
                     (.rewind buffer)
        (.write channel buffer))
      (catch Exception e (println e))
      (finally
        (.close file)))))  

(defn monitor-circular-buffer []
  (while (not (core isSequenceRunning))
    (Thread/sleep 100))
  (while (core isSequenceRunning)
    (println (core getRemainingImageCount))
    (Thread/sleep 250)))

(defn drain-queue [blocking-queue]
  (future (loop [i 0]
            (let [obj (.take blocking-queue)]
              (when (zero? (mod i 100))
                (println i (core getRemainingImageCount)))
              (when (not= obj blocking-queue)
                (recur (inc i)))))))

(defn profile [repetitions f]
  (time (dorun (repeatedly repetitions f))))

(defn test-popping-speed [n]
  ;(future (monitor-circular-buffer))
  (core startSequenceAcquisition n 0 false)
 ; (time (while (core isSequenceRunning)
 ;         (Thread/sleep 1)))
  (profile n #(pop-next-image)))

(defn extract-array [short-buf]
  (.rewind short-buf)
  (let [n (.limit short-buf)
        a (short-array n)]
    (.get short-buf a 0 n)
    a))
  
(defn extract-array-test [n]
  (let [short-buf (.asShortBuffer (byte-buffer (* 2 2560 2160)))]
    (profile n #(extract-array short-buf))))
  

