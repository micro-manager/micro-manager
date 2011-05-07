; FILE:         acq_engine.clj
; PROJECT:      Micro-Manager
; SUBSYSTEM:    mmstudio acquisition engine
; ----------------------------------------------------------------------------
; AUTHOR:       Arthur Edelstein, arthuredelstein@gmail.com, Dec 14, 2010
; COPYRIGHT:    University of California, San Francisco, 2006-2011
; LICENSE:      This file is distributed under the BSD license.
;               License text is included with the source distribution.
;               This file is distributed in the hope that it will be useful,
;               but WITHOUT ANY WARRANTY; without even the implied warranty
;               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
;               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
;               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
;               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

(ns org.micromanager.mm
  (:import [org.micromanager MMStudioMainFrame]
           [org.micromanager.navigation MultiStagePosition]
           [mmcorej Configuration DeviceType Metadata]
           [ij IJ]))

(declare gui)
(declare mmc)

(defn load-mm
  ([gui] (def mmc (.getMMCore gui)))
  ([] (def gui (MMStudioMainFrame/getInstance))
      (load-mm gui)))
    
(defn log [& x]
  (.logMessage mmc (apply str x) true))

(defmacro log-cmd [expr]
  `(do (log '~expr)
    (let [result# ~expr]
      (if (nil? result#)
        (log "  --> nil")
        (log "  --> " result#))
      result#)))

(defmacro core [& args]
  `(log-cmd (. mmc ~@args)))

(defmacro when-lets [bindings & body]
  (assert (vector? bindings))
  (let [n (count bindings)]
    (assert (zero? (mod n 2)))
    (assert (<= 2 n))
  (if (= 2 n)
    `(when-let ~bindings ~@body)
    (let [[a b] (map vec (split-at 2 bindings))]     
      `(when-let ~a (when-lets ~b ~@body))))))
      
(defmacro with-setting [gst & body]
  (let [[getter setter temp] gst]
    `(let [perm# (~getter)
           temp# ~temp
           different# (not= perm# temp#)]
    (println perm#)
    (when different# (~setter temp#))
    (do ~@body)
    (when different# (~setter perm#)))))
    
(defmacro with-core-setting [gst & body]
  (let [[getter setter temp] gst]
    `(with-setting [#(core ~getter) #(core ~setter %) ~temp] ~@body)))

(defmacro do-when [f & args]
  (let [args_ args]
    `(when (and ~@args_)
      (~f ~@args_))))

(defmacro if-args [f & args]
  (let [args_ args]
    `(if (and ~@args_)
      (~f ~@args_))))

(defn get-default-devices []
  {:camera          (core getCameraDevice)
   :shutter         (core getShutterDevice)
   :focus           (core getFocusDevice)
   :xy-stage        (core getXYStageDevice)
   :autofocus       (core getAutoFocusDevice)
   :image-processor (core getImageProcessorDevice)})
   
(defn config-struct [^Configuration data]
  (for [prop (map #(.getSetting data %) (range (.size data)))]
    [(.getDeviceLabel prop)
     (.getPropertyName prop)
     (.getPropertyValue prop)]))
   
(defn get-config [group config]
  (config-struct (core getConfigData group config)))

(defn get-system-config-cached []
  (config-struct (core getSystemStateCache)))

(defn get-property [dev prop]
  [dev prop (core getProperty dev prop)])

(defn map-config [^Configuration config]
  (into {}
    (for [prop (config-struct config)]
      [(str (prop 0) "-" (prop 1)) (prop 2)])))
       
(defn get-positions []
  (vec (.. gui getPositionList getPositions)))

(defn get-allowed-property-values [dev prop]
  (seq (core getAllowedPropertyValues dev prop)))
  
(defn select-values-match? [map1 map2 keys]
  (= (select-keys map1 keys)
     (select-keys map2 keys)))
     
(defn current-tagged-image []
  (let [img (IJ/getImage)]
    (.. img getStack (getTaggedImage (.getCurrentSlice img)))))

(defn get-camera-roi []
  (let [r (repeatedly 4 #(int-array 1))]
    (core getROI (nth r 0) (nth r 1) (nth r 2) (nth r 3))
    (flatten (map seq r))))

(defn parse-core-metadata [^Metadata m]
  (let [ks (seq (.. m getFrameKeys toArray))
        fd (. m getFrameData)]
    (zipmap ks (map #(.get fd %) ks))))

(defn reload-device [dev]
  (log "Attempting to reload " dev "...")
  (let [props (filter #(= (first %) dev)
                      (get-system-config-cached))
        prop-map (into {} (map #(-> % next vec) props))
        library (core getDeviceLibrary dev)
        name-in-library (core getDeviceNameInLibrary dev)
        state-labels (when (= DeviceType/StateDevice (core getDeviceType dev))
                       (seq (core getStateLabels "Dichroic")))]
    (core unloadDevice dev)
    (core loadDevice dev library name-in-library)
    (let [init-props (select-keys prop-map
                                  (filter #(core isPropertyPreInit dev %)
                                          (core getDevicePropertyNames dev)))]
      (doseq [[prop val] init-props]
        (core setProperty dev prop val)))
    (when state-labels
      (dotimes [i (count state-labels)]
        (core defineStateLabel dev i (nth state-labels i))))
    (core initializeDevice dev))
  (log "...reloading of " dev " has apparently succeeded."))

