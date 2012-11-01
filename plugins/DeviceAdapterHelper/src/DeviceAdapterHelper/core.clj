(ns org.micromanager.reloader
  (:use [clojure.java.io :only (file copy)]
        [clojure.data :only (diff)]
        [org.micromanager.mm :only (edt load-mm mmc core gui)]
        [clojure.pprint :only (pprint)])
  (:import (javax.swing JLabel JWindow)
           (java.awt Color)))


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
  "Reload a single device."
  [dev]
  (let [startup-settings (read-device-startup-settings dev)]
    (core unloadDevice dev)
    (startup-device dev startup-settings)))

(defn reload-module-fn
  "Function version of reload-module macro."
  [module housekeeping-fn]
  (let [devs (doall (get (devices-in-each-module) module))
        dev-settings-map (zipmap devs
                                 (doall (map read-device-startup-settings devs)))]
    (core unloadLibrary module)
    (housekeeping-fn)
    (doseq [dev devs]
      (startup-device dev (dev-settings-map dev)))))

(defmacro reload-module
  "Unload all devices in a module. Run housekeeping code.
   Then load and restart again with the original settings."
  [module & housekeeping]
  `(reload-module-fn ~module (fn [] ~@housekeeping)))

(defn file-dates
  "Maps each file in path to its last modification date."
  [path]
  (let [files (.listFiles (file path))]
    (zipmap files (map #(.lastModified %) files))))

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
  "Watches an atom that keeps an up-to-date map of files
   to modification dates. Runs handle-new-files-fn on any 
   files that change."
  [date-atom handle-new-file-fn]
  (add-watch date-atom "file-date"
             (fn [_ _ old-val new-val]
               (when-let [new-files (keys (second (diff old-val new-val)))]
                 (doseq [new-file new-files]
                   (handle-new-file-fn new-file))))))

(defn module-for-dll
  "Finds the module name for a dll path."
  [dll]
  (-> dll
      file .getName (.split "\\.")
      first (.replace "mmgr_dal_" "")))

(defonce 
  status-frame
  (let [l (JLabel.)
        f (JWindow.)]
    (edt (doto f
           (.setAlwaysOnTop true)
           (.setBounds 0 100 450 50))
         (.add (.getContentPane f) l)
         (doto l
           (.setBackground Color/PINK)
           (.setOpaque true))
         )
    {:label l :frame f}))

(defn label-html
  "Create the html for a label with 20-point font and internal padding."
  [& txt]
  (str "<html><body><p style=\"padding:10; font-size:20\">" (apply str txt) "</body></html>"))

(defn notify-user-reloading-started
  "Notify the user that reloading has started."
  [module]
  (let [{:keys [frame label]} status-frame]
    (edt
      (.setText label (label-html "Reloading " module " module..."))
    (.show frame))))

(defn notify-user-reloading-finished
  "Notify the user that reloading has finished."
  [module]
  (let [{:keys [frame label]} status-frame]
    (edt
      (.setText label (label-html "Finished reloading " module "!"))
      (.show frame))
    (future (Thread/sleep 3000)
            (edt (.hide frame)))))

(defn reload-updated-module
  "Unload a module, copy the new version to the Micro-Manager
   directory (overwriting the old version), and then load the module again."
  [new-dll-version]
  (let [dll (file new-dll-version)
        module (module-for-dll dll)
        loaded-modules (keys (devices-in-each-module))]
    (when (some #{module} loaded-modules)
      (let [live-mode (.isLiveModeOn gui)]
        (when live-mode
          (.enableLiveMode gui false))
        (notify-user-reloading-started module)
        (reload-module module
                       (copy dll
                             (file "." (.getName dll))))
        (notify-user-reloading-finished module)
        (when live-mode
          (.enableLiveMode gui true))))))

(defn reload-modules-on-device-adapter-change
  "Watch the directory where compiled DLLs are placed by Visual
   Studio. If a device adapter changes, copy it to Micro-Manager
   directory and reload it."
  [dll-dir-path]
  (let [date-atom (atom nil)
        stop-fn (follow-dir-dates date-atom dll-dir-path)]
    (watch-dates date-atom reload-updated-module)
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