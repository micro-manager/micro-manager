; FILE:         acq_engine.clj
; PROJECT:      Micro-Manager
; SUBSYSTEM:    mmstudio acquisition engine
; ----------------------------------------------------------------------------
; AUTHOR:       Arthur Edelstein, arthuredelstein@gmail.com, Dec 14, 2010
;               Adapted from the acq eng by Nenad Amodaj and Nico Stuurman
; COPYRIGHT:    University of California, San Francisco, 2006-2011
; LICENSE:      This file is distributed under the BSD license.
;               License text is included with the source distribution.
;               This file is distributed in the hope that it will be useful,
;               but WITHOUT ANY WARRANTY; without even the implied warranty
;               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
;               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
;               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
;               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

(ns org.micromanager.acq-engine
  (:use [org.micromanager.mm :only [when-lets map-config get-config get-positions load-mm
                                    get-default-devices core log log-cmd mmc gui with-core-setting
                                    do-when if-args get-system-config-cached select-values-match?
                                    get-property]]
        [org.micromanager.sequence-generator :only [generate-acq-sequence]])
  (:import [org.micromanager AcqControlDlg]
           [org.micromanager.api AcquisitionEngine TaggedImageAnalyzer]
           [org.micromanager.acquisition AcquisitionWrapperEngine LiveAcqDisplay TaggedImageQueue
                                         ProcessorStack SequenceSettings MMImageCache
                                         TaggedImageStorageRam VirtualAcquisitionDisplay]
           [org.micromanager.navigation MultiStagePosition StagePosition]
           [mmcorej TaggedImage Configuration]
           [java.util.prefs Preferences]
           [java.net InetAddress]
           [java.util.concurrent TimeUnit CountDownLatch]
           [org.micromanager.utils ChannelSpec GentleLinkedBlockingQueue MDUtils
                                   ReportingUtils]
           [org.json JSONObject JSONArray]
           [java.util Date UUID]
           [java.text SimpleDateFormat]
           [ij ImagePlus])
   (:gen-class
     :name org.micromanager.AcqEngine
     :implements [org.micromanager.api.Pipeline]
     :init init
     :state state))

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
      (if (pos? (. p-list getNumberOfPositions))
        (. p-list (getPosition idx))))))

(defn get-z-position [idx z-stage]
  (if-let [msp (get-msp idx)]
    (if-let [stage-pos (. msp get z-stage)]
      (. stage-pos x))))

(defn set-z-position [idx z-stage z]
  (if-let [msp (get-msp idx)]
    (if-let [stage-pos (. msp (get z-stage))]
      (set! (. stage-pos x) z))))

;; globals

(def state (atom {:running false :stop false}))

(defn state-assoc! [& args]
  (apply swap! state assoc args))

;; metadata

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

