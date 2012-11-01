(ns org.micromanager.reloader
  (:use [org.micromanager.mm :only (load-mm mmc core get-system-config)]))


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

(defn property-state-triple
  "Returns the [dev, prop, value] for a device
   and a property name."
  [[dev property-name]]
  [dev property-name (core getProperty dev property-name)])

(defn apply-property-state-triple
  "Sets the state of property 'dev-prop' to value"
  [[dev prop value]]
  (when-not (core isPropertyReadOnly dev prop)
    (core setProperty dev prop value)))

(defn device-instance-triple
  "Returns [module, name, label] for a device with dev-label"
  [dev-label]
  [(core getDeviceLibrary dev-label)
   (core getDeviceName dev-label)
                dev-label])

(defn state-device-labels
  "If dev is a state device, returns its assigned labels in 
   a sequence, else returns nil."
  [dev]
  (when (= mmcorej.DeviceType/StateDevice
           (core getDeviceType dev))
    (vec (seq (core getStateLabels dev)))))

(defn state-device-labels-map
  "Maps each device to its labels (nil if not a state device)"
  [devs]
  (zipmap devs (map state-device-labels devs)))

(defn apply-state-device-labels
  "Takes a map of devices to state labels and applies these
   labels to the appropriate state device."
  [dev-state-map]
  (doseq [[dev labels] dev-state-map]
    (dotimes [i (count labels)]
      (core defineStateLabel dev i (get labels i)))))

(defn reload-module [module]
  (let [devs (get (devices-in-each-module) module)
        dev-triples (map device-instance-triple devs)
        pre-init-vals (zipmap devs (map pre-init-property-names devs))
        state-dev-labels (state-device-labels-map devs)]
    (clojure.pprint/pprint [devs dev-triples pre-init-vals state-dev-labels])
    (core unloadLibrary module)
    (doseq [[module name dev] dev-triples]
      (core loadDevice dev module name))
    (doseq [dev devs]
       (core initializeDevice dev))))
  

(comment pasted for inspiration
(defn reload-device
  "Unload a device, and reload it, preserving its property settings."
  [dev]
  (when (. gui getAutoreloadOption)
    (log "Attempting to reload " dev "...")
    (let [props (filter #(= (first %) dev)
                        (get-system-config-cached))
          prop-map (into {} (map #(-> % next vec) props))
          library (core getDeviceLibrary dev)
          name-in-library (core getDeviceName dev)
          state-device (eval 'mmcorej.DeviceType/StateDevice) ; load at runtime
          state-labels (when (= state-device (core getDeviceType dev))
                         (vec (core getStateLabels dev)))]
      (core unloadDevice dev)
      (core loadDevice dev library name-in-library)
      (doseq [[prop val] (pre-init-property-names dev)]
          (core setProperty dev prop val))
      (core initializeDevice dev)
      (when state-labels
        (dotimes [i (count state-labels)]
          (core defineStateLabel dev i (get state-labels i)))))
    (log "...reloading of " dev " has apparently succeeded.")))
   )

;; testing

(defonce test-lib (first (keys (devices-in-each-module))))

(defn test-unload []
  (core unloadLibrary test-lib))