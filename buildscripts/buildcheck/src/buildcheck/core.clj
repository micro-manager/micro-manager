(ns buildcheck.core
  (:import (java.io File)
           (java.text SimpleDateFormat)
           (java.util Calendar Date))
  (:require [postal.core :as postal]
            [clojure.xml])
  (:gen-class))

(def micromanager (File. "../.."))

(def MS-PER-HOUR (* 60 60 1000))

(def today-token
  (let [format (SimpleDateFormat. "yyyyMMdd")
        one-hour-ago (doto (Calendar/getInstance)
                       (.add Calendar/HOUR -1))]
    (.format format (.getTime one-hour-ago))))

(defn old-file? [file time-limit-hours]
  (let [now (System/currentTimeMillis)
        before (- now (* time-limit-hours MS-PER-HOUR))]
    (< (.lastModified file) before)))

(defn device-adapter-dlls [dir]
  (filter
      (fn [file]
        (let [file-name (.getName file)]
          (and (.endsWith file-name ".dll")
               (.startsWith file-name "mmgr_dal"))))
      (.listFiles dir)))

(defn exe-on-server? [bits date-token]
  (let [txt (slurp "http://valelab.ucsf.edu/~MM/nightlyBuilds/1.4/Windows/")
        pattern (re-pattern (str "MMSetup_" bits "bit_[^\\s]+?_" date-token ".exe"))]
    (re-find pattern txt)))

(defn mac-build-on-server? [date-token]
  (let [txt (slurp "http://valelab.ucsf.edu/~MM/nightlyBuilds/1.4/Mac/")
        pattern (re-pattern (str date-token ".dmg"))]
    (re-find pattern txt)))

(def device-adapter-parent-dirs [(File. micromanager "/DeviceAdapters")
                                 (File. micromanager "/SecretDeviceAdapters")])