(defn annotate-image [img event state]
  (println event)
  {:pix img
   :tags (merge
      (map-config (core getSystemStateCache))
      {
       "AxisPositions" (when-let [axes (get-in event [:position :axes])] (JSONObject. axes))
       "Binning" (core getProperty (core getCameraDevice) "Binning")
       "Channel" (get-in event [:channel :name])
       "ChannelIndex" (:channel-index event)
       "ElapsedTime-ms" (if (state :start-time) (- (clock-ms) (state :start-time)) 0)
       "Exposure-ms" (:exposure event)
       "Frame" (:frame-index event)
       "Height" (state :init-height)
       "NextFrame" (:next-frame-index event)
       "PixelSizeUm" (core getPixelSizeUm)
       "PixelType" (get-pixel-type)
       "PositionIndex" (:position-index event)
       "PositionName" (if-let [pos (:position event)] (if-args #(.getLabel %) (get-msp pos)))
       "Slice" (:slice-index event)
       "SlicePosition" (:slice event)
       "Source" (core getCameraDevice)
       "Time" (get-current-time-str)
       "UUID" (UUID/randomUUID)
       "Width"  (state :init-width)
       "ZPositionUm" (state :last-z-position)
      })})

(defn make-TaggedImage [annotated-img]
  (TaggedImage. (:pix annotated-img) (JSONObject. (:tags annotated-img))))

;; acq-engine

(defn await-resume []
  (while (and (:pause @state) (not (:stop @state))) (Thread/sleep 5)))

(defn interruptible-sleep [time-ms]
  (let [sleepy (CountDownLatch. 1)]
    (state-assoc! :sleepy sleepy :next-wake-time (+ (clock-ms) time-ms))
    (.await sleepy time-ms TimeUnit/MILLISECONDS)))

(defn acq-sleep [interval-ms]
  (when (and (@state :init-continuous-focus)
             (not (core isContinuousFocusEnabled)))
    (core enableContinuousFocus true))
  (let [target-time (+ (@state :last-wake-time) interval-ms)
        delta (- target-time (clock-ms))]
    (when (pos? delta)
      (interruptible-sleep delta))
    (await-resume)
    (let [now (clock-ms)
          wake-time (if (> now (+ target-time 10)) now target-time)]
      (state-assoc! :last-wake-time wake-time))))

(declare device-agents)

(defn create-device-agents []
  (def device-agents
    (let [devs (seq (core getLoadedDevices))]
      (zipmap devs (repeatedly (count devs) #(agent nil))))))

(defn get-z-stage-position [stage]
  (if (pos? (count stage)) (core getPosition stage) 0))
  
(defn set-z-stage-position [stage pos]
  (when (core isContinuousFocusEnabled)
    (core enableContinuousFocus false))
  (when (pos? (count stage)) (core setPosition stage pos)))

(defn set-stage-position
  ([stage-dev z] (log "setting z position to " z)
     (set-z-stage-position stage-dev z)
     (state-assoc! :last-z-position z))
  ([stage-dev x y] (log "setting x,y position to " x "," y)
                   (when (and x y) (core setXYPosition stage-dev x y))))

(defn set-property
  ([prop] (core setProperty (prop 0) (prop 1) (prop 2))))

(defn send-device-action [dev action]
  (send-off (device-agents dev) (fn [_] (action))))

(defn create-presnap-actions [event]
  (concat
    (for [[axis pos] (:axes (MultiStagePosition-to-map (get-msp (:position event)))) :when pos]
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
  (.. gui getAutofocusManager getDevice fullFocus)
  (state-assoc! :reference-z-position (core getPosition (core getFocusDevice))))

(defn snap-image [open-before close-after]
  (with-core-setting [getAutoShutter setAutoShutter false]
    (if open-before
      (core setShutterOpen true)
      (core waitForDevice (core getShutterDevice)))
    (core snapImage)
    (if close-after
      (core setShutterOpen false))
      (core waitForDevice (core getShutterDevice))))

(defn init-burst [length]
  (core setAutoShutter (@state :init-auto-shutter))
  (core startSequenceAcquisition length 0 false))

(defn expose [event]
  (do (condp = (:task event)
    :snap (snap-image true (:close-shutter event))
    :init-burst (init-burst (:burst-length event))
    nil)))

(defn collect-burst-image []
  (while (and (core isSequenceRunning) (zero? (core getRemainingImageCount)))
    (Thread/sleep 5))
  (core popNextImage))

(defn collect-snap-image []
  (core getImage))

(defn collect-image [event out-queue]
  (let [image (condp = (:task event)
                :snap (collect-snap-image)
                :init-burst (collect-burst-image)
                :collect-burst (collect-burst-image))]
    (.put out-queue (make-TaggedImage (annotate-image image event @state)))))

(defn compute-z-position [event]
  (if-let [z-drive (:z-drive event)]
    (+ (or (get-in event [:channel :z-offset]) 0)
       (or (:slice event) 0)
       (if (and (:slice event) (not (:relative-z event)))
         0
         (or (get-z-position (:position event) z-drive)
             (@state :reference-z-position))))))

(defn make-event-fns [event out-queue]
  (let [task (:task event)]
    (cond
      (= task :collect-burst)
        (list #(collect-image event out-queue))
      (or (= task :snap) (= task :init-burst))
        (list
          #(log event)
          #(when-let [wait-time-ms (event :wait-time-ms)]
            (acq-sleep wait-time-ms))
          #(run-actions (create-presnap-actions event))
          #(await-for 10000 (device-agents (core getCameraDevice)))
          #(when (:autofocus event)
            (set-z-position (:position event) (:z-drive event) (run-autofocus)))
          #(when-let [z-drive (:z-drive event)]
            (let [z (compute-z-position event)]
              (when (not= z (@state :last-z-position))
                (do (set-stage-position z-drive z)
                    (core waitForDevice z-drive)))))
          #(do (expose event)
               (collect-image event out-queue))))))

(defn execute [event-fns]
  (doseq [event-fn event-fns :while (not (:stop @state))]
    (try (event-fn) (catch Throwable e (ReportingUtils/logError e)))
    (await-resume)))

(defn return-config []
  (dorun (map set-property
    (clojure.set/difference
      (set (@state :init-system-state))
      (set (get-system-config-cached))))))

(defn cleanup []
  (log "cleanup")
  (do-when #(.update %) (:display @state))
  (state-assoc! :running false :display nil)
  (when (core isSequenceRunning)
    (core stopSequenceAcquisition))
  (core setAutoShutter (@state :init-auto-shutter))
  (core setExposure (@state :init-exposure))
  (when (not= (@state :last-z-position) (@state :init-z-position))
    (set-z-stage-position (core getFocusDevice) (@state :init-z-position)))
  (when (and (@state :init-continuous-focus)
             (not (core isContinuousFocusEnabled)))
    (core enableContinuousFocus true))
  (return-config))

(defn prepare-state [this]
  (let [z (get-z-stage-position (core getFocusDevice))]
    (swap! (.state this) assoc
      :pause false
      :stop false
      :running true
      :last-wake-time (clock-ms)
      :last-z-position z
      :reference-z-position z
      :start-time (clock-ms)
      :init-auto-shutter (core getAutoShutter)
      :init-exposure (core getExposure)
      :init-z-position z
      :init-system-state (get-system-config-cached)
      :init-continuous-focus (core isContinuousFocusEnabled)
      :init-width (core getImageWidth)
      :init-height (core getImageHeight))))

(defn run-acquisition [this settings out-queue]
  (def acq-settings settings)
  (prepare-state this)
  (binding [state (.state this)]
    (def last-state state)
    (let [acq-seq (generate-acq-sequence settings)]
       (def acq-sequence acq-seq)
       (execute (mapcat #(make-event-fns % out-queue) acq-seq))
       (.put out-queue TaggedImageQueue/POISON)
       (cleanup))))

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
           :positions (range (.. settings positions size)))))


(defn get-IJ-type [depth]
  (get {1 ImagePlus/GRAY8 2 ImagePlus/GRAY16 4 ImagePlus/COLOR_RGB 8 64} depth))

(defn get-z-step-um [slices]
  (if (and slices (< 1 (count slices)))
    (- (second slices) (first slices))
    0))

(defn get-channel-components [channel]
  (let [default-cam (get-property "Core" "Camera")
        chan-cam
          (or
            (first
              (filter #(= (take 2 %) '("Core" "Camera"))
                (:properties channel)))
            default-cam)]
    (set-property chan-cam)
    (let [n (int (core getNumberOfComponents))]
      (set-property default-cam)
      (get {1 1 , 4 3} n))))

(defn make-summary-metadata [settings]
  (let [depth (int (core getBytesPerPixel))
        channels (settings :channels)]
     (JSONObject. {
      "Channels" (count (settings :channels))
      "ChNames" (JSONArray. (map :name channels))
      "ChColors" (JSONArray. (map #(.getRGB (:color %)) channels))         
      "ChContrastMax" (JSONArray. (repeat (count channels) Integer/MIN_VALUE))
      "ChContrastMin" (JSONArray. (repeat (count channels) Integer/MAX_VALUE))
      "Comment" (settings :comment)
      "ComputerName" (.. InetAddress getLocalHost getHostName)
      "Depth" (core getBytesPerPixel)
      "Directory" (if (settings :save) (settings :root) "")
      "Frames" (count (settings :frames))
      "GridColumn" 0
      "GridRow" 0
      "Height" (core getImageHeight)
      "Interval_ms" (settings :interval-ms)
      "IJType" (get-IJ-type depth)
      "KeepShutterOpenChannels" (settings :keep-shutter-open-channels)
      "KeepShutterOpenSlices" (settings :keep-shutter-open-slices)
      "MicroManagerVersion" (.getVersion gui)
      "MetadataVersion" 10
      "PixelAspect" 1.0
      "PixelSize_um" (core getPixelSizeUm)
      "PixelType" (get-pixel-type)
      "Positions" (count (settings :positions))
      "Prefix" (if (settings :save) (settings :prefix) "")
      "Slices" (count (settings :slices))
      "SlicesFirst" (settings :slices-first)
      "Source" "Micro-Manager"
      "TimeFirst" (settings :time-first)
      "UserName" (System/getProperty "user.name")
      "UUID" (UUID/randomUUID)
      "Width" (core getImageWidth)
      "z-step_um" (get-z-step-um (settings :slices))
     })))

;; acquire button

(def current-album (atom nil))

(defn compatible-image? [album annotated-image]
  (select-values-match?
    (:first-image-tags album)
    (:tags annotated-image)
    ["Width" "Height" "PixelType"]))

(defn create-new-album [first-image]
  (let [summary {:interval-ms 0.0, :use-autofocus false, :autofocus-skip 0,
                 :relative-slices true, :keep-shutter-open-slices false, :comment "",
                 :prefix "Untitled", :root "",
                 :time-first false, :positions (), :channels (), :slices-first true,
                 :slices nil, :numFrames 0, :keep-shutter-open-channels false,
                 :zReference 0.0, :frames (), :save false}
            summary-metadata (make-summary-metadata summary)
	          cache (doto (MMImageCache. (TaggedImageStorageRam. summary-metadata))
	                  (.setSummaryMetadata summary-metadata))
	          display (doto (VirtualAcquisitionDisplay. cache nil))]
	          (reset! current-album {:cache cache :display display
	                                 :first-image-tags (:tags first-image)
	                                 :count 0})))

(defn acquire-single []
  (core snapImage)
  (let [w (core getImageWidth)
        h (core getImageHeight)
        event   {:position-index 2, :position -1,
                 :frame-index 0, :slice 0.0, :channel-index 0, :slice-index 0, :frame 0
                 :channel {:name (core getCurrentConfig (core getChannelGroup))},
                 :exposure (core getExposure), :relative-z true,
                 :task :snap, :z-drive (core getFocusDevice), :wait-time-ms 0}
        state   {:init-width w :init-height h}
        tagged-image (annotate-image (collect-snap-image) event state)]
	  (if-not (and @current-album
	               (compatible-image? @current-album tagged-image)
	               (not (.windowClosed (:display @current-album))))
	    (create-new-album tagged-image))
	  (let [{:keys [display cache]} @current-album
	        count (@current-album :count)
	        myTaggedImage
	          (make-TaggedImage
	            (update-in tagged-image [:tags] merge
	              {"PositionIndex" count "PositionName" (str "Snap" count)}))]
      (.putImage cache myTaggedImage)
      (doto display (.showImage myTaggedImage) .show)
      (swap! current-album update-in [:count] inc))))

;; java interop

(defn -init []
  [[] (atom {:running false :stop false})])

(defn -run [this acq-settings acq-eng]
  ;(def last-acq this)
  (def eng acq-eng)
  (load-mm)
  (create-device-agents)
  (swap! (.state this) assoc :stop false :pause false)
  (let [out-queue (GentleLinkedBlockingQueue.)
        settings (convert-settings acq-settings)
        acq-thread (Thread. #(run-acquisition this settings out-queue))
        processors (ProcessorStack. out-queue (.getTaggedImageProcessors acq-eng))
        out-queue-2 (.begin processors)
        display (LiveAcqDisplay. mmc out-queue-2 (make-summary-metadata settings)
                  (:save settings) acq-eng)]
    (def outq out-queue)
    ;(.addImageProcessor acq-eng
      ;(proxy [TaggedImageAnalyzer] []
        ;(analyze [img] (log "pretending to analyze"))))
    (when-not (:stop @(.state this))
      (if (. gui getLiveMode)
        (. gui enableLiveMode false))
      (.start acq-thread)
      (swap! (.state this) assoc :display display)
      (.start display))))

(defn -acquireSingle [this]
  (load-mm)
  (acquire-single))

(defn -pause [this]
  (log "pause requested!")
  (swap! (.state this) assoc :pause true))

(defn -resume [this]
  (log "resume requested!")
  (swap! (.state this) assoc :pause false))

(defn -stop [this]
  (log "stop requested!")
  (let [state (.state this)]
    (swap! state assoc :stop true)
    (do-when #(.countDown %) (:sleepy @state))
    (log @state)))

(defn -isRunning [this]
  (:running @(.state this)))

(defn -isPaused [this]
  (:pause @(.state this)))

(defn -stopHasBeenRequested [this]
  (:stop @(.state this)))

(defn -nextWakeTime [this]
  (or (:next-wake-time @(.state this)) -1))

;; testing

(defn create-acq-eng []
  (doto
    (proxy [AcquisitionWrapperEngine] []
      (runPipeline [^SequenceSettings settings]
        ;(def orig-settings settings)
        (println "ss positions: " (.size (.positions settings)))
        (println "position-count: " (.getNumberOfPositions (.getPositionList gui)))
        (-run settings this)
    (.setCore mmc (.getAutofocusManager gui))
    (.setParentGUI gui)
    (.setPositionList (.getPositionList gui))))))

(defn test-dialog [eng]
  (.show (AcqControlDlg. eng (Preferences/userNodeForPackage (.getClass gui)) gui)))

(defn run-test []
  (test-dialog (create-acq-eng)))


