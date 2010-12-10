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

;; java interop
(defn data-object-to-map [obj]
  (into {}
    (for [f (.getFields (type obj))
          :when (zero? (bit-and
                         (.getModifiers f) java.lang.reflect.Modifier/STATIC))]
      [(keyword (.getName f)) (.get f obj)])))

(defmacro apply-method [& args]
  `(~@(drop-last args) ~@(eval (last args))))

;; utils
(defn rekey
  ([m kold knew]
    (-> m (dissoc kold) (assoc knew (get m kold))))
  ([m kold knew & ks]
    (reduce #(apply rekey %1 %2)
      m (partition 2 (conj ks knew kold)))))

; mmc utils
(defn set-stage-position
  ([stage-dev z] (. mmc setPosition z))
  ([stage-dev x y] (. mmc setXYPosition x y)))

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
    (rekey
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

; engine

(defn snap-image [event auto-shutter]
  (if (and auto-shutter (. mmc getShutterOpen))
    (. mmc setShutterOpen true)
    (. mmc waitForDevice (. mmc getShutterDevice)))
  (. mmc snapImage)
  (if (and auto-shutter (event :close-shutter))
     (. mmc setShutterOpen false))
  (. mmc getImage))

(defn start-burst [n-images]
  (. mmc startSequenceAcquisition n-images 0 false)) 

(defn collect-burst-image []
  (while (zero? (. mmc remainingImageCount))
    (Thread/sleep 5))
  (. mmc popNextImage))   
  
(defn update-channel [event]
  (when-let [channel (event :channel)]
    (let [channel-group (. mmc getChannelGroup)
          config (.config channel)]
      (. mmc setConfig channel-group config))))
      
(defn update-slice [event]
  (when-let [slice (event :slice)]
    (when-let [focusDevice (. mmc getFocusDevice)]
      (. mmc setPosition focusDevice slice))))

(defn get-stage-positions [^MultiStagePosition msp]
  (map #(.get msp %) (range (.size msp))))

(defn update-position [event]
  (when-let [position (event :position)]
    (for [sp (get-stage-positions position)]
      (let [stage (.stageName sp)]
        (condp = (.numAxes sp)
          1 (. mmc setPosition stage (.x sp))
          2 (. mmc setXYPosition stage (.x sp) (.y sp)))))))
          
(defn run-autofocus [event]
  (when (event :use-autofocus)
    (.. gui getAutofocusManager getDevice fullFocus)))
          
(defn clock-ms []
  (quot (System/nanoTime) 1000000))

(defn acq-sleep [event last-wake-time]
  (let [sleep-time (-
                     (+ last-wake-time (event :interval-ms))
                     (clock-ms))]
    (when (pos? sleep-time)
      (Thread/sleep sleep-time)))
  (clock-ms))
  
(defn wait-for-devices [event]
  (. mmc waitForDevice (. mmc getFocusDevice))
  ;; more
  )
  
(defn run-event [event last-wake-time]
  (update-channel event)
  (update-position event)
  (let [new-wake-time (acq-sleep event last-wake-time)]
    (run-autofocus event)
    (update-slice event)
    (wait-for-devices event)
    (snap-image event)
    new-wake-time))

(defn run-events [events]
  (doall (map run-event events)))
  
(defn run-acquisition [settings] 
  (def acq-settings settings)
  (let [acq-seq (generate-acq-sequence settings)]
     (def acq-sequence acq-seq)
     (doall (map println acq-seq))))
  
(defn convert-settings [^SequenceSettings settings]
  (-> settings
    (data-object-to-map)
    (assoc :frames (range (.numFrames settings))
           :channels (map ChannelSpec-to-map (.channels settings))
           :positions (map MultiStagePosition-to-map (.positions settings)))
    (rekey
      :slicesFirst             :slices-first
      :timeFirst               :time-first
      :keepShutterOpenSlices   :keep-shutter-open-slices
      :keepShutterOpenChannels :keep-shutter-open-channels
      :useAutofocus            :use-autofocus
      :skipAutofocusCount      :autofocus-skip
      :relativeZSlice          :relative-slices
      :intervalMs              :interval-ms
    )))

(defn run-acquisition-from-settings [^SequenceSettings settings]
  (def orig-settings settings)
  (run-acquisition (convert-settings settings)))

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

   

   