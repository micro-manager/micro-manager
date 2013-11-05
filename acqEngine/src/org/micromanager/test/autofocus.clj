(ns org.micromanager.test.autofocus
  "Use image stacks to test scoring methods for image-based autofocus."
  (:import (ij IJ)))

(defn slice-processors
  "Returns a sequence of ImageProcessors for a given slice in a multislice
   ImagePlus. Zero-based."
  [imgp]
  (for [i (range (.getNSlices imgp))]
    (.. imgp getStack (getProcessor (inc i)))))

;; scoring methods

(defn mean
  "Compute the mean intensity of a processor."
  [processor]
  (.. processor getStatistics mean))

(defn std-dev
  "Compute the normalized standard deviation of a processor."
  [processor]
  (let [stats (.getStatistics processor)]
    (/ (.stdDev stats) (.mean stats))))

(defn edges
  "Compute a score based on the intensity of edges in the image."
  [processor]
  (/ (-> processor .duplicate (doto .findEdges) mean)
     (mean processor)))

(defn sharp-edges
  "Compute a score based on the intensity of sharpened edges in the image."
  [processor]
  (/ (-> processor .duplicate (doto .sharpen .findEdges) mean)
     (mean processor)))

(defn edges-std-dev
  [processor]
  (-> processor .duplicate (doto .findEdges) std-dev))

;; data and printout

(defn diff
  "Compute the difference of subsequent points."
  [data]
  (map (fn [[a b]] (- b a)) (partition 2 1 data)))

(defn print-results
  "Prints each item in a sequence preceded by an index integer.
   If starting-index is specified, then the first item will
   have that index; otherwise the first item will be numbered 0."
  ([starting-index data]
    (loop [i starting-index
           data data]
      (println i (first data))
      (when (next data)
        (recur (inc i) (next data)))))
  ([data] (print-results 0 data)))
    
;; testing

(defn scoring-data
  [scoring-method]
  "Returns the scores using a particular scoring method on the slices
   in the current open ImageJ image stack."
  (map scoring-method (slice-processors (IJ/getImage))))

(defn scoring-test
  "Tests the scoring method on the current open ImageJ image stack."
  [scoring-method]
  (let [data (scoring-data scoring-method)
        diff-data (diff data)]
    (print-results (map vector data (cons nil diff-data)))))