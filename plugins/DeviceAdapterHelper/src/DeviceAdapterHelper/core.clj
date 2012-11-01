(ns org.micromanager.reloader
  (:use [clojure.java.io :only (file copy)]
        [clojure.data :only (diff)]
        [org.micromanager.mm :only (load-mm mmc core)]
        [clojure.pprint :only (pprint)]))


(load-mm (org.micromanager.MMStudioMainFrame/getInstance))

(defn devices-in-each-module
  "Returns a map of each module to a vector of loaded devices.
   Omits the Core device."
  []
  (-> (group-by #(core getDeviceLibrary %)
                    (seq (core getLoadedDevices)))
      (dissoc "")))

(defn pre-init-property-names
  "Returns a sequence of pre-initialization property
   names for a given device."
  [dev]
  (filter #(core isPropertyPreInit dev %)
          (seq (core getDevicePropertyNames dev))))

(defn pre-init-property-settings
  "Returns a vector of [prop value] pre-init property settings
   for a given device."
  [dev]
  (vec (doall (for [prop (pre-init-property-names dev)]
                [prop (core getProperty prop)]))))

(defn apply-property-settings
  "Sets the state of properties 'dev-prop' to value"
  [dev settings]
  (doseq [[prop value] settings]
    (when-not (core isPropertyReadOnly dev prop)
      (core setProperty dev prop value))))

(defn device-library-location
  "Returns [module name] for a device with dev-label"
  [dev-label]
  [(core getDeviceLibrary dev-label)
   (core getDeviceName dev-label)])

(defn state-device-labels
  "If dev is a state device, returns its assigned labels in 
   a sequence, else returns nil."
  [dev]
  (when (= mmcorej.DeviceType/StateDevice
           (core getDeviceType dev))
    (vec (doall (seq (core getStateLabels dev))))))

(defn apply-state-device-labels
  "Takes list of state labels for a state device
   and applies these labels."
  [dev labels]
  ;(println dev labels)
  (dotimes [i (count labels)]
      (core defineStateLabel dev i (get labels i))))

(defn read-device-startup-settings
  [dev]
  {:library-location (device-library-location dev)
   :pre-init-settings (pre-init-property-settings dev)
   :state-labels (state-device-labels dev)})

(defn startup-device
  "Startup a device given a device startup settings map."
  [dev {:keys [library-location
               pre-init-settings
               state-labels]
        :as settings}]
  (let [[module name] library-location]
    (core loadDevice dev module name)) 
  (apply-property-settings dev pre-init-settings)
  (core initializeDevice dev)
  (apply-state-device-labels dev state-labels))

(defn reload-device
  "Reload a single given device."
  [dev]
  (let [startup-settings (read-device-startup-settings dev)]
    (core unloadDevice dev)
    (startup-device dev startup-settings)))

(defn reload-module-fn
  "Unload all devices in a module. Run housekeeping function.
   Then load and restart again with the original settings."
  [module housekeeping-fn]
  (let [devs (doall (get (devices-in-each-module) module))
        dev-settings-map (zipmap devs
                                 (doall (map read-device-startup-settings devs)))]
    (core unloadLibrary module)
    (housekeeping-fn)
    (doseq [dev devs]
      (startup-device dev (dev-settings-map dev)))))

(defmacro reload-module
  [module & housekeeping]
  `(reload-module-fn ~module (fn [] ~@housekeeping)))

(defn file-dates [path]
  (let [files (.listFiles (file path))]
    (zipmap files (map #(.lastModified %) files))))

(defn changed-file [path old-dates]
  (diff old-dates (file-dates path)))

(defn follow-dir-dates
  "Polls a directory and keeps an up-to-date
   map of files to dates in date-atom. Returns a
   stop-following function." 
  [date-atom path]
  (reset! date-atom (file-dates path))
  (let [keep-following (atom true)]
    (future
      (while @keep-following
        (Thread/sleep 300)
        (reset! date-atom (file-dates path))))
    #(reset! keep-following false)))
      
(defn watch-dates
  [date-atom handle-new-files-fn]
  (add-watch date-atom "file-date"
             (fn [_ _ old-val new-val]
               (when-let [new-files (keys (second (diff old-val new-val)))]
                 (println new-files)
                 (handle-new-files-fn new-files)))))

(defn module-for-dll [dll]
  (-> dll
      file .getName (.split "\\.")
      first (.replace "mmgr_dal_" "")))

(defn reload-updated-module [new-dll-version]
  (let [dll (file new-dll-version)
        module (module-for-dll dll)
        loaded-modules (keys (devices-in-each-module))]
    (when (some #{module} loaded-modules)
      (println "Reloading" module "module...")
      (reload-module module
        (copy dll
              (file "." (.getName dll))))
      (println "...done!"))))

(defn reload-updated-modules [new-dll-versions]
  (doseq [dll new-dll-versions]
    (reload-updated-module dll)))

(defn reload-modules-on-device-adapter-change [dll-dir-path]
  (let [date-atom (atom nil)
        stop-fn (follow-dir-dates date-atom dll-dir-path)]
    (watch-dates date-atom reload-updated-modules)
    stop-fn))
        

;; testing

(defonce test-lib (first (keys (devices-in-each-module))))

(defn test-unload []
  (core unloadLibrary test-lib))

(def test-directory "E:\\projects\\micromanager\\bin_x64")

(def test-file (file test-directory "mmgr_dal_DemoCamera.dll"))

(def ^{:dynamic true} stop (fn []))

(defn run-test []
  (stop)
  (def stop (reload-modules-on-device-adapter-change test-directory)))