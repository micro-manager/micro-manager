; FILE:         browser/utils.clj
; PROJECT:      Micro-Manager Data Browser Plugin
; ----------------------------------------------------------------------------
; AUTHOR:       Arthur Edelstein, arthuredelstein@gmail.com, April 19, 2011
; COPYRIGHT:    University of California, San Francisco, 2011
; LICENSE:      This file is distributed under the BSD license.
;               License text is included with the source distribution.
;               This file is distributed in the hope that it will be useful,
;               but WITHOUT ANY WARRANTY; without even the implied warranty
;               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
;               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
;               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
;               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

(ns org.micromanager.browser.utils
  (:import (java.util UUID)
           (java.util.prefs Preferences)
           (java.io ByteArrayInputStream ByteArrayOutputStream File FilenameFilter)
           (java.awt FileDialog)
           (java.awt.event ActionListener)
           (javax.swing AbstractAction BorderFactory JButton
                        JFileChooser KeyStroke SpringLayout
                        SwingUtilities))
  (:require [clojure.contrib.string :as string]))

; clojure utils

(defmacro gen-map [& args]
  (let [kw (map keyword args)]
    (zipmap kw args)))

(defn remove-nth [s n]
  (lazy-cat (take n s) (drop (inc n) s)))

(defmacro awt-event [& body]
  `(SwingUtilities/invokeLater (fn [] ~@body)))

; Java Preferences

(defn partition-str [n s]
  (loop [rem s acc []]
    (if (pos? (.length rem))
      (recur (string/drop n rem)
             (conj acc (string/take n rem)))
      (seq acc))))

(def pref-max-bytes (* 3/4 Preferences/MAX_VALUE_LENGTH))

(defn write-value-to-prefs
  "Writes a pure clojure data structure to Preferences object."
  [prefs key value]
  (let [chunks (partition-str pref-max-bytes (with-out-str (pr value)))
        node (. prefs node key)]
    (.clear node)
    (doseq [i (range (count chunks))]
       (. node put (str i) (nth chunks i)))))

(defn read-value-from-prefs
  "Reads a pure clojure data structure from Preferences object."
  [prefs key]
  (let [node (. prefs node key)]
    (let [s (apply str
              (for [i (range (count (. node keys)))]
                (.get node (str i) nil)))]
      (when (and s (pos? (.length s))) (read-string s)))))

(defn remove-value-from-prefs
  [prefs key]
  (let [node (. prefs node key)]
    (.removeNode node)))

;; identify OS

(defn get-os []
  (.. System (getProperty "os.name") toLowerCase))

(def is-win
  (memoize #(not (neg? (.indexOf (get-os) "win")))))

(def is-mac
  (memoize #(not (neg? (.indexOf (get-os) "mac")))))

(def is-unix
  (memoize #(not (and (neg? (.indexOf (get-os) "nix"))
                     (neg? (.indexOf (get-os) "nux"))))))

;; swing layout

(defn put-constraint [comp1 edge1 comp2 edge2 dist]
  (let [edges {:n SpringLayout/NORTH
               :w SpringLayout/WEST
               :s SpringLayout/SOUTH
               :e SpringLayout/EAST}]
  (.. comp1 getParent getLayout
            (putConstraint (edges edge1) comp1 
                           dist (edges edge2) comp2))))

(defn put-constraints [comp & args]
  (let [args (partition 3 args)
        edges [:n :w :s :e]]
    (dorun (map #(apply put-constraint comp %1 %2) edges args))))

(defn constrain-to-parent
  "Distance from edges of parent comp args"
  [& args]
  (doseq [[comp & params] (partition 9 args)]
    (apply put-constraints comp
           (flatten (map #(cons (.getParent comp) %) (partition 2 params))))))

;; borders

(defn remove-borders [& components]
  (doseq [comp components]
    (.setBorder comp (BorderFactory/createEmptyBorder))))

;; standard swing

(defn create-button [text fn]
  (doto (JButton. text)
    (.addActionListener
      (reify ActionListener
        (actionPerformed [_ _] (fn))))))

(defn create-icon-button [icon fun]
  (doto (JButton.)
    (.addActionListener
      (reify ActionListener
        (actionPerformed [_ _] (fun))))
    (.setIcon icon)))

;; keys

(defn get-keystroke [key-shortcut]
  (KeyStroke/getKeyStroke
    (.replace key-shortcut "cmd"
      (if (is-mac) "meta" "ctrl"))))

;; actions

(defn attach-child-action-key
  "Maps an input-key on a swing component to an action,
  such that action-fn is executed when pred function is
  true, but the parent (default) action when pred returns
  false."
  [component input-key pred action-fn]
  (let [im (.getInputMap component)
        am (.getActionMap component)
        input-event (get-keystroke input-key)
        parent-action (if-let [tag (.get im input-event)]
                        (.get am tag))
        child-action
          (proxy [AbstractAction] []
            (actionPerformed [e]
              (if (pred)
                (action-fn)
                (when parent-action
                  (.actionPerformed parent-action e)))))
        uuid (.. UUID randomUUID toString)]
    (.put im input-event uuid)
    (.put am uuid child-action)))


(defn attach-child-action-keys [comp & items]
  (doall (map #(apply attach-child-action-key comp %) items)))

(defn attach-action-key
  "Maps an input-key on a swing component to an action-fn."
  [component input-key action-fn]
  (attach-child-action-key component input-key
                           (constantly true) action-fn))

(defn attach-action-keys [comp & items]
  "Maps input keys to action-fns."
  (doall (map #(apply attach-action-key comp %) items)))

;; tree seq on widgets (awt or swing)

(defn widget-seq [^java.awt.Component comp]
  (tree-seq #(instance? java.awt.Container %)
            #(seq (.getComponents %))
            comp))

(defn close-window [^java.awt.Window window]
  (let [listeners (.getWindowListeners window)]
    (dorun (map #(.windowClosing % nil) listeners))
    (.setVisible window false)))

;; saving and restoring window shape in preferences

(defn get-shape [^java.awt.Component window]
  (for [comp (widget-seq window)]
    (condp instance? comp
      java.awt.Window
        [:window {:x (.getX comp) :y (.getY comp)
                  :w (.getWidth comp) :h (.getHeight comp)}]
      javax.swing.JSplitPane
        [:split-pane {:location (.getDividerLocation comp)}]
      nil)))

(defn set-shape [comp shape-data]
  (loop [comps (widget-seq comp) shapes shape-data]
    (let [comp (first comps)
          shape (first shapes)]
      (try
        (when shape
          (condp = (first shape)
            :window
              (let [{:keys [x y w h]} (second shape)]
                (.setBounds comp x y w h))
            :split-pane
                (.setDividerLocation comp (:location (second shape)))
            nil))
        (catch Exception e (println e))))
    (when (next comps)
      (recur (next comps) (next shapes)))))

(defn save-shape [prefs name component]
  (write-value-to-prefs prefs name (get-shape component)))

(defn restore-shape [prefs name component]
  (set-shape component (read-value-from-prefs prefs name)))

(defn persist-window-shape [prefs name ^java.awt.Window window]
  (restore-shape prefs name window)
  (.addWindowListener window
    (proxy [java.awt.event.WindowAdapter] []
      (windowClosing [_] (save-shape prefs name window)))))

;; file choosers

(defn choose-file [parent title suffix load]
  (let [dialog
    (doto (FileDialog. parent title
            (if load FileDialog/LOAD FileDialog/SAVE))
      (.setFilenameFilter
        (reify FilenameFilter
          (accept [this _ name] (. name endsWith suffix))))
      (.setVisible true))
    d (.getDirectory dialog)
    n (.getFile dialog)]
    (if (and d n)
      (File. d n))))

(defn choose-directory [parent title]
  (if (is-mac)
    (let [dirs-on #(System/setProperty
                     "apple.awt.fileDialogForDirectories" (str %))]
      (dirs-on true)
        (let [dir (choose-file parent title "" true)]
          (dirs-on false)
          dir))
    (let [fc (JFileChooser.)]
      (doto fc (.setFileSelectionMode JFileChooser/DIRECTORIES_ONLY)
               (.setDialogTitle title))
       (if (= JFileChooser/APPROVE_OPTION (.showOpenDialog fc parent))
         (.getSelectedFile fc)))))


;; alphanumeric sorting

(defn compare-alphanumeric [val1 val2]
  (let [str1 (str val1)
        str2 (str val2)
        double-compare
          #(try (Double/compare
                 (Double/parseDouble %1)
                 (Double/parseDouble %2))
               (catch NumberFormatException _ nil))]
    (println str1 "vs" str2)
    (or
      (double-compare str1 str2)
      (let [re #"\d+|\D+"
            s1 (vec (re-seq re str1))
            s2 (vec (re-seq re str2))
            chunk-results
              (for [i (range (max (count s1) (count s2)))]
                (let [chunk1 (get s1 i)
                      chunk2 (get s2 i)]
                  (if (and chunk1 chunk2)
                    (or
                      (double-compare chunk1 chunk2)
                      (.compareTo chunk1 chunk2))
                    (if chunk1 1 -1))))]
        (or (first (filter #(not (zero? %)) chunk-results)) 0)))))

(defn create-alphanumeric-comparator []
  (reify java.util.Comparator
    (compare [_ val1 val2] (compare-alphanumeric val1 val2))))


