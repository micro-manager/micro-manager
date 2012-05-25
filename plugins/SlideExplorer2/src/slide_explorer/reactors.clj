(ns slide-explorer.reactors
  (:import (java.util.concurrent Executors)
           (java.util UUID)
           (clojure.lang IRef)))

(defn add-watch-simple
  "Adds a watch (more simple than clojure core/add-watch). The function
   should have arguments [old-state new-state]."
  [reference function]
  (let [key (UUID/randomUUID)]
    (add-watch reference key 
               (fn [_ _ old-state new-state]
                 (function old-state new-state)))))

(defn remove-watches
  "Removes all watches from a reference (ref/atom/agent)."
  ([^IRef reference]
    (doseq [key (keys (.getWatches reference))]
      (remove-watch reference key)))
  ([^IRef reference & more-references]
    (doseq [each-ref (cons reference more-references)]
      (remove-watches each-ref))))

(defn diff-coll
  "Returns the set of all items in coll1 but not in coll2."
  [coll1 coll2]
  (when-not (identical? coll1 coll2)
    (let [diff
          (clojure.set/difference
            (set coll1)
            (set coll2))]
      (when-not (empty? diff)
        diff))))

(defn single-threaded-executor []
  "A single-threaded Executor. Call .submit to add Runnables or Callables."
  (Executors/newFixedThreadPool 1))

(defn add-reactor
  "Adds a watch that submits a function to run on a java executor
   whenever the value of a reference changes. The function's arguments
   should be [old-state new-state]. If executor arg is omitted, a new
   single-threaded executor is created."
  ([reference function executor]
    (add-watch-simple reference
                      (fn [old-state new-state]
                        (when (not= old-state new-state)
                          (.submit executor #(function old-state new-state))))))
  ([reference function]
    (add-reactor reference function (single-threaded-executor))))

(defn added-items-reactor
  "Attaches a single-threaded reactor that applies a function to
   the set of all items added to a coll inside reference (a ref/atom/agent/var)." 
  [reference function]
  (add-reactor reference
               (fn [old-state new-state]
                 (when-let [diff (diff-coll new-state old-state)]
                   (function diff)))))

(defn removed-items-reactor
  "Attaches a single-threaded reactor that applies a function to
   the set of all items removed from a coll inside reference (a ref/atom/agent/var)." 
  [reference function]
  (add-reactor reference 
               (fn [old-state new-state]
                 (when-let [diff (diff-coll old-state new-state)]
                   (function diff)))))