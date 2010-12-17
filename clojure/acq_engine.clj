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

(ns acq-engine
  (:use [mm :only [mmc gui acq map-config get-config get-positions get-default-devices]]
        [sequence-generator :only [generate-acq-sequence]])
  (:import [org.micromanager AcqControlDlg]
           [org.micromanager.api AcquisitionEngine]
           [org.micromanager.acquisition AcquisitionWrapperEngine LiveAcqDisplay]
           [org.micromanager.acquisition.engine SequenceSettings]
           [org.micromanager.navigation MultiStagePosition StagePosition]
           [mmcorej TaggedImage Configuration]
           [java.util.prefs Preferences]
           [org.micromanager.utils ChannelSpec GentleLinkedBlockingQueue MDUtils]
           [org.json JSONObject]
           [java.util Date UUID]
           [java.util.concurrent LinkedBlockingQueue]
           [java.text SimpleDateFormat]
           ))

;; constants

(def run-devices-parallel false)

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

(defn clock-ms []
  (quot (System/nanoTime) 1000000))

;; mm utils

(def iso8601modified (SimpleDateFormat. "yyyy-MM-dd E HH:mm:ss Z"))

(defn get-current-time-str []
  (. iso8601modified format (Date.)))

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
  {:label (.getLabel msp)
   :axes
      (into {}
        (for [i (range (.size msp))]
          (let [stage-pos (.get msp i)]
            [(.stageName stage-pos)
              (condp = (.numAxes stage-pos)
                1 [(.x stage-pos)]
                2 [(.x stage-pos) (.y stage-pos)])])))})

;; globals

(declare last-wake-time)
(declare start-time)
(declare interrupt-requests)
(declare z-corrections)

;; metadata

(defn generate-metadata [event]
  (-> event
    (select-and-rekey
      :channel-index        "ChannelIndex"
      :frame-index          "Frame"
      :position-index       "PositionIndex"
      :slice-index          "Slice"
      :slice                "SlicePosition"
    )
    (assoc
      "PositionName" (get-in event [:position :label])
      "Channel" (get-in event [:channel :name])
      "PixelType" "GRAY8"
      "ZPositionUm" (get event :slice)
      "AxisPositions" (JSONObject. (get-in event [:position :axes]))
    )))  
    
(defn annotate-image [img event]
  (TaggedImage. img
    (JSONObject. (merge
      {"ElapsedTime-ms" (- (clock-ms) start-time)
       "Time" (get-current-time-str)
       "Width"  (. mmc getImageWidth)
       "Height" (. mmc getImageHeight)
       "Binning" (. mmc getProperty (. mmc getCameraDevice) "Binning")
       "UUID" (UUID/randomUUID)
      }
      (map-config (. mmc getSystemStateCache))
      (generate-metadata event)
      ))))

;; acq-engine

(defn acq-sleep [interval-ms]
  (let [current-time (clock-ms)
        target-time (+ @last-wake-time interval-ms)
        sleep-time (- target-time current-time)]
    (when (pos? sleep-time)
      (Thread/sleep sleep-time)
      (reset! last-wake-time target-time))))

(def device-agents
  (let [devs (seq (. mmc getLoadedDevices))]
    (zipmap devs (repeatedly (count devs) #(agent nil)))))

(defn get-z-stage-position [stage]
  (. mmc getPosition stage))

(defn set-stage-position
  ([stage-dev z] (when z (. mmc setPosition stage-dev z)))
  ([stage-dev x y] (when (and x y) (. mmc setXYPosition stage-dev x y))))

(defn set-property
  ([dev prop] (. mmc setProperty (prop 0) (prop 1) (prop 2))))
  
(defn send-device-action [dev action]
  (send-off (device-agents dev) (fn [_] (action))))
    
(defn create-presnap-actions [event]
  (concat
    (when-let [z-drive (:z-drive event)]
      (list [z-drive #(set-stage-position z-drive (:z event))]))
    (for [[axis pos] (get-in event [:position :axes])]
      [axis #(apply set-stage-position axis pos)])
    (for [prop (get-in event [:channel :properties])]
      [(prop 0) #(.setProperty mmc (prop 0) (prop 1) (prop 2))])))

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

(defn run-autofocus []
  (.. gui getAutofocusManager getDevice fullFocus))

(defn snap-image [open-before close-after]
  (if open-before
    (. mmc setShutterOpen true)
    (. mmc waitForDevice (. mmc getShutterDevice)))
  (. mmc snapImage)
  (if close-after
    (. mmc setShutterOpen false))
    (. mmc waitForDevice (. mmc getShutterDevice)))

(defn collect-image [event out-queue]
    (.put out-queue (annotate-image (. mmc getImage) event)))
  
(defn store-z-correction [z event]
  (alter z-corrections assoc (get-in event [:position :label]) z))
 
(defn compute-z-position [event]
  (if-let [z-drive (:z-drive event)]
    (-> event
      (assoc :z
        (if (:relative-slices event)
          (+ (:slice event)
            (or
              (get @z-corrections z-drive)
              (get-z-stage-position z-drive)))
          (:slice event)))
      (assoc-in [:postion :axes z-drive] nil))
    event))
   
(defn make-event-fns [event out-queue]
  (let [event (compute-z-position event)]
    (list
		#(run-actions (create-presnap-actions event))
		#(await-for 10000 (device-agents (. mmc getCameraDevice)))
		#(when-let [wait-time-ms (event :wait-time-ms)]
		  (acq-sleep wait-time-ms))
		#(when (event :autofocus)
		  (store-z-correction (run-autofocus)))
		#(snap-image true (:close-shutter event))
		#(collect-image event out-queue)
    )))
  
(defn execute [event-fns]
  (doseq [event-fn event-fns :while (not (:stop @interrupt-requests))]
    (while (@interrupt-requests :pause) (Thread/sleep 5))
    (event-fn)))

(defn stop-acq []
  (dosync (alter interrupt-requests assoc :stop true)))
  
(defn pause-acq []
  (dosync (alter interrupt-requests assoc :pause true)))
  
(defn run-acquisition [settings out-queue] 
  (def acq-settings settings)
  (binding [interrupt-requests (ref {:pause false :stop false})
            z-corrections (ref nil)
            last-wake-time (atom (clock-ms))
            start-time (clock-ms)]
    (let [acq-seq (generate-acq-sequence settings)]
       (def acq-sequence acq-seq)
       (execute (mapcat #(make-event-fns % out-queue) acq-seq)))))
  
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
        (def orig-settings settings)
        (println "ss positions: " (.size (.positions settings)))
        (println "position-count: " (.getNumberOfPositions (.getPositionList gui)))
        (let [out-queue (GentleLinkedBlockingQueue.)]
          (.start (Thread. #(run-acquisition (convert-settings settings) out-queue)))
          (.start (LiveAcqDisplay. mmc out-queue settings (.channels settings) (.save settings) this)))))
    (.setCore mmc (.getAutofocusManager gui))
    (.setParentGUI gui)
    (.setPositionList (.getPositionList gui))
    ))

(defn test-dialog [eng]
  (.show (AcqControlDlg. eng (Preferences/userNodeForPackage (.getClass gui)) gui)))

(defn run-test []
  (test-dialog (create-acq-eng)))

   