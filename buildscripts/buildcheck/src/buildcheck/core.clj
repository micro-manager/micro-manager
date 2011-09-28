(ns buildcheck.core
  (:import (java.io File))
  (:use [local-file :only (file*)]
        [clj-mail.core])
  (:gen-class))

(def micromanager (file* "../.."))

(def MS-PER-HOUR (* 60 60 1000))

(defn result-file [bits mode]
  (File. micromanager (str "/result" bits (name mode) ".txt")))

(defn visual-studio-errors [result-text]
  (map first
       (re-seq #"\n[^\n]+\b([1-9]|[0-9][1-9]|[0-9][0-9][1-9])\b\serror\(s\)[^\n]+\n" result-text)))

;(defn jar-errors [result-text]
;  (re-seq #"\nBUILD FAILED[^\n]+\n" result-text))

(defn old-dlls [dir time-limit-hours]
  (let [now (System/currentTimeMillis)
        before (- now (* time-limit-hours MS-PER-HOUR))]
    (filter
      (fn [file]
        (let [file-name (.getName file)]
          (and (.endsWith file-name ".dll")
               (.startsWith file-name "mmgr_dal")
               (< (.lastModified file) before))))
      (.listFiles dir))))

(defn report-build-errors [bits mode]
  (let [result-txt (slurp (result-file bits mode))
        vs-errors (visual-studio-errors result-txt)
        outdated-dlls (old-dlls (File. micromanager
                                       (condp = bits
                                         32 "bin_Win32"
                                         64 "bin_x64")) 24)]
    (when-not (or (empty? vs-errors) (empty? outdated-dlls))
      (println (str "MICROMANAGER " bits "-bit "
                    ({:inc "INCREMENTAL" :full "FULL"} mode)
                    " BUILD ERROR REPORT"))
      (println "\nVisual Studio reported errors:")
      (dorun (map println vs-errors))
      (println "\nOutdated device adapter DLLs:")
      (dorun (map #(println (.getAbsolutePath %)) outdated-dlls))
      (println "\n\n"))))

(defn send-full-report [mode]
  (let [report
        (with-out-str
          (report-build-errors 32 mode)
          (report-build-errors 64 mode))]
    (when-not (empty? report)
     (with-session
       "mmbuilderrors@gmail.com" (slurp "C:\\pass.txt") "smtp.gmail.com" 465 "smtp" true
       (send-email (text-email ["info@micro-manager.org"] "mm build errors" report)))  
    report
    )))

(defn -main [mode]
  (send-full-report (get {"inc" :inc "full" :full} mode)))

    
 