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

(ns org.micromanager.acq-engine
  (:use [org.micromanager.mm :only [map-config get-config get-positions 
                                    get-default-devices mmc gui]]
        [org.micromanager.sequence-generator :only [generate-acq-sequence]])
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
    )
    (assoc :properties (get-config (core getChannelGroup) (.config_ chan)))))

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

(declare state)

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
      "ZPositionUm" (get event :z)
      "AxisPositions" (when-let [axes (get-in event [:position :axes])] (JSONObject. axes))
    )))  
    
(defn annotate-image [img event]
  (TaggedImage. img
    (JSONObject. (merge
      {"ElapsedTime-ms" (- (clock-ms) (@state :start-time))
       "Time" (get-current-time-str)
       "Width"  (core getImageWidth)
       "Height" (core getImageHeight)
       "PixelType" (get-pixel-type)
       "Binning" (core getProperty (core getCameraDevice) "Binning")
       "UUID" (UUID/randomUUID)
      }
      (map-config (core getSystemStateCache))
      (generate-metadata event)
      ))))

;; acq-engine

(defn acq-sleep [interval-ms]
  (let [current-time (clock-ms)
        target-time (+ (@state :last-wake-time) interval-ms)
        sleep-time (- target-time current-time)]
    (when (pos? sleep-time)
      (Thread/sleep sleep-time)
      (alter state assoc :last-wake-time target-time))))

(declare device-agents)

