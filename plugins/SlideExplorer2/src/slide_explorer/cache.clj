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
  (let [tick (get-in lru-meta [::lru :tick])]
    (if (not= tick (get-in lru-meta [::lru :priority key]))
      (let [tick+ (inc tick)]
        (-> lru-meta
            (assoc-in [::lru :tick] tick+)
            (update-in [::lru :priority] assoc key tick+)))
      lru-meta)))

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
    ;(println "add-item" lru-map key val)
  (if (or overwrite?
          (not (lru-map key)))
    (-> lru-map
        (assoc key val)
        (hit-item key)
        remove-lru-excess)
    lru-map))
  ([lru-map key val]
    (add-item lru-map key val true)))

