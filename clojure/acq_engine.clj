(ns acq-engine
  (:use [mm :only [mmc gui acq]]
        [sequence-generator :only [generate-acq-sequence]])
  (:import [org.micromanager AcqControlDlg]
           [org.micromanager.api AcquisitionEngine]
           [org.micromanager.acquisition AcquisitionWrapperEngine]
           [org.micromanager.acquisition.engine SequenceSettings]
           [org.micromanager.navigation MultiStagePosition StagePosition]
           [java.util.prefs Preferences]
           [org.micromanager.utils ChannelSpec]))

;; general utils
(defn data-object-to-map [obj]
  (into {}
    (for [f (.getFields (type obj))
          :when (zero? (bit-and
                         (.getModifiers f) java.lang.reflect.Modifier/STATIC))]
      [(keyword (.getName f)) (.get f obj)])))

(defmacro apply* [& args]
  `(~@(butlast args) ~@(eval (last args))))

(defn rekey
  ([m kold knew]
    (-> m (dissoc kold) (assoc knew (get m kold))))
  ([m kold knew & ks]
    (reduce #(apply rekey %1 %2)
      m (partition 2 (conj ks knew kold)))))

(defn select-and-rekey [m & ks]
    (apply rekey (select-keys m (apply concat (partition 1 2 ks))) ks))

;; mm utils
(defn get-default-devices []
  {:camera          (. mmc getCameraDevice)
   :shutter         (. mmc getShutterDevice)
   :focus           (. mmc getFocusDevice)
   :xy-stage        (. mmc getXYStageDevice)
   :autofocus       (. mmc getAutoFocusDevice)
   :image-processor (. mmc getImageProcessorDevice)})

(defn get-config [group config]
  (let [data (. mmc getConfigData group config)
        n (.size data)
        props (map #(.getSetting data %) (range n))]
    (for [prop props]
      [(.getDeviceLabel prop)
       (.getPropertyName prop)
       (.getPropertyValue prop)])))

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
    )
    (assoc :properties (get-config (. mmc getChannelGroup) (.config_ chan)))))

(defn MultiStagePosition-to-map [^MultiStagePosition msp]
  {:label (.getLabel msp) :axes
    (into {}
      (for [i (range (.size msp))]
        (let [stage-pos (.get msp i)]
          [(.stageName stage-pos)
            (condp = (.numAxes stage-pos)
              1 [(.x stage-pos)]
              2 [(.x stage-pos) (.y stage-pos)])])))})

;; acq-engine
 
(defn clock-ms []
  (quot (System/nanoTime) 1000000))

(defn acq-sleep [event last-wake-time]
  (let [sleep-time (-
                     (+ last-wake-time (event :interval-ms))
                     (clock-ms))]
    (when (pos? sleep-time)
      (Thread/sleep sleep-time)))
  (clock-ms)) 
 
(def device-agents
  (let [devs (seq (. mmc getLoadedDevices))]
    (zipmap devs (repeatedly (count devs) #(agent nil)))))

(defn set-stage-position
  ([stage-dev z] (. mmc setPosition stage-dev z))
  ([stage-dev x y] (. mmc setXYPosition stage-dev x y)))

(defn set-property
  ([dev prop] (. mmc setProperty (prop 0) (prop 1) (prop 2))))
  
(defn send-device-action [dev action]
  (send-off (device-agents dev) (fn [_] (action))))
    
(defn create-presnap-actions [event]
  (into {} (concat
    (for [[axis pos] (get-in event [:position :axes])]
      [axis #(apply set-stage-position axis pos)])
    (for [prop (get-in event [:channel :properties])]
      [(prop 0) #(.setProperty mmc (prop 0) (prop 1) (prop 2))]))))

(defn run-actions [action-map]
  (doseq [[dev action] action-map]
    (send-device-action dev action))
  (doseq [dev (keys action-map)]
    (send-device-action dev #(. mmc waitForDevice dev)))
  (doall (map (partial await-for 10000) (vals device-agents))))

(defn snap-image [open-before close-after]
  (if open-before
    (. mmc setShutterOpen true)
    (. mmc waitForDevice (. mmc getShutterDevice)))
  (. mmc snapImage)
  (if close-after
    (. mmc setShutterOpen false))
    (. mmc waitForDevice (. mmc getShutterDevice)))

(defn collect-image [event]
  (. mmc getImage))
  
(defn run-event [event]
  (run-actions (create-presnap-actions event))
  (await-for 10000 (device-agents (. mmc getCameraDevice)))
  (snap-image true true)
  (send-device-action (. mmc getCameraDevice) #(collect-image event)))
  
(defn run-acquisition [settings] 
  (def acq-settings settings)
  (let [acq-seq (generate-acq-sequence settings)]
     (def acq-sequence acq-seq)
     (map run-event acq-seq)))
  
(defn convert-settings [^SequenceSettings settings]
  (-> settings
    (data-object-to-map)
    (rekey
      :slicesFirst             :slices-first
      :timeFirst               :time-first
      :keepShutterOpenSlices   :keep-shutter-open-slices
      :keepShutterOpenChannels :keep-shutter-open-channels
      :useAutofocus            :use-autofocus
      :skipAutofocusCount      :autofocus-skip
      :relativeZSlice          :relative-slices
      :intervalMs              :interval-ms
      :slices                  :slices
    )
    (assoc :frames (range (.numFrames settings))
           :channels (filter :use-channel (map ChannelSpec-to-map (.channels settings)))
           :positions (map MultiStagePosition-to-map (.positions settings)))))

(defn run-acquisition-from-settings [^SequenceSettings settings]
  (println settings)
  (def orig-settings settings)
  (println (run-acquisition (convert-settings settings))))

(defn create-acq-eng []
  (doto
    (proxy [AcquisitionWrapperEngine] []
      (runPipeline [^SequenceSettings settings]
        (run-acquisition-from-settings settings)))
    (.setCore mmc (.getAutofocusManager gui))
    (.setParentGUI gui)
    (.setPositionList (.getPositionList gui))))

(defn test-dialog [eng]
  (.show (AcqControlDlg. eng (Preferences/userNodeForPackage (.getClass gui)) gui)))


   