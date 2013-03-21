(ns slide-explorer.tile-cache
  (:require [clojure.core.cache :as cache]
            [clojure.java.io :as io]
            [slide-explorer.disk :as disk]
            [slide-explorer.persist :as persist]
            [slide-explorer.reactive :as reactive]))

(def file-executor (reactive/single-threaded-executor))

(defn init-tile-listener-set! [memory-tile-atom]
  (alter-meta! memory-tile-atom assoc ::tile-listeners #{}))

(defn add-tile-listener! [memory-tile-atom callback]
  (alter-meta! memory-tile-atom update-in [::tile-listeners] conj callback))

(defn run-tile-listeners [memory-tile-atom]
  (doseq [callback (::tile-listeners (meta memory-tile-atom))]
    (callback)))

(defn remove-tile-listeners! [memory-tile-atom]
  (alter-meta! memory-tile-atom dissoc ::tile-listeners))

(defn tile-dir
  "Returns the associated directory for a tile cache."
  [memory-tile-atom]
  (::directory (meta memory-tile-atom)))

(defn tile-dir!
  "Sets the associated directory for a tile cache."
  [memory-tile-atom dir]
  (alter-meta! memory-tile-atom assoc ::directory dir))

(defn add-tile
  "Adds a tile to the atom in memory and saves a .tif image to the associated directory."
  [memory-tile-atom key image-processor]
  (swap! memory-tile-atom assoc key image-processor)
  (run-tile-listeners memory-tile-atom)
  ;(println (tile-dir memory-tile-atom) (count @memory-tile-atom))
  (when-let [dir (tile-dir memory-tile-atom)]
    (reactive/submit file-executor #(disk/write-tile dir key image-processor))))

(defn get-tile
  "Returns a tile with a specific key. If hit? is true,
   the tile is marked as most recently used."
  ([memory-tile-atom key hit?]
  (when-let [val (get @memory-tile-atom key)]
    (when hit?
      (swap! memory-tile-atom #(try (cache/hit % key)
                                    (catch Exception _))))
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
                               (run-tile-listeners memory-tile-atom)
                               tile))))))) 

(defn create-tile-cache
  ([lru-cache-limit directory read-only?]
    (when-let [dir (io/file directory)]   
      (if read-only?
        (when-not (.exists dir)
          (throw (Exception. "Directory not found")))
        (.mkdirs dir)))
    (doto (atom (cache/lru-cache-factory {} :threshold lru-cache-limit))
      (tile-dir! directory)
      init-tile-listener-set!))
  ([lru-cache-limit]
    (create-tile-cache lru-cache-limit nil nil)))
    
(defn move-cache
  [memory-tile-atom]
  (let [new-location (persist/save-as (tile-dir memory-tile-atom))]
    (tile-dir! memory-tile-atom new-location)))
