(ns org.micromanager.test.saving
  (:import (java.util.concurrent LinkedBlockingQueue)
           (mmcorej TaggedImage)
           (java.nio ByteBuffer ByteOrder)
           (java.io RandomAccessFile))
  (:require [org.micromanager.mm :as mm])
  (:use [org.micromanager.mm :only (core)]))

(def native-order (ByteOrder/nativeOrder))

(defn byte-buffer
  "Create a direct byte-buffer with native order."
  [size-bytes]
  (.. ByteBuffer (allocateDirect size-bytes)
      (order native-order)))

(defn image-to-byte-buffer
  "Store 16-bit image pixel array data in a
   pre-existing byte buffer instance."
  [pix buffer]
  (.. buffer asShortBuffer (put pix))
  buffer)

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

(defn single-thread-pop-test
  "Run a sequence acquisition and pop the images,
   inserting them into image-queue as they arrive."
  [num-images image-queue]
  (run-sequence-acquisition num-images false)
  (dotimes [i num-images]
        (when-let [image (pop-next-image)]
          (.put image-queue image))))

(defn byte-buffer-queue
  "Run a sequence acquisition, convert the incoming
   images to direct byte buffers, and return references
   to the buffers in a queue."
  [num-images]
  (let [image-queue (LinkedBlockingQueue. 1)]
    (future (single-thread-pop-test num-images image-queue))
    (let [queue (LinkedBlockingQueue. 1)]
      (future (dotimes [_ num-images]
                (let [img (.take image-queue)
                      n (count img)]
                  (.put queue
                        (image-to-byte-buffer
                          img
                          (byte-buffer (* n 2)))))))
      queue)))

(defmacro dotimes-timed [bindings interval & body]
  `(let [t0# (System/currentTimeMillis)]
     (println '~(first bindings) "time (ms)")
     (dotimes ~bindings
       (when (zero? (mod ~(first bindings) ~interval))
         (println ~(first bindings) (- (System/currentTimeMillis) t0#)))
       ~@body)))

(defn acquire-and-save
  "Simulate running a sequence acquisition, and save the
   images to a file using the direct byte buffer method."
  [num-images]
    (let [filename (str "E:/acquisition/test" (rand-int 100000) ".dat")
          file (RandomAccessFile. filename "rw")
          channel (.getChannel file)]
      (try
        (let [buffer-queue (time (byte-buffer-queue num-images))]
          (println filename)
          (dotimes-timed [i num-images] 100
            (let [buffer (.take buffer-queue)]
              (.write channel buffer))))
        (catch Exception e (println e))
        (finally (.close file)))
      filename))

(defn acquire-and-store-in-ram
  "Simulate running a sequence acquisition, and store
   the images in RAM using the direct byte buffer method."
  [num-images]
  (let [buffer-queue (time (byte-buffer-queue num-images))
        storage-queue (proxy [LinkedBlockingQueue] []
                        (toString [] (str "(" (count this) " items)")))]
      (dotimes-timed [i num-images] 100
        (let [buffer (.take buffer-queue)]
          (.put storage-queue buffer)))
    storage-queue))

