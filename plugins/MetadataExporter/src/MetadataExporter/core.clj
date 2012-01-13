(ns MetadataExporter.core
  (:import (java.io BufferedReader FileReader
                    BufferedWriter FileWriter))
  (:use [clojure.data.json :only (read-json)]))

(defn read-json-file [file]
  (-> file FileReader. BufferedReader. (read-json false)))

(defn make-rows [metadata chosen-keys]
  (let [frames (dissoc metadata "Summary")]
    (for [[_ frame] frames]
      (for [k chosen-keys]
        (get frame k)))))

(defn write-csv-rows [file header rows]
  (with-open [writer (BufferedWriter. (FileWriter. file))]
    (doseq [row (cons header rows)]
      (.write writer 
              (str (apply str (interpose "\t" row)) "\n")))))

(defn write-data-to-csv [file metadata tags]
  (write-csv-rows file tags (make-rows metadata tags)))
  
