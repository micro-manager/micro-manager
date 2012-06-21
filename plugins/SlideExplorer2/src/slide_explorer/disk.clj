(ns slide-explorer.disk
  (:require [slide-explorer.image :as image]
            [slide-explorer.reactive :as reactive]
            [slide-explorer.cache :as cache])
  (:import (java.util.concurrent Executors)
           (java.io File)))

(def file-service 
  "All file i/o runs on this thread."
  (reactive/single-threaded-executor))
  
(defn- key-map-to-file-name
  "Convert the map used as a key in image cache to a file name. Uses
   the directory stored in the agent's metadata."
  [dir key-map]
  (str dir "/"
       (apply str 
              (for [[k v] (into (sorted-map) key-map)]
                (let [val-str (.replace (str v) "/" "by")]
                  (str (name k) "_" val-str "_"))))
       ".tif"))

(defn read-tile
  "Read a tile image from disk for the given key."
  [dir key]
  ;(println (key-map-to-file-name dir key))
  (image/read-processor (key-map-to-file-name dir key)))

(defn write-tile
  "Save a tile image to disk for the given key."
  [dir key processor]
  ;(println "writing tile" key)
  ;(try (throw (Exception.)) (catch Exception e (.printStackTrace e)))
  (image/write-processor (key-map-to-file-name dir key) processor))

(def file-executor (reactive/single-threaded-executor))

(defn tile-dir [memory-tile-atom]
  (:slide-explorer.main/directory (meta memory-tile-atom)))

(defn add-tile
  "Adds a tile to the atom in memory and saves a .tif image to the associated directory."
  [memory-tile-atom key processor]
  (swap! memory-tile-atom cache/add-item key processor)
  ;(println (count @memory-tile-atom))
  (.submit file-executor
           #(write-tile (tile-dir memory-tile-atom) key processor)))

(defn load-tile
  [memory-tile-atom key]
  "Loads the tile into memory-tile-atom, if tile is not already present."
  (.submit file-executor
           (fn []
             (when-not (get @memory-tile-atom key)
               (when-let [tile (read-tile (tile-dir memory-tile-atom) key)]
                 (swap! memory-tile-atom
                        #(if-not (get % key)
                           (cache/add-item % key tile)
                           %)))))))

(defn unload-tile
  [memory-tile-atom key]
  (println "unloading")
  (swap! memory-tile-atom dissoc key))