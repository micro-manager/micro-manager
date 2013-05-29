(ns org.micromanager.test.saving
  (:import (java.util.concurrent LinkedBlockingQueue)
           (mmcorej TaggedImage)
           (java.nio ByteBuffer ByteOrder)
           (java.io FileOutputStream RandomAccessFile)
           (java.nio.channels FileChannel$MapMode))
  (:require [org.micromanager.mm :as mm])
  (:use [org.micromanager.mm :only (core)]))

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
             (when-let [img (core popNextTaggedImage)]
               (.getDirectBuffer img))
             (when (core isBufferOverflowed)
               (throw (Exception. "Circular buffer overflowed."))))))

(def pop-next-image-memo (memoize pop-next-image))

;; converting to byte buffer
  
(defn byte-buffer
  "Create a direct byte buffer with native order."
  [size-bytes]
  (.. ByteBuffer (allocateDirect size-bytes)
      (order (ByteOrder/nativeOrder))))

(def byte-buffer-memo (memoize byte-buffer))

(defn image-to-byte-buffer
  "Store 16-bit image pixel array data in a
   pre-existing byte buffer instance."
  ([pix buffer]
  (doto buffer
    .rewind
    (.. asShortBuffer (put pix))))
  ([pix]
    (if (instance? ByteBuffer pix)
      pix
      (image-to-byte-buffer pix (byte-buffer (* 2 (count pix)))))))


;; writing to disk

(defn write-buf [channel buffer]
  ;(println "write")
  (.rewind buffer)
  (.write channel buffer))

(def corePop-memo (memoize #(core popNextImage)))

(defn acquire-and-write-to-disk [num-images]
  (let [filename (str "D:/AcquisitionData/deleteMe" (rand-int 100000) ".dat")]
    (println "\nto disk:" filename)
    (with-open [file (RandomAccessFile. filename "rw")]
               (time (let [channel (.getChannel file)
                           image-queue (queuify num-images 5 #(core getImage))
                           filled-buffer-queue (queuify num-images 5 #(image-to-byte-buffer
                                                                        (.take image-queue)))
                           ]
                       ;(println image-queue empty-buffer-queue filled-buffer-queue)
                       (dotimes [_ num-images]
                         (write-buf channel (.take filled-buffer-queue))))))))


(defn write-test [num-images]
  (let [filename (str "D:/AcquisitionData/deleteMe" (rand-int 100000) ".dat")
        buf (image-to-byte-buffer (core getImage) (byte-buffer (* 2 n)))]
    (println "\nparallel:" filename)
    (with-open [file (RandomAccessFile. filename "rw")]
               (time (let [channel (.getChannel file)]
                       (dotimes [_ num-images]
                         (write-buf channel buf)))))))

(defn acquire-and-write-parallel [num-images]
  (let [filename (str "D:/AcquisitionData/deleteMe" (rand-int 100000) ".dat")]
    (println "\nparallel:" filename)
    (with-open [file (RandomAccessFile. filename "rw")]
               (time (let [channel (.getChannel file)
                           n (count (core getImage))
                           image-queue (queuify num-images 5 #(core getImage))
                           empty-buffer-queue (queuify 10 10 #(byte-buffer (* 2 n)))
                           filled-buffer-queue (queuify num-images 5 #(image-to-byte-buffer
                                                                        (.take image-queue)
                                                                        (.take empty-buffer-queue)))
                           ]
                       ;(println image-queue empty-buffer-queue filled-buffer-queue)
                       (dotimes [_ num-images]
                         (let [buf (.take filled-buffer-queue)]
                           (write-buf channel buf)
                           (.offer empty-buffer-queue buf))))))))
        
  

;; other tests

(defn monitor-circular-buffer []
  (while (not (core isSequenceRunning))
    (Thread/sleep 100))
  (while (core isSequenceRunning)
    (println (core getRemainingImageCount))
    (Thread/sleep 1000)))

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
  