(defn create-device-agents []
  (def device-agents
    (let [devs (seq (core getLoadedDevices))]
      (zipmap devs (repeatedly (count devs) #(agent nil))))))

(defn get-z-stage-position [stage]
  (core getPosition stage))

(defn set-stage-position
  ([stage-dev z] (log "setting z position to " z)
		 (core setPosition stage-dev z)
		 (dosync alter state assoc :last-z-position z))
  ([stage-dev x y] (log "setting x,y position to " x "," y)
                   (when (and x y) (core setXYPosition stage-dev x y))))

(defn set-property
  ([dev prop] (core setProperty (prop 0) (prop 1) (prop 2))))
  
(defn send-device-action [dev action]
  (send-off (device-agents dev) (fn [_] (action))))
    
(defn create-presnap-actions [event]
  (concat
    (when-let [z-drive (:z-drive event)]
      (when-let [z (:z event)]
        (when (and z (not= z (@state :last-z-position)))
          (list [z-drive #(set-stage-position z-drive z)]))))
    (for [[axis pos] (get-in event [:position :axes])]
      [axis #(apply set-stage-position axis pos)])
    (for [prop (get-in event [:channel :properties])]
      [(prop 0) #(core setProperty (prop 0) (prop 1) (prop 2))])
    (when-let [exposure (:exposure event)]
      (list [(core getCameraDevice) #(core setExposure exposure)]))))

(defn run-actions [action-map]
  (if run-devices-parallel
    (do
      (doseq [[dev action] action-map]
        (send-device-action dev action))
      (doseq [dev (keys action-map)]
        (send-device-action dev #(core waitForDevice dev)))
      (doall (map (partial await-for 10000) (vals device-agents))))
    (do
      (doseq [[dev action] action-map]
        (action) (core waitForDevice dev)))))

(defn run-autofocus []
  (.. gui getAutofocusManager getDevice fullFocus))

(defn snap-image [open-before close-after]
  (core setAutoShutter false)
  (if open-before
    (core setShutterOpen true)
    (core waitForDevice (core getShutterDevice)))
  (core snapImage)
  (if close-after
    (core setShutterOpen false))
    (core waitForDevice (core getShutterDevice))
  (core setAutoShutter (@state :init-auto-shutter)))

(defn init-burst [length]
  (core setAutoShutter (@state :init-auto-shutter))
  (core startSequenceAcquisition length 0 false))

(defn expose [event]
  (do (condp = (:task event)
    :snap (snap-image true (:close-shutter event))
    :init-burst (init-burst (:burst-length event))
    nil)))

(defn collect-burst-image []
  (while
    (and
      (core isSequenceRunning)
      (zero? (core getRemainingImageCount))) (Thread/sleep 5))
  (core popNextImage))
  
(defn collect-snap-image []
  (core getImage))

(defn collect-image [event out-queue]
  (let [image (condp = (:task event)
                :snap (collect-snap-image)
                :init-burst (collect-burst-image)
			    :collect-burst (collect-burst-image))]
    (.put out-queue (annotate-image image event))))
  
(defn store-z-correction [z event]
  (alter state assoc-in [:z-corrections (get-in event [:position :label])] z))
 
(defn compute-z-position [event]
  (if-let [z-drive (:z-drive event)]
    (-> event
      (assoc :z
				(+ (or (get-in event [:channel :z-offset]) 0)
				   (or (get (@state :z-corrections) z-drive)
				     (:slice event)
				     (@state :last-z-position))))
      (assoc-in [:postion :axes z-drive] nil))
    event))
   
(defn make-event-fns [event out-queue]
  (let [event (compute-z-position event)]
    (list
      ;#(println event)  
      #(run-actions (create-presnap-actions event))
      #(await-for 10000 (device-agents (core getCameraDevice)))
      #(when-let [wait-time-ms (event :wait-time-ms)]
        (acq-sleep wait-time-ms))
      #(when (:autofocus event)
        (store-z-correction (run-autofocus)))
      #(expose event)
      #(collect-image event out-queue)
    )))
  
(defn execute [event-fns]
  (doseq [event-fn event-fns :while (not (:stop (@state :interrupt-requests)))]
    (while (:pause (@state :interrupt-requests)) (Thread/sleep 5))
    (event-fn)))

(defn stop-acq []
  (dosync (alter state assoc-in [:interrupt-requests :stop] true)))
  
(defn pause-acq []
  (dosync (alter state assoc-in [:interrupt-requests :pause] true)))
  
(defn run-acquisition [settings out-queue] 
  (def acq-settings settings)
  (binding
    [state (ref {:interrupt-requests {:pause false :stop false}
                 :z-corrections nil
                 :last-wake-time (clock-ms)
                 :start-time (clock-ms)
                 :init-auto-shutter (core getAutoShutter)
                 :last-z-position (get-z-stage-position (core getFocusDevice))})]
    (let [acq-seq (generate-acq-sequence settings)]
       (def acq-sequence acq-seq)
       (def last-state state)
       (execute (mapcat #(make-event-fns % out-queue) acq-seq))
       (core setAutoShutter (@state :init-auto-shutter)))))

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

(defn set-to-absolute-slices [settings]
  (if (and (:slices settings) (:relative-slices settings))
    (assoc settings :slices
      (map (partial + (core getPosition (core getFocusDevice))) (:slices settings)))
    settings))

(defn run-pipeline [settings acq-eng]
  (create-device-agents)
	(let [out-queue (LinkedBlockingQueue.)]
		(.start (Thread. #(run-acquisition (set-to-absolute-slices (convert-settings settings)) out-queue)))
		(.start (LiveAcqDisplay. mmc out-queue settings (.channels settings) (.save settings) acq-eng))))

(defn create-acq-eng []
  (doto
    (proxy [AcquisitionWrapperEngine] []
      (runPipeline [^SequenceSettings settings]
        (def orig-settings settings)
        (println "ss positions: " (.size (.positions settings)))
        (println "position-count: " (.getNumberOfPositions (.getPositionList gui)))
				(run-pipeline settings this)
    (.setCore mmc (.getAutofocusManager gui))
    (.setParentGUI gui)
    (.setPositionList (.getPositionList gui))))))

(defn test-dialog [eng]
  (.show (AcqControlDlg. eng (Preferences/userNodeForPackage (.getClass gui)) gui)))

(defn run-test []
  (test-dialog (create-acq-eng)))

   
