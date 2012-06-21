(ns slide-explorer.cache
  (:refer-clojure :exclude [get])
  (:require [slide-explorer.image :as image])
  (:import (java.util.concurrent Executors)
           (java.io File)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Immutable LRU (Least Recently Used) cache
;; based on, but different from, clojure.core.cache by @fogus
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn empty-lru-map
  "Factory method for producing a persistent map,
   with metadata that allows LRU policy."
  [limit]
  (with-meta {}
             {::lru {:limit limit
                     :tick 0
                     :priority {}}}))

(defn remove-lru-excess [lru-map]
  "Updates a LRU metadata map by removing the oldest item,
   if there are too many items."
  (let [{:keys [priority tick limit]} (::lru (meta lru-map))]
    (if (<= (count lru-map) limit)
      lru-map
      (let [old-key (apply min-key priority (keys priority))]
        (vary-meta (dissoc lru-map old-key)
                   update-in [::lru :priority] dissoc old-key)))))
        
(defn update-lru-metadata
  "Update a LRU metadata map to make key the most recent item."
  [lru-meta key]
  (let [tick+ (inc (get-in lru-meta [::lru :tick]))]
    (-> lru-meta
        (assoc-in [::lru :tick] tick+)
        (update-in [::lru :priority] assoc key tick+))))

(defn hit-item
  "Updates lru-map's metadata so that key is the most recently used item."
  [lru-map key]
  (vary-meta lru-map update-lru-metadata key))

(defn remove-item
  "Remove an item from the LRU persistent map. Like dissoc."
  [lru-map key]
  (-> lru-map
      (dissoc key val)
      (vary-meta update-in [::lru :priority] dissoc key)))

(defn add-item
  "Add an item to the LRU persistent map. Like assoc."
  ([lru-map key val overwrite?]
  (if (or overwrite?
          (not (lru-map key)))
    (-> lru-map
        (assoc key val)
        (hit-item key)
        remove-lru-excess)
    lru-map))
  ([lru-map key val]
    (add-item lru-map key val true)))

(defn get-item
  "Get an item from the LRU persistent map."
  [lru-map key]
  (-> lru-map (hit-item key) key))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; image-cache consists of an agent
;; that contains a map
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def file-service 
  "All file i/o runs on this thread."
  (Executors/newFixedThreadPool 1))
  
(defn- key-map-to-file-name
  "Convert the map used as a key in image cache to a file name. Uses
   the directory stored in the agent's metadata."
  [cache-agent key-map]
  (let [dir (-> cache-agent meta ::cache :directory)]
    (str dir "/"
         (apply str 
                (for [[k v] key-map]
                  (let [val-str (.replace (str v) "/" "by")]
                    (str (name k) "_" val-str "_"))))
         ".tif")))

(defn add-image
  "Add an image to the cache at key. Will be held in memory and saved to disk."
  [cache-agent key image]
  (swap! cache-agent add-item key image)
  (.submit file-service #(image/write-processor (key-map-to-file-name cache-agent key) image))
  nil)
  
(defn get-image
  "Get the image located at key. If image is in memory, will return it. If image
   is not in memory, nil will be returned, but the image will be read from file and
   added to the map asynchronously so that a future get-image request will return
   the image in memory."
  [cache-agent key block?]
  (if-let [mem-val (clojure.core/get @cache-agent key)]
    (do (swap! cache-agent hit-item key)
        mem-val)
    (let [loaded-image-future
          (.submit file-service
                   #(let [image (image/read-processor (key-map-to-file-name cache-agent key))]
                      (swap! cache-agent add-item key image false)
                      image))]
      (when block?
        (let [img (.get loaded-image-future)]
          ;(println img)
          img)))))

(defn has-image
  "Checks if the cache-agent has an image in memory or on disk."
  [cache-agent key]
  (or (not (nil? (@cache-agent key)))
      (.exists (File. (key-map-to-file-name cache-agent key)))))
                 
(defn image-cache
  "Creates an image cache that uses a LRU policy to store memory-cache-size
   images in memory, and also stores all images on disk in TIFF files in the
   specified directory."
  [dir memory-cache-size]
  (doto (atom (empty-lru-map memory-cache-size))
    (reset-meta! {::cache {:directory dir}})))

;; test

(import ij.ImageJ)

(import ij.process.ByteProcessor)