(defn do-not-build []
  (set (apply concat
         (for [blacklist (map #(File. % "_ADAPTERS_NOT_IN_BUILD.txt") device-adapter-parent-dirs)]
           (let [txt (slurp blacklist)]
             (map #(first (.split % ":")) (.split txt "\n")))))))

(def non-windows-device-adapters #{"dc1394" "HamamatsuMac" "SimpleCam" "Video4Linux" "Spot"})

(defn device-adapter-dirs []
  (filter #(and (.isDirectory %)
                (not (.. % getName (startsWith "."))))
          (mapcat #(.listFiles %) device-adapter-parent-dirs)))

(defn files-of-type [parent-dirs suffix]
  (let [ending (str "." suffix)]
    (filter #(.. % getName (endsWith ending))
            (mapcat file-seq parent-dirs))))

(defn missing-vcxproj []
  (let [device-adapter-dirs (device-adapter-dirs)
        directories-without-vcxproj
        (filter identity
                (for [device-adapter-dir device-adapter-dirs]
                  (when (empty? (filter #(.. % getName (endsWith ".vcxproj"))
                                        (file-seq device-adapter-dir)))
                    device-adapter-dir)))]
        (sort
          (clojure.set/difference
            (set (map #(.getName %) directories-without-vcxproj))
            (do-not-build)
            non-windows-device-adapters))))

(defn device-vcxproj-files []
    (filter #(.. % getName (endsWith ".vcxproj"))
            (mapcat file-seq device-adapter-parent-dirs)))

(defn dll-name [file]
  (second (re-find #"mmgr_dal_(.*?).dll" (.getName file))))

(defn project-name [vcxproj-file]
  (try
    (-> vcxproj-file clojure.xml/parse :attrs :Name)
    (catch Exception e (println vcxproj-file))))

(defn dll-dir [bits]
  (File. micromanager
         (condp = bits
           32 "stage/Release/Win32"
           64 "stage/Release/x64")))

(defn get-dll-names [bits]
  (map dll-name (device-adapter-dlls (dll-dir bits))))

(def helper-vcxprojs #{"DEClientLib" "DEMessaging"})

(defn missing-device-adapters [bits]
  (let [dll-names (get-dll-names bits)
        project-names (map project-name (filter #(not (.. % getAbsolutePath (contains "_ATTIC")))
                                          (device-vcxproj-files)))]
    (sort (clojure.set/difference (set project-names)
                                  #{nil}
                                  (set dll-names)
                                  (do-not-build)
                                  helper-vcxprojs))))

(defn all-devices []
  (let [dll-names (get-dll-names 32)]
        (clojure.set/difference
          (clojure.set/union (set non-windows-device-adapters)
                             (missing-vcxproj)
                             (set dll-names))
          (do-not-build))))

(def device-list-page "http://micro-manager.org/wiki/Device_Support")

(defn device-pages []
  (let [index-txt (slurp device-list-page)]
    (map second (re-seq #"a href=\"/wiki/(.*?)\"" index-txt))))

(defn device-links []
  (let [index-txt (slurp device-list-page)]
    (remove empty? (map #(.trim %) (map second (re-seq #"\>(.*?)\<" index-txt))))))

(def dont-link #{"HamamatsuMac" "NI100X" "NNLC" "Neos" "PriorLegacy" "SimpleCam" "Spot"})

(defn missing-device-links []
  (sort (clojure.set/difference
          (all-devices)
          (set (device-links))
          dont-link)))

(defn missing-device-pages []
  (sort (clojure.set/difference
            (all-devices)
            (set (device-pages)))))

(defn str-lines [sequence]
  (apply str (interpose "\n" sequence)))

(defn report-segment [title data]
  (str "\n\n" title ":\n"
       (if-not (empty? data)
         (str-lines (flatten (list data)))
         "None.")))

(defn report-build-errors [testmode]
  (let [installer32-ok (exe-on-server? 32 today-token)
        installer64-ok (exe-on-server? 64 today-token)
        mac-ok (mac-build-on-server? today-token)
        missing-vcxproj-files (missing-vcxproj)
        missing-links (missing-device-links)]
    (when-not (and (not testmode)
                   installer32-ok
                   installer64-ok
                   mac-ok
                   (empty? missing-vcxproj-files)
                   (empty? missing-links))
      (str
        "MICROMANAGER BUILD STATUS REPORT\n"
        "\n\nIs Windows 32-bit installer download available on website?\n"
        (if installer32-ok "Yes." "No. (build missing)\n")
        "\n\nIs Windows 64-bit installer download available on website?\n"
        (if installer64-ok "Yes." "No. (build missing)\n")
        "\n\nIs Mac installer download available on website?\n"
        (if mac-ok "Yes." "No. (build missing)\n")
        (report-segment "Missing .vcxproj files" missing-vcxproj-files)
        (report-segment "Uncompiled device adapters (Win32)" (missing-device-adapters 32))
        (report-segment "Uncompiled device adapters (x64)" (missing-device-adapters 64))
        (report-segment "Missing device links" missing-links)
        (report-segment "Missing device pages" (missing-device-pages))))))

(defn make-full-report [send?]
  (let [report (report-build-errors false)]
    (if-not (empty? report)
      (do 
        (when send?
          (postal/send-message ^{:host "smtp.gmail.com"
                                 :user "mmbuilderrors"
                                 :pass (slurp "C:\\pass.txt")
                                 :ssl :yes}
                               {:from "mmbuilderrors@gmail.com"
                                :to "info@micro-manager.org"
                                :subject (str "MM Nightly Build Status " today-token)
                                :body report}))
        (println report))
      (println "Nothing to report."))))

(defn test-report []
  (println (report-build-errors true)))

(defn -main []
  (make-full-report true))

;; other windows stuff (manual)


(defn edit-file! [file edit-fn]
  (let [file (clojure.java.io/file file)]
    (spit file (edit-fn (slurp file)))))

(defn replace-in-file!
  "Replace a re-pattern in a file with a new value."
  [file pat new-val]
  (edit-file! file #(clojure.string/replace % pat new-val)))

(defn replace-in-files!
  "Replace a re-pattern in a list of files with a new value."
  [files pat new-val]
  (dorun (map #(replace-in-file! % pat new-val) files)))

(defn fix-output-file-tags!
  "Fix the dll output path specified in all vcxproj files."
  []
  (replace-in-files! (device-vcxproj-files)
                    #"\$\(OutDir\)/.+?\.dll" "\\$(OutDir)/mmgr_dal_\\$(ProjectName).dll"))
    
(defn find-copy-step [vcxproj]
  (re-find #"\"copy .+?\"" (slurp vcxproj)))

(defn bad-copy-step [vcxproj]
  (not (.contains (or (find-copy-step vcxproj) "PlatformName") "PlatformName")))

(defn all-bad-copy-steps
  "Find all vcxproj files with a bad post-build copy step"
  []
  (filter bad-copy-step (device-vcxproj-files)))

(defn find-pdb [vcxproj]
  (re-find #"\".*?\.pdb\"" (slurp vcxproj)))

(defn fix-pdb-file-tags!
  "Fix the pdb file path specified in all vcxproj files."
  []
  (replace-in-files! (device-vcxproj-files)
                    #"\".*?\.pdb\"" "\"\\$(OutDir)/\\$(ProjectName).pdb\""))


;;;; checking mac stuff (manual)

(defn uses-serial-port [file]
  (.contains (slurp file) "g_Keyword_Port"))

(defn devices-using-serial-port []
         (into (sorted-set)
               (map #(.getName (.getParentFile %))
                    (filter uses-serial-port (files-of-type device-adapter-parent-dirs "cpp")))))

(defn unix-built-devices []
  (->
    (into (sorted-set)
          (map #(nth (.split % "_") 2)
               (filter #(.startsWith % "libmmgr")
                       (map #(.getName %)
                            (file-seq (File. "/Users/arthur/Programs/ImageJ"))))))
    (disj "Stradus") (conj "Vortran")
    (disj "MarzhauserLStep") (conj "Marzhauser-LStep")))


(defn missing-unix-adapters []
  (into (sorted-set)
        (clojure.set/difference (set (map #(.toLowerCase %) (devices-using-serial-port)))
                                (set (map #(.toLowerCase %) (unix-built-devices)))
                                #{"pi_gcs" "pi_gcs_2" "xcite120pc_exacte" "skeleton" "crystal"
                                  "imic2000" "polychrome5000" "yokogawa" "ni100x" "twophoton"
                                  "thorlabsdcstage" "lumencorcia" "toptica_ichrome_mle"})))
