(ns slide-explorer.cache
  (:refer-clojure :exclude [get])
  (:require [slide-explorer.image :as image])
  (:import (java.util.concurrent Executors)
           (java.io File)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Immutable LRU cache
;; based on, but different from, clojure.core.cache by @fogus
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn lru-map [base limit]
  (with-meta base
             {::lru {:limit limit
                     :tick 0
                     :priority {}}}))

(defn remove-lru-excess [lru-map]
  (let [{:keys [priority tick limit]} (::lru (meta lru-map))]
    (if (<= (count lru-map) limit)
      lru-map
      (let [old-key (apply min-key priority (keys priority))]
        (vary-meta (dissoc lru-map old-key)
                   update-in [::lru :priority] dissoc old-key)))))
        
(defn hit-lru-metadata [lru-meta key]
  (let [tick+ (inc (get-in lru-meta [::lru :tick]))]
    (-> lru-meta
        (assoc-in [::lru :tick] tick+)
        (update-in [::lru :priority] assoc key tick+))))

(defn remove-item [lru-map key]
  (-> lru-map
      (dissoc key val)
      (vary-meta update-in [::lru :priority] dissoc key)))

(defn add-item [lru-map key val]
  (-> lru-map
      (assoc key val)
      (vary-meta hit-lru-metadata key)
      remove-lru-excess))

(defn hit-item [lru-map key]
  (vary-meta lru-map hit-lru-metadata key))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; image-cache consists of an agent
;; that contains a map
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def file-service (Executors/newFixedThreadPool 1))
  
(defn- key-map-to-file-name [cache-agent key-map]
  (let [dir (-> cache-agent meta ::cache :directory)]
    (str dir "/"
         (apply str 
                (for [[k v] key-map]
                  (str (name k) "_" v "_")))
         ".tif")))

(defn add-image
  [cache-agent key image]
  (send-off cache-agent add-item key image)
  (.submit file-service #(image/write-processor (key-map-to-file-name cache-agent key) image))
  nil)
  
(defn get-image
  [cache-agent key]
  (send-off cache-agent hit-item key) 
  (or (clojure.core/get @cache-agent key)
      (do
        (.submit file-service
                 #(let [image (image/read-processor (key-map-to-file-name cache-agent key))]
                    (send-off cache-agent add-item key image)))
        nil)))

(defn has-image
  [cache-agent key]
  (or (not (nil? (@cache-agent key)))
      (.exists (File. (key-map-to-file-name cache-agent key)))))
                 
(defn image-cache
  "Creates an image cache that uses a LRU policy to store memory-cache-size
   images in memory, and also stores all images on disk in TIFF files in the
   specified directory. Calls to .get never block: if the image is not
   in memory, nil is returned instead, but the image is then loaded
   into memory in the background, so that the next time .get is called,
   the image will be returned. The clojure.core function add-watch may
   be used to provide a callback when the image has been loaded into
   memory."
  [dir memory-cache-size]
  (doto (agent (lru-map {} memory-cache-size))
    (reset-meta! {::cache {:directory dir}})))

;; test

(import ij.ImageJ)

(import ij.process.ByteProcessor)