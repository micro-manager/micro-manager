; FILE:         mm.clj
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
  (:import [org.micromanager.api MultiStagePosition]
           [mmcorej Configuration DoubleVector Metadata StrVector] ;; load mmcorej.DeviceType at runtime only
           [org.json JSONArray JSONObject]
           [java.text SimpleDateFormat]
           [org.micromanager.api MultiStagePosition StagePosition]
           [org.micromanager.utils ChannelSpec]
           [java.util Date]
           [ij IJ]
           [javax.swing SwingUtilities])
  (:require [clojure.pprint]
            [clojure.string]))

(declare gui)
(declare mmc)

(defmacro edt
  "Run body on the Event Dispatch Thread."
  [& body]
  `(SwingUtilities/invokeLater (fn [] ~@body)))

(defn store-mmcore
  [mmc]
    (def gui nil)
    (def mmc mmc))

(defn join-string [sep coll]
  (apply str (interpose sep coll)))

(defn load-mm
  "Load Micro-Manager gui and mmc objects."
  ([gui]
    (def gui gui)
    (def mmc (.getMMCore gui)))
  ([]
    (org.micromanager.MMStudio/getInstance)))

(defn rekey
  "Change the name of key kold to knew."
  ([m kold knew]
    (-> m (dissoc kold) (assoc knew (get m kold))))
  ([m kold knew & ks]
    (reduce #(apply rekey %1 %2)
      m (partition 2 (conj ks knew kold)))))

(defn select-and-rekey
  "Select certain keys and change their names. ks consists
   of alternating old and new keys."
  [m & ks]
  (apply rekey (select-keys m (apply concat (partition 1 2 ks))) ks))
    
(defn data-object-to-map
  "Automatically convert a Java data object (public fields,
   no methods) to an immutable clojure map."
  [obj]
  (into {}
    (for [f (.getFields (type obj))
          :when (zero? (bit-and
                         (.getModifiers f) java.lang.reflect.Modifier/STATIC))]
      [(keyword (.getName f))
       (condp = (.getType f)
         Boolean (.booleanValue (.get f obj))
         Boolean/TYPE (.getBoolean f obj)
         (.get f obj))])))

(defn very-pretty-print
  "Standard pretty print, unless we have a string, in which case
   omit the quotation marks."
  [x]
  ; 30 items is arbitrary but usually enough to show all settings
  ; Use wide lines to avoid excessive line breaks
  (binding [*print-length* 30 clojure.pprint/*print-right-margin* 136]
    (if (.contains (str (type x)) "class [")
      (print x)
      (clojure.pprint/pprint x))))

(defn send-to-debug-log [s]
    (.logMessage mmc (str "[AE] " s) true))

(defn cleanup-multiline
  "Take a string, remove empty lines and redundant newline-like characters, and
  re-assemble into a multiline string. Optionally prepend a newline if the
  result is more than one line."
  ([x] (cleanup-multiline x false))
  ([x add-newline]
   (let [lines (clojure.string/split-lines x)
         trimmed-lines (remove empty? (map clojure.string/trimr lines))
         reassembled (join-string "\n" trimmed-lines)]
     (if (and
           add-newline
           (< 1 (count trimmed-lines)))
       (str "\n" reassembled)
       reassembled))))

(defn form-to-log-string
  "Format a form for logging. Prepend a newline if the result is more than one
  line."
  [x]
  (let [formatted (cleanup-multiline
                    (with-out-str (very-pretty-print x))
                    true)]
    formatted))

(defn log
  "Log string or form x to the Core log output (debug only)."
  [& x]
  (let [converted (for [item x]
                    (if (string? item)
                      item
                      (form-to-log-string item)))]
    (->> converted
         (join-string " ")
         cleanup-multiline
         send-to-debug-log)))

(defmacro log-cmd
  "Log the enclosed expr to the Core log output (debug only)."
  ([cmd-count expr]
    (let [[cmd# args#] (split-at cmd-count expr)]
      `(let [expr# (concat '~cmd# (list ~@args#))]
         (log "<--" expr#)
         (let [result# ~expr]
           (log "-->" result#)
           result#))))
   ([expr] `(log-cmd 1 ~expr)))


(defmacro core
  "Run args as a method and parameters on Micro-Manager core.
   If debugging, log the command and the returned value to the
   Micro-Manager log output."
  [& args]
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
      
(defmacro with-setting
  "gst consists of a vector containing a getter, a setter, and a temp
   value. Calls getter to read the current value, uses setter to
   set the temporary value, executes body, and then calls setter
   to set the original value again."
  [gst & body]
  (let [[getter setter temp] gst]
    `(let [perm# (~getter)
           temp# ~temp
           different# (not= perm# temp#)]
    ;(println perm#)
    (when different# (~setter temp#))
    (do ~@body)
    (when different# (~setter perm#)))))
    
(defmacro with-core-setting
  "Temporarily set a core setting to a value, and then set it back after
   body is executed."
  [gst & body]
  (let [[getter setter temp] gst]
    `(with-setting [#(core ~getter) #(core ~setter %) ~temp] ~@body)))

(defmacro do-when
  "Apply f to args when all arguments are non-nil."
  [f & args]
  (let [args_ args]
    `(when (and ~@args_)
      (~f ~@args_))))

(defmacro if-args
  "Apply f to arguments only if all arguments are not nil."
  [f & args]
  (let [args_ args]
    `(if (and ~@args_)
      (~f ~@args_))))

(defmacro attempt-all
  "Attempt to evaluate every form in body, regardless of exceptions,
   then afterwards throw the first exception."
  [& body]
  (let [errors (gensym "errors")]
    `(let [~errors (atom nil)]
       ~@(for [statement# body]
           `(try ~statement#
                 (catch Throwable t# (swap! ~errors conj t#))))
       (when-let [first-err# (first @~errors)]
         (throw first-err#)))))

(defn get-default-devices
  "Get the list of default (core) devices."
  []
  {:camera          (core getCameraDevice)
   :shutter         (core getShutterDevice)
   :focus           (core getFocusDevice)
   :xy-stage        (core getXYStageDevice)
   :autofocus       (core getAutoFocusDevice)
   :image-processor (core getImageProcessorDevice)
   :galvo           (core getGalvoDevice)})
   
(defn config-struct
  "Creates a map of properties from a given Micro-Manager Configuration
   where keys are given as [dev-name prop-name] and values are the property
   values."
  [^Configuration data]
  (into {}
        (for [prop (map #(.getSetting data %) (range (.size data)))]
          [[(.getDeviceLabel prop) (.getPropertyName prop)] (.getPropertyValue prop)])))

(defn map-config
  "Creates a map of properties from a configuration map where
   keys are strings such as \"Core-Shutter\"."
  [config]
  (into {}
        (for [[[d p] v] (config-struct config)]
          [(str d "-" p) v])))
   
(defn get-config
  "Get a map representing a Micro-Manager configuration preset."
  [group config]
  (config-struct (core getConfigData group config)))

(defn get-system-config-cached
  "Get the current system state cached configuration as a map."
  []
  (config-struct (core getSystemStateCache)))

(defn get-property-value
  "Get a property value."
  [dev prop]
  (when (core hasProperty dev prop)
    (core getProperty dev prop)))

(defn get-property
  "Get a property vector, given device and property. The results
   include device, property name, and property value. Device and
   property are grouped together in a vector: [[dev prop] value]."
  [dev prop]
  [[dev prop] (get-property-value dev prop)])

(defn get-positions
  "Get the position list as a vector of MultiStagePositions."
  ([position-list]
    (vec (.getPositions (or position-list (.getPositionList gui)))))
  ([]
    (get-positions nil)))

(defn get-allowed-property-values
  "Returns a sequence of the allowed property values
   for a give device and property."
  [dev prop]
  (seq (core getAllowedPropertyValues dev prop)))
  
(defn select-values-match?
  "Checks if a particular set of values in two maps
   are the same, give a vector of keys for comparison."
  [map1 map2 keys]
  (= (select-keys map1 keys)
     (select-keys map2 keys)))
     
(defn current-tagged-image
  "Get the current tagged image displayed frontmost in Micro-Manager."
  []
  (let [img (IJ/getImage)]
    (.. img getStack (getTaggedImage (.getCurrentSlice img)))))

(defn get-camera-roi
  "Returns a vector containing the [left top width height] of the roi."
  []
  (let [r (repeatedly 4 #(int-array 1))]
    (core getROI (nth r 0) (nth r 1) (nth r 2) (nth r 3))
    (vec (flatten (map seq r)))))

(defn parse-core-metadata
  "Reads the metadata from a core Metadata object into a map."
  [^Metadata m]
  (into {}
    (for [k (.GetKeys m)]
      [k (.. m (GetSingleTag k) GetValue)])))

(defn json-to-data
  "Take a JSON object and convert it to a clojure data object."
  [json]
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

(defn to-json [x]
  (cond
    (map? x) (JSONObject. (into {} (for [[k v] x]
                                     [(name k) (to-json v)])))
    (vector? x) (JSONArray. (map to-json x))
    (list? x) (JSONArray. (map to-json x))
    (keyword? x) (name x)
    :else x))

(def ^{:doc "the ISO8601 standard date format modified to make it
             slightly more human-readable."}
       imageDateFormat (SimpleDateFormat. "yyyy-MM-dd HH:mm:ss Z"))

(defn get-current-time-str 
  "Get the current time and date in the format for image metadata."
  []
  (. imageDateFormat format (Date.)))

(defn get-pixel-type
  "Get the current pixel type."
  []
  (str ({1 "GRAY", 4 "RGB"} (int (core getNumberOfComponents)))
       (* 8 (core getBytesPerPixel))))

(defn ChannelSpec-to-map
  "Convert a Micro-Manager ChannelSpec object to a clojure data map
   with friendly keys."
  [channel-group ^ChannelSpec chan]
  (into (sorted-map)
        (-> chan
            (data-object-to-map)
            (select-and-rekey
              :config                 :name
              :exposure               :exposure
              :zOffset                :z-offset
              :doZStack               :use-z-stack
              :skipFactorFrame        :skip-frames
              :useChannel             :use-channel
              :color                  :color
              )
            (assoc :properties (get-config channel-group (.config chan))))))

(defn MultiStagePosition-to-map
  "Convert a Micro-Manager MultiStagePosition object to a clojure data map."
  [^MultiStagePosition msp]
  (if msp
    (sorted-map
      :label (.getLabel msp)
      :axes
      (into {}
            (for [i (range (.size msp))]
              (let [stage-pos (.get msp i)]
                [(.stageName stage-pos)
                 (condp = (.numAxes stage-pos)
                   1 [(.x stage-pos)]
                   2 [(.x stage-pos) (.y stage-pos)])]))))))

(defn get-msp
  "Get a MultiStagePosition object from the Position List with the specified
   index number."
  ([position-list idx]
    (when idx
      (when position-list
        (when (pos? (.getNumberOfPositions position-list))
          (.getPosition position-list idx)))))
  ([idx]
    (get-msp nil idx)))


(defn add-msp
  "Add a MultiStagePosition object to the position list."
  ([position-list label x y z]
    (.addPosition position-list
                  (doto
                    (MultiStagePosition.
                      (core getXYStageDevice) x y
                      (core getFocusDevice) z)
                    (.setLabel label))))
  ([label x y z]
    (add-msp (.getPositionList gui) label x y z)))

(defn remove-msp
  ([position-list idx]
    (.removePosition position-list idx))
  ([idx]
    (remove-msp (.getPositionList gui) idx)))

(defn get-msp-z-position
  "Get the z position for a given z-stage from the MultiStagePosition
   with the given index in the Position List."
  ([position-list idx z-stage]
    (if-let [msp (get-msp position-list idx)]
      (if-let [stage-pos (. msp get z-stage)]
        (. stage-pos x))))
  ([idx z-stage]
    (get-msp-z-position nil idx z-stage)))

(defn set-msp-z-position
  "Set the z position for a given z-stage from the MultiStagePosition
   with the given index in the Position List."
  ([position-list idx z-stage z]
  (when-let [msp (get-msp position-list idx)]
    (when-let [stage-pos (. msp (get z-stage))]
      (set! (. stage-pos x) z))))
  ([idx z-stage z]
    (set-msp-z-position nil idx z-stage z)))  

(defn positions-map [position-list]
  (let [position (map MultiStagePosition-to-map (get-positions))]
    (zipmap
      (map :label position)
      (map :axes position))))

(defn swig-vector-contents
  "Returns a string containing all values in a swig vector
   with spaces in between."
  [v]
  (->> (range (.size v))
       (map #(.get v %))
       (join-string " ")))

(defn str-vector
  "Convert a sequence of strings into a Micro-Manager StrVector."
  [str-seq]
  (let [v (proxy [StrVector] []
            (toString [] (swig-vector-contents this)))]
    (doseq [item str-seq]
      (.add v item))
    v))

(defn double-vector [doubles]
  "Convert a sequence of numbers to a Micro-Manager DoubleVector."
  (let [v (proxy [DoubleVector] []
            (toString [] (swig-vector-contents this)))]
    (doseq [item doubles]
      (.add v item))
    v))

(defn all-properties []
  (for [dev (.getLoadedDevices mmc) prop (.getDevicePropertyNames mmc dev)]
    [dev prop]))

(defn property-sequence-max-lengths []
  (into {}
        (for [[dev prop :as property] (all-properties)]
          [property
           (if (.isPropertySequenceable mmc dev prop)
             (.getPropertySequenceMaxLength mmc dev prop)
             0)])))

(defn all-cameras []
  (seq (.getLoadedDevicesOfType mmc (eval 'mmcorej.DeviceType/CameraDevice))))

(defn exposure-sequence-max-lengths []
  (into {}
        (for [camera (all-cameras)]
          [camera
           (if (.isExposureSequenceable mmc camera)
             (.getExposureSequenceMaxLength mmc camera)
             0)])))

(defn all-z-stages []
  (seq (.getLoadedDevicesOfType mmc (eval 'mmcorej.DeviceType/StageDevice))))

(defn stage-sequence-max-lengths []
  (into {}
        (for [stage (all-z-stages)]
          [stage
           (if (.isStageSequenceable mmc stage)
             (.getStageSequenceMaxLength mmc stage)
             0)])))

(defn sequence-max-lengths []
  {:camera-exposures (exposure-sequence-max-lengths)
   :stages (stage-sequence-max-lengths)
   :properties (property-sequence-max-lengths)})


  
