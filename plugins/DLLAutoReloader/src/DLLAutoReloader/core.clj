; FILE:         DLLAutoReloader/core.clj
; PROJECT:      Micro-Manager
; SUBSYSTEM:    DeviceAdapterHelper plugin
; ----------------------------------------------------------------------------
; AUTHOR:       Arthur Edelstein, arthuredelstein@gmail.com, 2012
; COPYRIGHT:    University of California, San Francisco, 2012
; LICENSE:      This file is distributed under the BSD license.
;               License text is included with the source distribution.
;               This file is distributed in the hope that it will be useful,
;               but WITHOUT ANY WARRANTY; without even the implied warranty
;               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
;               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
;               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
;               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

(ns DLLAutoReloader.core
  (:use [clojure.java.io :only (file copy)]
        [clojure.data :only (diff)]
        [org.micromanager.mm :only (edt load-mm mmc core gui)])
  (:import (javax.swing JButton JFrame JLabel JWindow)
           (java.awt Color)
           (java.awt.event ActionListener)
           (java.util.prefs Preferences)
           (org.micromanager.utils FileDialogs FileDialogs$FileType
                                   GUIUtils JavaUtils)))

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
                [prop (core getProperty dev prop)]))))

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
  (dotimes [i (count labels)]
      (core defineStateLabel dev i (get labels i))))

(defn device-port
  "Get the name of the port connected to a device."
  [dev]
  (when (core hasProperty dev "Port")
    (core getProperty dev "Port")))

(defn read-device-startup-settings
  "Read all settings that will be necessary for restarting
   a device."
  [dev]
  {:library-location (device-library-location dev)
   :pre-init-settings (pre-init-property-settings dev)
   :state-labels (state-device-labels dev)
   :port (device-port dev)})

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
                                 (doall (map read-device-startup-settings devs)))
        ports (set (remove nil? (map :port (vals dev-settings-map))))]
    (core unloadLibrary module)
    (housekeeping-fn)
    (dorun (map reload-device ports))
    (doseq [dev devs]
      (startup-device dev (dev-settings-map dev)))))

(defmacro reload-module
  "Unload all devices in a module. Run housekeeping code.
   Then load and restart again with the original settings."
  [module & housekeeping]
  `(reload-module-fn ~module (fn [] ~@housekeeping)))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Plugin code.
;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
  (println @date-atom)
  (let [keep-following (atom true)]
    (future
      (while @keep-following
        (Thread/sleep 500)
        (when-not (.isAcquisitionRunning gui)
          (reset! date-atom (file-dates path)))))
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
  (let [name (-> dll file .getName (.split "\\.") first)]
    (when (.contains name "mmgr_dal_") ; else not a lib!
      (second (.split name "mmgr_dal_")))))

(def status-frame
  "A JFrame that displays autoreloading status."
  (memoize
    (fn []
      (let [l (JLabel.)
            f (JWindow.)]
        (edt (doto f
               (.setAlwaysOnTop true)
               (.setBounds 0 100 450 50))
             (.add (.getContentPane f) l)
             (doto l
               (.setBackground Color/PINK)
               (.setOpaque true)))
        {:label l :frame f}))))

(defn label-html
  "Create the html for a label with 20-point font and internal padding."
  [& txt]
  (str "<html><body><p style=\"padding:10; font-size:20\">" (apply str txt) "</body></html>"))

(defn notify-user
  "Show the user a message."
  [temporary? & message-parts]
  (let [{:keys [frame label]} (status-frame)]
    (edt
      (.setText label (apply label-html message-parts))
      (.show frame))
    (when temporary?
      (future (Thread/sleep 3000)
              (edt (.hide frame))))))

(defmacro pausing-live-mode
  "Temporarily stop live mode (if it is running), run body code, and then
   start live mode again (if it was running)."
  [& body]
  `(let [live-mode# (.isLiveModeOn gui)]
    (when live-mode#
      (.enableLiveMode gui false))
    ~@body
    (when live-mode#
      (.enableLiveMode gui true))))

(defn reload-updated-module
  "Unload a module, copy the new version to the Micro-Manager
   directory (overwriting the old version), and then load the module again."
  [new-dll-version]
  (let [dll (file new-dll-version)
        module (module-for-dll dll)
        loaded-modules (keys (devices-in-each-module))]
    (when (some #{module} loaded-modules)
      (notify-user false "Reloading " module " module...")
      (pausing-live-mode
        (reload-module module
                       (copy dll
                             (file "." (.getName dll)))))
      (notify-user true "Finished reloading " module "!"))))

(defn reload-modules-on-device-adapter-change
  "Watch the directory where compiled DLLs are placed by Visual
   Studio. If a device adapter changes, copy it to Micro-Manager
   directory and reload it."
  [dll-dir-path]
  (let [date-atom (atom nil)
        stop-fn (follow-dir-dates date-atom dll-dir-path)]
    (watch-dates date-atom reload-updated-module)
    stop-fn))
        
(def ^{:dynamic true} stop (fn []))

(defn activate
  "Start device adapter library reloading."
  [path]
  (stop)
  (def stop (reload-modules-on-device-adapter-change path))
  (notify-user true "Ready to autoreload!"))

(defn deactivate []
  "Stop device adapter library reloading."
  []
  (stop)
  (notify-user true "Autoreloading deactivated!"))

(def dll-directory-type (FileDialogs$FileType. "DLL Directory" "New DLL location" "" false nil))

(defn dll-dir-dialog
  "Presents user with a dialog for choosing the directory from which DLLs will be reloaded."
  []
  (FileDialogs/openDir nil "Please choose a directory where new DLLs will appear"
                       dll-directory-type))

(def prefs (.. Preferences userRoot (node "DLLAutoReloader")))

(defn choose-dll-dir
  "Allows user to choose a directory, and activates that directory
   for DLL reloading."
  [path-button]
  (when-let [dir (file (dll-dir-dialog))]
         (let [path (.getAbsolutePath dir)]
           (.setText path-button path)
           (activate path)
           (.put prefs "dir" path))))

(def control-frame (atom nil))

(defn on-button-click
  "When button is clicked, the no-arg callback-fn will be called. This function
   returns a function that will remove the button click listener."
  [^JButton button callback-fn]
  (let [listener
        (proxy [ActionListener] []
          (actionPerformed [e] (callback-fn)))]
    (.addActionListener button listener)
    (fn [] (.removeListener listener))))   

(defn setup-frame
  "Control frame for the plugin."
  []
  (doto (proxy [JFrame] [])
    (.setBounds 100 100 600 50)
    (.setResizable false)
    (GUIUtils/recallPosition)
    (.setTitle "DLL Auto Reloading")))

(defn startup
  "Create the control frame and activate directory watching."
  []
  (let [frame (setup-frame)
        path (.get prefs "dir" nil)
        path-button (JButton. (or path "(choose path)"))]
    (when path
      (activate path))
    (on-button-click path-button #(choose-dll-dir path-button))
    (doto (.getContentPane frame)
      (.add path-button))
    frame))

(defn show-plugin
  "Show the plugin at a given location."
  [app]
  (load-mm app)
  (when-not @control-frame
    (reset! control-frame (startup)))
  (.show @control-frame))

(defn handle-exit
  "Runs when application exits."
  []
  (stop))

;; testing

(defn plugin-test
  "Test the plugin."
  []
  (show-plugin (org.micromanager.MMStudio/getInstance)))

(defn test-lib
  "Get the first module with loaded devices."
  []
  (first (keys (devices-in-each-module))))

(defn test-unload
  "Attemt to unload a the test library."
  []
  (core unloadLibrary (test-lib)))
