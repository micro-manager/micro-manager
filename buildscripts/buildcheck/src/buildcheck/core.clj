(ns buildcheck.core
  (:import (java.io File)
           (java.text SimpleDateFormat)
           (java.util Calendar Date))
  (:use [local-file :only (file*)]
        [clj-mail.core])
  (:require [clojure.xml])
  (:gen-class))

(def micromanager (file* "../.."))

(def MS-PER-HOUR (* 60 60 1000))

(def today-token
  (let [format (SimpleDateFormat. "yyyyMMdd")
        one-hour-ago (doto (Calendar/getInstance)
                       (.add Calendar/HOUR -1))]
    (.format format (.getTime one-hour-ago))))
    
(def yyyymmdd (SimpleDateFormat. "yyyyMMdd"))

;(defn result-file [bits mode]
;  (File. micromanager (str "/result" bits (name mode) ".txt")))

(defn result-file [bits mode]
  (File. micromanager (str "/results" bits ".txt")))

(defn visual-studio-errors [result-text]
  (map first
       (re-seq #"\n[^\n]+\b([1-9]|[0-9][1-9]|[0-9][0-9][1-9])\b\serror\(s\)[^\n]+\n" result-text)))

(defn visual-studio-error-info [result-text errors]
  (for [error errors]
    (let [prefix (ffirst (re-seq #"\n([1-9]|[0-9][1-9]|[0-9][0-9][1-9])>" error))
          pattern (re-pattern (str prefix "[^\\n]+"))]
      (when prefix
          (re-seq (re-pattern pattern) result-text)))))

(defn visual-studio-error-text [result-text]
  (->> (visual-studio-error-info result-text (visual-studio-errors result-text))
       (interpose "\n")
       flatten
       (apply str)))

(defn javac-errors [result-text]
  (map first
    (re-seq #"\[javac\]\s\b([1-9]|[0-9][1-9]|[0-9][0-9][1-9])\b\serrors?" result-text)))

(defn device-adapter-dlls [dir]
  (filter
      (fn [file]
        (let [file-name (.getName file)]
          (and (.endsWith file-name ".dll")
               (.startsWith file-name "mmgr_dal"))))
      (.listFiles dir)))

(defn old-files [files time-limit-hours]
  (let [now (System/currentTimeMillis)
        before (- now (* time-limit-hours MS-PER-HOUR))]
    (filter #(< (.lastModified %) before) files)))
    
(defn old-dlls [dir time-limit-hours]
  (old-files (device-adapter-dlls dir) time-limit-hours))

(defn old-jars [dir time-limit-hours]
  (old-files
    (filter
      #(.. % getName (endsWith ".jar"))
      (file-seq dir))
    time-limit-hours))

(defn exe-on-server? [bits date-token]
  (let [txt (slurp "http://valelab.ucsf.edu/~MM/nightlyBuilds/1.4/Windows/")
        pattern (re-pattern (str "MMSetup" bits "BIT_[^\\s]+?_" date-token ".exe"))]
    (re-find pattern txt)))

(defn device-vcproj-files []
  (let [device-adapter-dirs [(File. micromanager "/DeviceAdapters")
                         (File. micromanager "/SecretDeviceAdapters")]]
    (filter #(.. % getName (endsWith ".vcproj"))
            (mapcat file-seq device-adapter-dirs))))

(defn dll-name [file]
  (second (re-find #"mmgr_dal_(.*?).dll" (.getName file))))

(defn project-name [vcproj-file]
  (-> vcproj-file clojure.xml/parse :attrs :Name))

(defn bin-dir [bits]
  (File. micromanager
         (condp = bits
           32 "bin_Win32"
           64 "bin_x64")))

(defn get-dll-names [bits]
  (map dll-name (device-adapter-dlls (bin-dir bits))))

(defn missing-device-adapters [bits]
  (let [dll-names (get-dll-names bits)
        project-names (map project-name (device-vcproj-files))]
    (sort (clojure.set/difference (set project-names) (set dll-names)))))
     
(defn device-pages []
  (let [index-txt (slurp "http://valelab.ucsf.edu/~MM/MMwiki/index.php/Device%20Support")]
    (map second (re-seq #"a href=\"/~MM/MMwiki/index.php/(.*?)\"" index-txt))))

(defn missing-device-pages [bits]
  (let [dll-names (get-dll-names bits)
        device-page-names (device-pages)]
    (sort (clojure.set/difference (set dll-names) (set device-page-names)))))

(defn str-lines [sequence]
  (apply str (interpose "\n" sequence)))

(defn report-segment [title data]
  (str "\n\n" title ":\n"
       (if-not (empty? data)
         (str-lines (flatten (list data)))
         "None.")))

(defn report-build-errors [bits mode]
  (let [f (result-file bits mode)
        result-txt (slurp f)
        vs-error-text (visual-studio-error-text result-txt)
        outdated-dlls (map #(.getName %) (old-dlls (bin-dir bits) 24))
        javac-errs (javac-errors result-txt)
        outdated-jars (map #(.getName %)
                           (old-jars (File. micromanager "Install_AllPlatforms") 24))
        installer-ok (exe-on-server? bits today-token)
        missing-adapters (missing-device-adapters bits)
        missing-pages (missing-device-pages bits)]
    (when-not (and (empty? vs-error-text) (empty? outdated-dlls) false
                   (empty? javac-errs) (empty? outdated-jars)
                   installer-ok)
      (str
        "\n\nMICROMANAGER " bits "-bit "
          ({:inc "INCREMENTAL" :full "FULL"} mode)
          " BUILD ERROR REPORT\n"
        "For the full build output, see " (.getAbsolutePath f)
        (report-segment "Visual Studio reported errors" vs-error-text)
        (report-segment "Outdated device adapter DLLs" outdated-dlls)
        (report-segment "Errors reported by java compiler" javac-errs)
        (report-segment "Outdated jar files" outdated-jars)
        (report-segment "Uncompiled device adapters" missing-adapters)
        "\n\nIs installer download available on website?\n"
        (if installer-ok
          "Yes"
          "No. (build missing)\n")
        (when (= 32 bits)
          (report-segment "Missing device pages" missing-pages))
      ))))

(defn make-full-report [mode send?]
  (let [report
        (str
          (report-build-errors 32 mode)
          (report-build-errors 64 mode))]
    (if-not (empty? report)
      (do 
        (when send?
          (with-session
            "mmbuilderrors@gmail.com" (slurp "C:\\pass.txt") "smtp.gmail.com" 465 "smtp" true
            (send-email (text-email ["info@micro-manager.org"] "mm build errors" report))))
        (println report))
      (println "Nothing to report."))))

(defn -main [mode]
  (make-full-report (get {"inc" :inc "full" :full} mode) true))

    
 