(ns slide-explorer.cache
  (:refer-clojure :exclude [get])
  (:require [clojure.core.cache :as cache]
            [slide-explorer.image :as image])
  (:use [clojure.core.cache :only (defcache CacheProtocol)])
  (:import [java.util.concurrent Executors]))

(def file-service (Executors/newFixedThreadPool 1))
  
(defn- key-map-to-file-name [dir key-map]
  (str dir "/"
    (apply str 
           (for [[k v] key-map]
             (str (name k) "_" v "_")))
       ".tif"))

(defn- add-image
  [cache-agent dir key image]
  (send-off cache-agent assoc key image)
    (.submit file-service #(image/write-processor (key-map-to-file-name dir key) image))
  nil)
  
(defn- get-image
  [cache-agent dir key]
  (send-off cache-agent #(.hit %1 %2) key) 
  (or (clojure.core/get @cache-agent key)
      (do
        (.submit file-service
                 #(let [image (image/read-processor (key-map-to-file-name dir key))]
                    (send-off cache-agent assoc key image)))
        nil)))

(defn- has-image
  [cache-value dir key]
  (or (.has? cache-value key)
      (.exists (File. (key-map-to-file-name dir key)))))

(defprotocol ImageCacheProtocol
  "An image cache that stores images."
  (add [this key image]
       "Put an image into cache.")
  (get [this key]
       "Read an image from cache.")
  (has [this key]
       "Checks if image is available in cache."))

(deftype ImageCache [cache-agent dir memory-cache-size]
  ImageCacheProtocol
    (add [_ key image] (add-image cache-agent dir key image))
    (get [_ key] (get-image cache-agent dir key))
    (has [_ key] (has-image @cache-agent dir key))
  clojure.lang.IRef
    (addWatch [_ Object IFn] (.addWatch cache-agent Object IFn))
    (deref [_] (.deref cache-agent))
    (getValidator [_] (.getValidator cache-agent))
    (getWatches [_] (.getWatches cache-agent))
    (removeWatch [_ Object] (.removeWatch cache-agent Object))
    (setValidator [_ IFn] (.setValidator cache-agent IFn))
  )

(defn image-cache
  "Creates an image cache that uses a LRU policy to store memory-cache-size images in memory,
   and also stores all images on disk in TIFF files in the specified directory."
  [dir memory-cache-size]
   (proxy [clojure.lang.Agent ImageCacheProtocol]
           [(cache/lru-cache-factory memory-cache-size {})])
      (add [key image] (add-image this dir key image))
      (get [key] (get-image this dir key))
      (has [key] (has-image this dir key))
      (ADD
      (toString [] (str t ". On disk: " dir)))

;; test

(import ij.ImageJ)

(import ij.process.ByteProcessor)