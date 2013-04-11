(ns
  ^{:doc "Analyzes serial messages printed form HHD Serial Port Monitor,
          http://www.serial-port-monitor.com/
          Possibly useful functions for analyzing unknown serial protocols."
    :author "Arthur Edelstein"}
  serial-analyzer.core
  (:import (java.io BufferedReader StringReader))
  (:require [clojure.java.io :as io]
            [clojure.string :as string]))

(defn list-data-files []
  (filter #(-> % .getPath (.endsWith ".txt"))
          (.listFiles (io/file "."))))

(defn file-lines [f]
  (let [txt (slurp  f :encoding "UTF-8")]
    (line-seq (BufferedReader. (StringReader. txt)))))

(defn serial-commands [lines]
  (->> lines
       (remove empty?)
       (filter #(.startsWith % " "))
       (map string/trim)
       (partition 2)))

(defn find-changing-lines [lines num-commands]
  (let [command-length (/ (count lines) num-commands)
        commands (partition command-length lines)
        transpose (apply map list commands)]
    (remove #(apply = %) transpose)))

(defn simple-command [changing-pair]
  (-> changing-pair
      first
      (.split "                        ")
      first))

(defn handle-command [changing-pair]
  (-> changing-pair
      simple-command
      (.split "00")
      second
      (string/replace #"\s" "")
      (Long/parseLong 16)))

(defn handle-commands [changing-lines]
  (let [changing-lines (first changing-lines)]
       [(map handle-command changing-lines)
        (map simple-command changing-lines)]))

(defn differences [values]
  (map #(- (apply - %)) (partition 2 1 values)))
  
  
  
    
    
    
  
  

