(ns org.micromanager.test.saving
  (:import (java.util.concurrent LinkedBlockingQueue)
           (mmcorej TaggedImage)
           (java.nio ByteBuffer ByteOrder)
           (java.io FileOutputStream RandomAccessFile)
           (java.nio.channels FileChannel$MapMode))
  (:require [org.micromanager.mm :as mm])
  (:use [org.micromanager.mm :only (core)]))

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
               (.pix img))
             (when (core isBufferOverflowed)
               (throw (Exception. "Circular buffer overflowed."))))))

(def pop-next-image-memo (memoize pop-next-image))

;; converting to byte buffer

(def native-order (ByteOrder/nativeOrder))
  
(defn byte-buffer
  "Create a direct byte-buffer with native order."
  [size-bytes]
  (.. ByteBuffer (allocateDirect size-bytes)
      (order native-order)))

(defn image-to-byte-buffer
  "Store 16-bit image pixel array data in a
   pre-existing byte buffer instance."
  ([pix buffer]
  (doto buffer
    .rewind
    (.. asShortBuffer (put pix))))
  ([pix]
    (image-to-byte-buffer pix (byte-buffer (* 2 (count pix))))))


;; writing to disk

(defn write-buf [channel buffer]
  (.write channel buffer))

(defn acquire-and-write-to-disk [num-images]
  (let [filename (str "D:/AcquisitionData/deleteMe" (rand-int 100000) ".dat")
        file (RandomAccessFile. filename "rw")
        channel (.getChannel file)
        fake-buf (image-to-byte-buffer (core getImage))]
    (println filename)
    (run-sequence-acquisition num-images false)
    (time (bucket-brigade num-images
            pop-next-image
            ;pop-next-image-memo
            image-to-byte-buffer
            ;(constantly fake-buf)
            #(write-buf channel %)
            ;(constantly nil)
            ))
    (.close file)))

;; other tests

(defn test-write [num-images]
  (let [buffer (byte-buffer (* 2560 2160 2))
        filename (str "D:/AcquisitionData/test" (rand-int 100000) ".dat")
        file (RandomAccessFile. filename "rw")
        channel (.getChannel file)]
    (println filename)
    (try
      (dotimes-timed [i num-images] 100
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
  

