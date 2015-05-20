(ns slide-explorer.reactive
  (:import (java.awt.event WindowAdapter)
           (java.util.concurrent Executors ExecutorService)
           (java.util.concurrent.atomic AtomicReference)
           (java.util UUID)
           (javax.swing JFrame JLabel SwingUtilities)
           (clojure.lang IRef))
  (:require [clojure.pprint :as pprint]))

(defn add-watch-simple
  "Adds a watch (more simple than clojure core/add-watch). The function
   should have arguments [old-state new-state]."
  [reference function]
  (let [key (UUID/randomUUID)]
    (add-watch reference key
               (fn [_ _ old-state new-state]
                 (try
                   (function old-state new-state)
                   (catch Throwable e (println e)))))))

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

(defn submit
  "Add a zero-arg function to an executor queue."
  [executor function]
      (.submit ^ExecutorService executor
               ^Callable (identity ; avoid type-hinting the return value of fn
                           (fn [] (try (function)
                                       (catch Throwable t (.printStackTrace t)))))))

(defn single-threaded-executor
  "A single-threaded Executor. Call submit to add Runnables or Callables."
  []
  (Executors/newFixedThreadPool 1))

(defn handle-change
  "Adds a watch that submits a function to run on a java executor
   whenever the value of a reference changes. The function's arguments
   should be [old-state new-state]. If executor arg is omitted, a new
   single-threaded executor is created."
  ([reference function executor]
    (add-watch-simple reference
                      (fn [old-state new-state]
                        (when (not= old-state new-state)
                            (submit executor #(function old-state new-state))))))
  ([reference function]
    (handle-change reference function (single-threaded-executor))))

(defn assoc-if-lacking
  "Like assoc, but leave existing key-value pair untouched."
  [map key val]
  (update-in map [key] #(or % val))) 

(defn send-off-update
  "Like send-off, but if the agent is piled up with tasks, then only
   the most recent task will run (intermediate tasks will be skipped)."
  [a f & args]
  (alter-meta! a assoc-if-lacking ::task-ref (AtomicReference. nil))
  (let [task-ref (::task-ref (meta a))]
    (when-not (.getAndSet task-ref [f args])
      (send-off a
        #(let [[f1 args1] (.getAndSet task-ref nil)]
           (when f1
             (apply f1 % args1)))))))

(defn handle-update
  "Attempts to run a function asynchronously whenever there is a new value in reference.
   If the value changes too rapidly, then some values may be skipped. The
   function arguments should be [last-val current-val]."
  ([reference function agent]
    (let [last-val-agent agent]
      (add-watch-simple reference
                        (fn [_ _]
                          (send-off-update
                            last-val-agent
                            (fn [last-val]
                              (try
                                (let [current-val @reference]
                                  (when-not (identical? last-val current-val)
                                    (function last-val current-val))
                                  current-val)
                                (catch Throwable t (do (.printStackTrace t)
                                                       (def t1 t)
                                                       (println t)
                                                       (throw t))))))))))
  ([reference function]
    (let [agent0 (agent @reference)]
      (def agent1 agent0)
      (handle-update reference function agent0))))

(defn handle-update-on-change
  "Like handle-update, but only runs the function
   when valuation-fn applied to the reference's value
   has changed."
  ([reference function valuation-fn agent]
    (handle-update reference
                   (fn [last-val current-val]
                     (println (valuation-fn last-val)
                              (valuation-fn current-val))
                     (when-not (= (valuation-fn last-val)
                                  (valuation-fn current-val))
                       (function last-val current-val)))
                   agent))
  ([reference function valuation-fn]
    (handle-update-on-change function valuation-fn
                             (agent @reference))))

(defn handle-update-added-items
  "Attempts to run a function whenever there are new items in reference.
   If the value changes too rapidly, then some values may be skipped. The
   function should expect a set of new values."
  ([reference function agent]
    (handle-update
      reference
      (fn [last-val current-val]
        (function (diff-coll current-val last-val)))
      agent))
  ([reference function]
    (handle-update-added-items reference function (agent @reference))))
  
(defn handle-added-items
  "Adds a watch that applies a function to each item added to
   a coll inside reference (a ref/atom/agent/var). The function executes
   repeatedly on an executor, once for each item. If executor arg is omitted,
   a new single-threaded executor is created."
  ([reference function executor]
    (handle-change reference
                 (fn [old-state new-state]
                   (when-let [diff (time (diff-coll new-state old-state))]
                     (dorun (map function diff))))
                 executor))
  ([reference function]
    (handle-added-items reference function (single-threaded-executor))))


(defn handle-removed-items
  "Adds a watch that applies a function to each item removed from
   a coll inside reference (a ref/atom/agent/var). The function executes
   repeatedly on an executor, once for each item. If executor arg is omitted,
   a new single-threaded executor is created." 
  ([reference function executor]
    (handle-change reference 
                 (fn [old-state new-state]
                   (when-let [diff (diff-coll old-state new-state)]
                     (dorun (map function diff))))
                   executor))
  ([reference function]
    (handle-removed-items reference function (single-threaded-executor))))

;; tests


(defn reference-viewer
  "Creates a small window that shows the value of a reference
   and updates as that value changes."
  [reference key]
  (let [frame (JFrame. key)
        label (JLabel.)
        update-fn (fn [_ new-state]
                    (SwingUtilities/invokeLater
                      #(.setText label
                                 (str "<html><pre>" 
                                      (with-out-str (pprint/pprint new-state))
                                      "</pre></html>"))))]
    (.add (.getContentPane frame) label)
    (handle-update reference update-fn)
    (update-fn nil @reference)
    (doto frame
      (.addWindowListener
        (proxy [WindowAdapter] []
          (windowClosing [e]
                         (remove-watch reference key))))
      .show))
  reference)

(defn test-handle-update []
  (let [q (atom 0)]
    (handle-update q (fn [_ v] (println v)))
    (doseq [_ (range 10000)] (swap! q inc))))
  

