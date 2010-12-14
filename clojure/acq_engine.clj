; FILE:         acq_engine.clj
; PROJECT:      Micro-Manager
; SUBSYSTEM:    mmstudio acquisition engine
; ----------------------------------------------------------------------------
; AUTHOR:       Arthur Edelstein, arthuredelstein@gmail.com, Dec 14, 2010
;               Adapted from the acq eng by Nenad Amodaj and Nico Stuurman
; COPYRIGHT:    University of California, San Francisco, 2006-2010
; LICENSE:      This file is distributed under the BSD license.
;               License text is included with the source distribution.
;               This file is distributed in the hope that it will be useful,
;               but WITHOUT ANY WARRANTY; without even the implied warranty
;               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
;               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
;               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
;               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
; CVS:          $ $
;   

(ns acq-engine
  (:use [mm :only [mmc gui acq]]
        [sequence-generator :only [generate-acq-sequence]])
  (:import [org.micromanager AcqControlDlg]
           [org.micromanager.api AcquisitionEngine]
           [org.micromanager.acquisition AcquisitionWrapperEngine LiveAcqDisplay]
           [org.micromanager.acquisition.engine SequenceSettings]
           [org.micromanager.navigation MultiStagePosition StagePosition]
           [mmcorej TaggedImage Configuration]
           [java.util.prefs Preferences]
           [org.micromanager.utils ChannelSpec MDUtils]
           [org.json JSONObject]
           [java.util.concurrent LinkedBlockingQueue]))

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

(def run-devices-parallel false)

;; mm utils
(defn get-default-devices []
  {:camera          (. mmc getCameraDevice)
   :shutter         (. mmc getShutterDevice)
   :focus           (. mmc getFocusDevice)
   :xy-stage        (. mmc getXYStageDevice)
   :autofocus       (. mmc getAutoFocusDevice)
   :image-processor (. mmc getImageProcessorDevice)})

(defn map-config [^Configuration config]
  (let [n (.size config)
        props (map #(.getSetting config %) (range n))]
    (into {}
      (for [prop props]
        [(str (.getDeviceLabel prop) "-" (.getPropertyName prop))
         (.getPropertyValue prop)]))))

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

(defn generate-metadata [event]
  (-> event
    (select-and-rekey
      :channel-index        "ChannelIndex"
      :frame-index          "Frame"
      :position-index       "PositionIndex"
      :slice-index          "Slice"
      :slice                "SlicePosition"
      "ElapsedTime-ms"      "ElapsedTime-ms"
    )
    (assoc
      "PositionName" (get-in event [:position :label])
      "Channel" (get-in event [:channel :name])
      "PixelType" "GRAY8"
      "ZPositionUm" (get event :slice)
    )))  

;; acq-engine
 
(defn clock-ms []
  (quot (System/nanoTime) 1000000))

(defn acq-sleep [last-wake-time interval-ms]
  (let [current-time (clock-ms)
        target-time (+ last-wake-time interval-ms)
        sleep-time (- target-time current-time)]
    (if (pos? sleep-time)
      (do (Thread/sleep sleep-time) target-time)
      current-time)))
 
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
  (if run-devices-parallel
    (do
      (doseq [[dev action] action-map]
        (send-device-action dev action))
      (doseq [dev (keys action-map)]
        (send-device-action dev #(. mmc waitForDevice dev)))
      (doall (map (partial await-for 10000) (vals device-agents))))
    (do
      (doseq [[dev action] action-map]
        (action) (. mmc waitForDevice dev)))))

(defn snap-image [open-before close-after]
  (if open-before
    (. mmc setShutterOpen true)
    (. mmc waitForDevice (. mmc getShutterDevice)))
  (. mmc snapImage)
  (if close-after
    (. mmc setShutterOpen false))
    (. mmc waitForDevice (. mmc getShutterDevice)))

(defn annotate-image [img event]
  (TaggedImage. img
    (JSONObject. (merge
      (map-config (. mmc getSystemStateCache))
      (generate-metadata event)
      {"ElapsedTime-ms" (clock-ms)}))))

(defn collect-image [event out-queue]
    (.add out-queue (annotate-image (. mmc getImage) event)))
  
(defn run-event [event last-wake-time out-queue]
  (run-actions (create-presnap-actions event))
  (await-for 10000 (device-agents (. mmc getCameraDevice)))
  (when-let [wait-time-ms (event :wait-time-ms)]
    (swap! last-wake-time acq-sleep wait-time-ms))
  (snap-image true true)
  (collect-image event out-queue))
  ;(send-device-action (. mmc getCameraDevice)
  ;  #(collect-image (assoc event :time (clock-ms)) out-queue)))
  
(defn run-acquisition [settings out-queue] 
  (def acq-settings settings)
  (let [acq-seq (generate-acq-sequence settings)
        last-wake-time (atom (clock-ms))]
     (def acq-sequence acq-seq)
     (dorun (map #(run-event % last-wake-time out-queue) acq-seq))))
  
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

(defn create-acq-eng []
  (doto
    (proxy [AcquisitionWrapperEngine] []
      (runPipeline [^SequenceSettings settings]
        (let [out-queue (LinkedBlockingQueue.)]
          (.start (Thread. #(run-acquisition (convert-settings settings) out-queue)))
          (.start (LiveAcqDisplay. mmc out-queue settings (.channels settings) (.save settings) this)))))
    (.setCore mmc (.getAutofocusManager gui))
    (.setParentGUI gui)
    (.setPositionList (.getPositionList gui))))

(defn test-dialog [eng]
  (.show (AcqControlDlg. eng (Preferences/userNodeForPackage (.getClass gui)) gui)))


   