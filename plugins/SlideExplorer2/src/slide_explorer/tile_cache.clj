(ns slide-explorer.tile-cache
  (:require [clojure.core.cache :as cache]
            [slide-explorer.disk :as disk]
            [slide-explorer.persist :as persist]
            [slide-explorer.reactive :as reactive]))

(def file-executor (reactive/single-threaded-executor))

(defn tile-dir [memory-tile-atom]
  (::directory (meta memory-tile-atom)))

(defn tile-dir! [memory-tile-atom dir]
  (alter-meta! memory-tile-atom assoc ::directory dir))

(defn add-tile
  "Adds a tile to the atom in memory and saves a .tif image to the associated directory."
  [memory-tile-atom key image-processor]
  (swap! memory-tile-atom assoc key image-processor)
  ;(println (tile-dir memory-tile-atom) (count @memory-tile-atom))
  (when-let [dir (tile-dir memory-tile-atom)]
    (reactive/submit file-executor #(disk/write-tile dir key image-processor))))

(defn get-tile
  "Returns a tile with a specific key. If hit? is true,
   the tile is marked as most recently used."
  ([memory-tile-atom key hit?]
  (when-let [val (get @memory-tile-atom key)]
    (when hit?
      (swap! memory-tile-atom cache/hit key))
    val))
  ([memory-tile-atom key]
    (get-tile memory-tile-atom key false)))

(defn load-tile
  "Loads the tile into memory-tile-atom, if tile is not already present."
  [memory-tile-atom key]
  (.get
    (reactive/submit file-executor
                     (fn []
                       (or (get-tile memory-tile-atom key true)
                           (when-let [dir (tile-dir memory-tile-atom)]
                             (when-let [tile (disk/read-tile dir key)]
                               (swap! memory-tile-atom
                                      #(if-not (get % key)
                                         (assoc % key tile)
                                         %))
                               tile)))))))

(defn create-tile-cache
  ([lru-cache-limit directory]
    (doto (atom (cache/lru-cache-factory {} :threshold lru-cache-limit))
      (tile-dir! directory)))
  ([lru-cache-limit]
    (create-tile-cache lru-cache-limit nil)))
    
(defn move-cache
  [memory-tile-atom]
  (let [new-location (persist/save-as (tile-dir memory-tile-atom))]
    (tile-dir! memory-tile-atom new-location)))