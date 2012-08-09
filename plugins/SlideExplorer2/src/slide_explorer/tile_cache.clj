(ns slide-explorer.tile-cache
  (:use [slide-explorer.cache :as cache]
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
  (swap! memory-tile-atom cache/add-item key image-processor)
  (when-let [dir (tile-dir memory-tile-atom)]
    (.submit file-executor #(disk/write-tile dir key image-processor))))

(defn load-tile
  [memory-tile-atom key]
  "Loads the tile into memory-tile-atom, if tile is not already present."
  (reactive/submit file-executor
                   (fn []
                     (or (get @memory-tile-atom key)
                         (when-let [dir (tile-dir memory-tile-atom)]
                           (when-let [tile (disk/read-tile dir key)]
                             (swap! memory-tile-atom
                                    #(if-not (get % key)
                                       (cache/add-item % key tile)
                                       %))
                             tile))))))

(defn get-tile
  [memory-tile-atom key]
  (swap! memory-tile-atom cache/hit-item key)
  (get @memory-tile-atom key))

(defn create-tile-cache
  ([lru-cache-limit directory]
    (doto (atom (cache/empty-lru-map lru-cache-limit))
      (tile-dir! directory)))
  ([lru-cache-limit]
    (create-tile-cache lru-cache-limit nil)))
    
(defn move-cache
  [memory-tile-atom]
  (let [new-location (persist/save-as (tile-dir memory-tile-atom))]
    (tile-dir! memory-tile-atom new-location)))