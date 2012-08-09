(ns slide-explorer.disk
  (:require [slide-explorer.image :as image])
  (:import (java.util.concurrent Executors)
           (java.io File)))
  
(defn- key-map-to-file-name
  "Convert the map used as a key in image cache to a file name. Uses
   the directory stored in the agent's metadata."
  [dir key-map]
  (str (.getAbsolutePath (clojure.java.io/file dir)) "/"
       (apply str 
              (for [[k v] (into (sorted-map) key-map)]
                (let [val-str (.replace (str v) "/" "by")]
                  (str (name k) "_" val-str "_"))))
       ".tif"))

(defn read-tile
  "Read a tile image from disk for the given key."
  [dir key]
  (image/read-processor (key-map-to-file-name dir key)))

(defn write-tile
  "Save a tile image to disk for the given key."
  [dir key processor]
  (image/write-processor (key-map-to-file-name dir key) processor))
