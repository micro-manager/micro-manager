; FILE:         acq_engine.clj
; PROJECT:      Micro-Manager
; SUBSYSTEM:    mmstudio acquisition engine
; ----------------------------------------------------------------------------
; AUTHOR:       Arthur Edelstein, arthuredelstein@gmail.com, 2010-2011
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
           [mmcorej Configuration DoubleVector Metadata StrVector]
           [org.json JSONArray JSONObject]
           [java.text SimpleDateFormat]
           [org.micromanager.navigation MultiStagePosition StagePosition]
           [org.micromanager.utils ChannelSpec]
           [java.util Date]
           [ij IJ]))

(declare gui)
(declare mmc)

(defn load-mm
  ([gui] (def mmc (.getMMCore gui)))
  ([] (def gui (MMStudioMainFrame/getInstance))
      (load-mm gui)))

(defn rekey
  ([m kold knew]
    (-> m (dissoc kold) (assoc knew (get m kold))))
  ([m kold knew & ks]
    (reduce #(apply rekey %1 %2)
      m (partition 2 (conj ks knew kold)))))

(defn select-and-rekey [m & ks]
    (apply rekey (select-keys m (apply concat (partition 1 2 ks))) ks))
    
(defn data-object-to-map [obj]
  (into {}
    (for [f (.getFields (type obj))
          :when (zero? (bit-and
                         (.getModifiers f) java.lang.reflect.Modifier/STATIC))]
      [(keyword (.getName f)) (.get f obj)])))

(defn log [& x]
  (.logMessage mmc (apply pr-str x) true))

(defmacro log-cmd
  ([cmd-count expr]
    (let [[cmd# args#] (split-at cmd-count expr)]
      `(let [expr# (concat '~cmd# (list ~@args#))]
        (log (.trim (prn-str expr#)))
        (let [result# ~expr]
          (log
            (if (nil? result#)
              "  --> nil"
              (str "  --> " result#)))
            result#))))
  ([expr] (log-cmd 1 expr)))

(defmacro core [& args]
  `(log-cmd 3 (. mmc ~@args)))

(defmacro when-lets
  "Serially binds values to locals as in let, but stops and returns nil
   if any value is nil."
  [bindings & body]
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
    ;(println perm#)
    (when different# (~setter temp#))
    (do ~@body)
    (when different# (~setter perm#)))))
    
(defmacro with-core-setting [gst & body]
  (let [[getter setter temp] gst]
    `(with-setting [#(core ~getter) #(core ~setter %) ~temp] ~@body)))

(defmacro do-when
  "Apply f to args when all arguments are non-nil."
  [f & args]
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

(defn get-property-value [dev prop]
  (when (core hasProperty dev prop)
    (core getProperty dev prop)))

(defn get-property [dev prop]
  [dev prop (get-property-value dev prop)])

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
  (into {}
    (for [k (.GetKeys m)]
      [k (.. m (GetSingleTag k) GetValue)])))

(defn reload-device [dev]
  (when (. gui getAutoreloadOption)
    (log "Attempting to reload " dev "...")
    (let [props (filter #(= (first %) dev)
                        (get-system-config-cached))
          prop-map (into {} (map #(-> % next vec) props))
          library (core getDeviceLibrary dev)
          name-in-library (core getDeviceNameInLibrary dev)
          state-device (eval 'mmcorej.DeviceType/StateDevice) ; load at runtime
          state-labels (when (= state-device (core getDeviceType dev))
                         (vec (core getStateLabels dev)))]
      (core unloadDevice dev)
      (core loadDevice dev library name-in-library)
      (let [init-props (select-keys prop-map
                                    (filter #(core isPropertyPreInit dev %)
                                            (core getDevicePropertyNames dev)))]
        (doseq [[prop val] init-props]
          (core setProperty dev prop val)))
      (core initializeDevice dev)
      (when state-labels
        (dotimes [i (count state-labels)]
          (core defineStateLabel dev i (get state-labels i)))))
    (log "...reloading of " dev " has apparently succeeded.")))

(defn json-to-data [json]
  (condp #(isa? (type %2) %1) json
    JSONObject
      (let [keys (iterator-seq (.keys json))]
        (into {}
          (for [key keys]
            (let [val (if (.isNull json key) nil (.get json key))]
              [key (json-to-data val)]))))
    JSONArray
      (vec
        (for [i (range (.length json))]
          (json-to-data (.get json i))))
    json))

(def iso8601modified (SimpleDateFormat. "yyyy-MM-dd HH:mm:ss Z"))

(defn get-current-time-str []
  (. iso8601modified format (Date.)))

(defn get-pixel-type []
  (str ({1 "GRAY", 4 "RGB"} (int (core getNumberOfComponents))) (* 8 (core getBytesPerPixel))))

(defn ChannelSpec-to-map [^ChannelSpec chan]
  (-> chan
    (data-object-to-map)
    (select-and-rekey
      :config_                 :name
      :exposure_               :exposure
      :zOffset_                :z-offset
      :doZStack_               :use-z-stack
      :skipFactorFrame_        :skip-frames
      :useChannel_             :use-channel
      :color_                  :color
    )
    (assoc :properties (get-config (core getChannelGroup) (.config_ chan)))))

(defn MultiStagePosition-to-map [^MultiStagePosition msp]
  (if msp
    {:label (.getLabel msp)
     :axes
        (into {}
          (for [i (range (.size msp))]
            (let [stage-pos (.get msp i)]
              [(.stageName stage-pos)
                (condp = (.numAxes stage-pos)
                  1 [(.x stage-pos)]
                  2 [(.x stage-pos) (.y stage-pos)])])))}))

(defn get-msp [idx]
  (when idx
    (let [p-list (. gui getPositionList)]
      (when (pos? (. p-list getNumberOfPositions))
        (.getPosition p-list idx)))))

(defn get-msp-z-position [idx z-stage]
  (if-let [msp (get-msp idx)]
    (if-let [stage-pos (. msp get z-stage)]
      (. stage-pos x))))

(defn set-msp-z-position [idx z-stage z]
  (if-let [msp (get-msp idx)]
    (if-let [stage-pos (. msp (get z-stage))]
      (set! (. stage-pos x) z))))

(defn str-vector [str-seq]
  (let [v (StrVector.)]
    (doseq [item str-seq]
      (.add v item))
    v))

(defn double-vector [doubles]
  (let [v (DoubleVector.)]
    (doseq [item doubles]
      (.add v item))
    v))
