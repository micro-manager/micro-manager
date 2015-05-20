(ns slide-explorer.disk
  (:require [slide-explorer.image :as image]
            [clojure.java.io :as io])
  (:import (java.util.concurrent Executors)
           (java.io File)))
  
(defn- key-map-to-file
  "Convert the map used as a key in image cache to a file name."
  [dir key-map]
  (str (.getAbsolutePath (io/file dir)) "/"
       (apply str 
              (for [[k v] (into (sorted-map) key-map)]
                (let [val-str (.replace (str v) "/" "by")]
                  (str (name k) "_" val-str "_"))))
       ".tif"))

(defn read-tile
  "Read a tile image from disk for the given key."
  [dir key]
  (when key
    (image/read-processor (key-map-to-file dir key))))

(defn write-tile
  "Save a tile image to disk for the given key."
  [dir key processor]
  (image/write-processor (key-map-to-file dir key) processor))

(defn parse
  "Read a number as a number, other text as a string."
  [x]
  (try (read-string x) (catch NumberFormatException e x)))
       
(defn- pair-to-index
  "Create an [:key value] vector for a given (key,value-str) pair."
  [[k v]]
  [(keyword k) (parse (.replace v "by" "/"))])

(defn- file-to-key-map
  "Convert a filename to an index map."
  [file]
  (let [name (.getName (io/file file))]
    (->> (.split name "_")
         (partition 2)
         (map pair-to-index)
         (into {}))))

(defn available-keys
  "Returns the list of available index maps for a given directory."
  [dir]
  (->> (.listFiles (io/file dir))
       (map file-to-key-map)
       (remove empty?)))
    
(defn read-tiles
  "A lazy seq of tiles, returned as [index image], from directory dir."
  [dir]
  (map #(vector % (read-tile dir %)) (available-keys dir)))

