; FILE:         acq_engine.clj
; PROJECT:      Micro-Manager
; SUBSYSTEM:    mmstudio acquisition engine
; ----------------------------------------------------------------------------
; AUTHOR:       Arthur Edelstein, arthuredelstein@gmail.com, 2010-2011
;               Developed from the acq eng by Nenad Amodaj and Nico Stuurman
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
  (:use [org.micromanager.mm :only
          [when-lets map-config get-config get-positions load-mm
           get-default-devices core log log-cmd mmc gui with-core-setting
           do-when if-args get-system-config-cached select-values-match?
           get-property get-camera-roi parse-core-metadata reload-device
           json-to-data get-pixel-type get-msp-z-position set-msp-z-position
           get-msp MultiStagePosition-to-map ChannelSpec-to-map
           get-pixel-type get-current-time-str rekey
           data-object-to-map str-vector double-vector
           get-property-value edt]]
        [org.micromanager.sequence-generator :only [generate-acq-sequence
                                                    make-property-sequences]])
  (:require [clojure.set])
  (:import [org.micromanager AcqControlDlg]
           [org.micromanager.api AcquisitionEngine TaggedImageAnalyzer]
           [org.micromanager.acquisition AcquisitionWrapperEngine LiveAcq TaggedImageQueue
                                         ProcessorStack SequenceSettings MMImageCache
                                         TaggedImageStorageRam VirtualAcquisitionDisplay]
           [org.micromanager.utils ReportingUtils]
           [mmcorej TaggedImage Configuration Metadata]
           [java.util.prefs Preferences]
           [java.net InetAddress]
           [java.util.concurrent TimeUnit CountDownLatch]
           [org.micromanager.utils GentleLinkedBlockingQueue MDUtils
                                   ReportingUtils]
           [org.json JSONObject JSONArray]
           [java.util Date UUID]
           [javax.swing SwingUtilities]
           [ij ImagePlus])
   (:gen-class
     :name org.micromanager.AcqEngine
     :implements [org.micromanager.api.Pipeline]
     :init init
     :state state))

;; globals

(def ^:dynamic state (atom {:stop false}))

(def settings (atom nil))

(defn state-assoc! [& args]
  (apply swap! state assoc args))
   
(def attached-runnables (atom (vec nil)))

(def pending-devices (atom #{}))

(defn add-to-pending [dev]
  (swap! pending-devices conj dev))

(def active-property-sequences (atom nil))

(def active-slice-sequence (atom nil))

(def pixel-type-depths {"GRAY8" 1 "GRAY16" 2 "RGB32" 4 "RGB64" 8})

;; time

(defn jvm-time-ms []
  (quot (System/nanoTime) 1000000))

(defn elapsed-time [state]
  (if (state :start-time) (- (jvm-time-ms) (state :start-time)) 0))

(defn core-time-from-tags [tags]
  (try (Double/parseDouble (tags "ElapsedTime-ms")) (catch Exception e nil)))

(defn burst-time [tags state]
  (when (and (:burst-time-offset state) (get tags "ElapsedTime-ms"))
    (+ (core-time-from-tags tags)
       (:burst-time-offset state))))

;; image metadata

(defn generate-metadata [event state]
  (merge
    (map-config (core getSystemStateCache))
    (:metadata event)
    (let [[x y] (let [xy-stage (state :default-xy-stage)]
                  (when-not (empty? xy-stage)
                    (get-in state [:last-stage-positions xy-stage])))]
      {
       "AxisPositions" (when-let [axes (get-in event [:position :axes])]
                         (JSONObject. axes))
       "Binning" (state :binning)
       "BitDepth" (state :bit-depth)
       "Channel" (get-in event [:channel :name])
       "ChannelIndex" (:channel-index event)
       "Exposure-ms" (:exposure event)
       "Frame" (:frame-index event)
       "FrameIndex" (:frame-index event)
       "Height" (state :init-height)
       "NextFrame" (:next-frame-index event)
       "PixelSizeUm" (state :pixel-size-um)
       "PixelType" (state :pixel-type)
       "PositionIndex" (:position-index event)
       "PositionName" (if-let [pos (:position event)] (if-args #(.getLabel %) (get-msp pos)))
       "Slice" (:slice-index event)
       "SliceIndex" (:slice-index event)
       "SlicePosition" (:slice event)
       "Source" (state :source)
       "Time" (get-current-time-str)
       "UUID" (UUID/randomUUID)
       "WaitInterval" (:wait-time-ms event)
       "Width"  (state :init-width)
       "XPositionUm" x
       "YPositionUm" y
       "ZPositionUm" (get-in state [:last-stage-positions (state :default-z-drive)])
      })))
   
(defn annotate-image [img event state elapsed-time-ms]
  {:pix (:pix img)
   :tags 
   (merge
     (generate-metadata event state)
     (:tags img)
     {"ElapsedTime-ms" elapsed-time-ms}
     )}) ;; include any existing metadata

(defn make-TaggedImage [annotated-img]
  (TaggedImage. (:pix annotated-img) (JSONObject. (:tags annotated-img))))

;; hardware error handling

(defmacro successful? [& body]
  `(try (do ~@body true)
     (catch Exception e#
            (do (ReportingUtils/logError e#) false))))

(defmacro device-best-effort [device & body]
  `(let [attempt# #(do (wait-for-device ~device)
                       (add-to-pending ~device)
                       ~@body)]
    (when-not
      (or 
        (successful? (attempt#)) ; first attempt
        (do (log "second attempt") (successful? (attempt#)))
        (do (log "reload and try a third time")
            (successful? (reload-device ~device) (attempt#)))) ; third attempt after reloading
      (throw (Exception. (str "Device failure: " ~device)))
      (swap! state assoc :stop true)
      nil)))

;; hardware control

(defn wait-for-device [dev]
  (when-not (empty? dev)
    (try
      (core waitForDevice dev)
      (swap! pending-devices disj dev)
      (catch Exception e (log "wait for device" dev "failed.")))))

(defn set-exposure [camera exp]
  (when (not= exp (get-in @state [camera :exposures]))
    (device-best-effort (core setExposure exp))
    (swap! state assoc-in [camera :exposures] exp)))

(defn wait-for-pending-devices []
  (log "pending devices: " @pending-devices)
  (dorun (map wait-for-device @pending-devices)))

(defn get-z-stage-position [stage]
  (if-not (empty? stage) (core getPosition stage) 0))
  
(defn get-xy-stage-position [stage]
  (if-not (empty? stage)
    (let [xy (.getXYStagePosition gui)]
      [(.x xy) (.y xy)])))

(defn set-z-stage-position [stage pos]
  (when-not (empty? stage)
    (when (and (core isContinuousFocusEnabled)
               (not (core isContinuousFocusDrive stage)))
      (core enableContinuousFocus false))
      (device-best-effort stage (core setPosition stage pos))))

(defn set-stage-position
  ([stage-dev z]
    (when (not= z (get-in @state [:last-stage-positions stage-dev]))
      (set-z-stage-position stage-dev z)
      (swap! state assoc-in [:last-stage-positions stage-dev] z)))
  ([stage-dev x y]
    (when (and x y
               (not= [x y] (get-in @state [:last-stage-positions stage-dev])))
      (device-best-effort stage-dev (core setXYPosition stage-dev x y))
      (swap! state assoc-in [:last-stage-positions stage-dev] [x y]))))

(defn set-property
  ([prop] (let [[d p v] prop]
            (device-best-effort d (core setProperty d p v)))))

(defn run-autofocus []
  (let [z-drive (@state :default-z-drive)
        z0 (get-z-stage-position z-drive)]
  (try
    (let [z (.. gui getAutofocusManager getDevice fullFocus)]
      (swap! state assoc-in [:last-stage-positions (@state :default-z-drive)] z))
    (catch Exception e
           (ReportingUtils/logError e)
           (set-stage-position :default-z-drive z-drive (+ 1.0e-6 z0)))))
    (log "running autofocus " (.. gui getAutofocusManager getDevice getDeviceName)))

(defn snap-image [open-before close-after]
  (with-core-setting [getAutoShutter setAutoShutter false]
    (let [shutter (core getShutterDevice)]
      (when open-before
        (device-best-effort shutter
          (core setShutterOpen true)))
      (wait-for-pending-devices)
      (device-best-effort (core getCameraDevice) (core snapImage))
      (swap! state assoc :last-image-time (elapsed-time @state))
      (when close-after
        (device-best-effort shutter
          (core setShutterOpen false))))))

(defn load-property-sequences [property-sequences]
  (let [new-seq (not= property-sequences @active-property-sequences)]
    (doseq [[[d p] s] property-sequences]
      (log "property sequence:" (seq (str-vector s)))
      (when new-seq
        (core loadPropertySequence d p (str-vector s))))
    (reset! active-property-sequences property-sequences)))
  
(defn load-slice-sequence [slice-sequence relative-z]
  (when slice-sequence
    (let [z (core getFocusDevice)
          ref (@state :reference-z)
          adjusted-slices (vec (if relative-z
                                 (map #(+ ref %) slice-sequence)
                                 slice-sequence))
          new-seq (not= [z adjusted-slices] @active-slice-sequence)]
      (when new-seq
        (core loadStageSequence z (double-vector adjusted-slices)))
      (reset! active-slice-sequence [z adjusted-slices])
      adjusted-slices)))

(defn start-property-sequences [property-sequences]
  (doseq [[[d p] _] property-sequences]
    (core startPropertySequence d p)))
          
(defn start-slice-sequence []
  (core startStageSequence (core getFocusDevice)))

(defn first-trigger-missing? []
  (= "1" (get-property-value (core getCameraDevice) "OutputTriggerFirstMissing")))

(defn init-burst [length trigger-sequence relative-z]
  (core setAutoShutter (@state :init-auto-shutter))
  (load-property-sequences (:properties trigger-sequence))
  (let [absolute-slices (load-slice-sequence (:slices trigger-sequence) relative-z)]
    (start-property-sequences (:properties trigger-sequence))
    (when absolute-slices
      (start-slice-sequence))
    (core startSequenceAcquisition (if (first-trigger-missing?) (inc length) length) 0 true)
    (swap! state assoc-in [:last-stage-positions (core getFocusDevice)]
           (last absolute-slices))))
  
(defn pop-burst-image []
  (while (and (. mmc isSequenceRunning) (zero? (. mmc getRemainingImageCount)))
    (Thread/sleep 5))
  (let [md (Metadata.)
        pix (core popNextImageMD md)
        tags (parse-core-metadata md)]
    {:pix pix :tags (dissoc tags "StartTime-ms")}))
    
(defn make-multicamera-channel [camera-channel raw-channel-index]
  (+ camera-channel (* (core getNumberOfCameraChannels) raw-channel-index)))

(defn make-multicamera-events [event]
  (let [num-camera-channels (core getNumberOfCameraChannels)]
    (for [camera-channel (range num-camera-channels)]
      (let [super-channel-index (make-multicamera-channel camera-channel (event :channel-index))]
        (assoc event :channel-index super-channel-index
               :camera-channel-index camera-channel)))))
  
(defn collect-burst-images [event out-queue]
  (when (first-trigger-missing?)
    (pop-burst-image)) ; drop first image if first trigger doesn't happen
  (swap! state assoc :burst-time-offset nil)
  (let [slices (second @active-slice-sequence)
        camera-index (str (core getCameraDevice) "-CameraChannelIndex")]
    (doseq [event (:burst-data event)]
      (doseq [i (range (core getNumberOfCameraChannels))]
        (when-not (@state :stop)
          (let [image (pop-burst-image)
                image+ (if-not slices
                         image
                         (assoc-in image [:tags "ZPositionUm"] (get slices i)))]
            (when (zero? i)
              (swap! state assoc
                     :burst-time-offset (- (elapsed-time @state)
                                           (core-time-from-tags (image :tags)))))
            (let [cam-chan (get-in image [:tags camera-index])
                  event+ (if cam-chan
                           (assoc event :channel-index
                                  (Long/parseLong cam-chan))
                           event)]
              (.put out-queue (make-TaggedImage (annotate-image image+ event+ @state
                                                                (burst-time (:tags image) @state))))))
          (when (core isBufferOverflowed)
            (swap! state assoc :stop true)
            (ReportingUtils/showError "Circular buffer overflowed."))))))
  (while (and (not (@state :stop)) (. mmc isSequenceRunning))
    (Thread/sleep 5)))

(defn collect-snap-image [event out-queue]
  (let [image
        {:pix (core getImage (event :channel-index))
         :tags nil}]
    (select-keys event [:position-index :frame-index
                        :slice-index :channel-index])
    (when out-queue
      (.put out-queue
            (make-TaggedImage (annotate-image image event @state (elapsed-time @state)))))
    image))

(defn return-config []
  (dorun (map set-property
    (clojure.set/difference
      (set (@state :init-system-state))
      (set (get-system-config-cached))))))

(defn stop-trigger []
  (doseq [[[d p] _] @active-property-sequences]
    (core stopPropertySequence d p)
    (reset! active-property-sequences nil))
  (when @active-slice-sequence
    (core stopStageSequence (first @active-slice-sequence))
    (reset! active-slice-sequence nil)))

;; sleeping

(defn await-resume []
  (while (and (:pause @state) (not (:stop @state))) (Thread/sleep 5)))

(defn interruptible-sleep [time-ms]
  (let [sleepy (CountDownLatch. 1)]
    (state-assoc! :sleepy sleepy :next-wake-time (+ (jvm-time-ms) time-ms))
    (.await sleepy time-ms TimeUnit/MILLISECONDS)))

(defn acq-sleep [interval-ms]
  (log "acq-sleep")
  (when (and (@state :init-continuous-focus)
    (not (core isContinuousFocusEnabled)))
      (core enableContinuousFocus true))
  (let [target-time (+ (@state :last-wake-time) interval-ms)
        delta (- target-time (jvm-time-ms))]
    (when (pos? delta)
      (interruptible-sleep delta))
    (await-resume)
    (let [now (jvm-time-ms)
          wake-time (if (> now (+ target-time 10)) now target-time)]
      (state-assoc! :last-wake-time wake-time))))

;; higher level

(defn expose [event]
  (let [shutter-states
         (if (core getAutoShutter)
           [true (:close-shutter event)]
           [false false])]
    (condp = (:task event)
      :snap (apply snap-image shutter-states)
      :burst (init-burst (count (:burst-data event))
                         (:trigger-sequence event)
                         (:relative-z event))
      nil)))

(defn collect [event out-queue]
  (condp = (:task event)
                :snap (doseq [sub-event (make-multicamera-events event)]
                        (collect-snap-image sub-event out-queue))
                :burst (collect-burst-images event out-queue)))

(defn z-in-msp [msp z-drive]
  (-> msp MultiStagePosition-to-map :axes (get z-drive) first))

(defn compute-z-position [event]
  (let [z-ref (or (-> event :position get-msp (z-in-msp (@state :default-z-drive)))
                  (@state :reference-z))]
    (+ (or (get-in event [:channel :z-offset]) 0) ;; add a channel offset if there is one.
       (if-let [slice (:slice event)]
         (+ slice
            (if (:relative-z event)
              z-ref
              0))
         z-ref))))

(defn update-z-positions [msp-index]
  (when-let [msp (get-msp msp-index)]
    (dotimes [i (.size msp)]
      (let [stage-pos (.get msp i)
            stage-name (.stageName stage-pos)]
        (when (= 1 (.numAxes stage-pos))
          (when (or (not (core isContinuousFocusEnabled))
                    (core isContinuousFocusDrive stage-name))
            (set-msp-z-position msp-index stage-name (get-z-stage-position stage-name))))))))

(defn recall-z-reference [current-position]
  (let [z-drive (@state :default-z-drive)]
    (when (or (not (core isContinuousFocusEnabled))
              (core isContinuousFocusDrive z-drive))
      (set-z-stage-position z-drive
        (or (get-msp-z-position current-position z-drive)
            (@state :reference-z))))))

(defn store-z-reference [current-position]
  (let [z-drive (@state :default-z-drive)]
    (when (and (or (not (core isContinuousFocusEnabled))
                   (core isContinuousFocusDrive z-drive)))
      (let [z (get-z-stage-position z-drive)]
        (state-assoc! :reference-z z)))))

;; startup and shutdown

(defn prepare-state [this]
  (let [default-z-drive (core getFocusDevice)
        default-xy-stage (core getXYStageDevice)
        z (get-z-stage-position default-z-drive)
        xy (get-xy-stage-position default-xy-stage)
        exposure (core getExposure)]
    (swap! (.state this) assoc
      :pause false
      :stop false
      :finished false
      :last-wake-time (jvm-time-ms)
      :last-stage-positions (into {} [[default-z-drive z]
                                [default-xy-stage xy]])
      :last-position nil     
      :reference-z z
      :start-time (jvm-time-ms)
      :init-auto-shutter (core getAutoShutter)
      :init-exposure exposure
      :exposures {(core getCameraDevice) exposure}
      :default-z-drive default-z-drive
      :default-xy-stage default-xy-stage
      :init-z-position z
      :init-system-state (get-system-config-cached)
      :init-continuous-focus (core isContinuousFocusEnabled)
      :init-width (core getImageWidth)
      :init-height (core getImageHeight)
      :binning (core getProperty (core getCameraDevice) "Binning")
      :bit-depth (core getImageBitDepth)
      :pixel-size-um (core getPixelSizeUm)
      :source (core getCameraDevice)
      :pixel-type (get-pixel-type)
      )))

(defn cleanup []
  (try
    (log "cleanup")
    ; (do-when #(.update %) (:display @state))
    (state-assoc! :finished true :display nil)
    (when (core isSequenceRunning)
      (core stopSequenceAcquisition))
    (stop-trigger)
    (core setAutoShutter (@state :init-auto-shutter))
    (set-exposure (core getCameraDevice) (@state :init-exposure))
    (set-stage-position (@state :default-z-drive) (@state :init-z-position))
    (when (and (@state :init-continuous-focus)
               (not (core isContinuousFocusEnabled)))
      (core enableContinuousFocus true))
    (return-config)
    (catch Throwable t (ReportingUtils/showError t "Acquisition cleanup failed."))))

;; running events
  
(defn make-event-fns [event out-queue]
  (let [current-position (:position event)
        z-drive (@state :default-z-drive)
        check-z-ref (and z-drive
                         (or (:autofocus event)
                             (:wait-time-ms event)))]
    (filter identity
      (flatten
        (list
          #(log event)
          (for [[axis pos] (:axes (MultiStagePosition-to-map (get-msp current-position)))
                :when pos]
            #(apply set-stage-position axis pos))
          (for [[d p v] (get-in event [:channel :properties])]
            #(set-property [d p v]))
          #(when-lets [exposure (:exposure event)
                       camera (core getCameraDevice)]
             (device-best-effort set-exposure exposure))
          #(when check-z-ref
             (recall-z-reference current-position))
          #(when-let [wait-time-ms (:wait-time-ms event)]
             (acq-sleep wait-time-ms))
          #(when (get event :autofocus)
             (run-autofocus))
          #(when check-z-ref
             (store-z-reference current-position))
          #(update-z-positions current-position)
          #(when z-drive
             (let [z (compute-z-position event)]
               (set-stage-position z-drive z)))
          (for [runnable (event :runnables)]
            #(.run runnable))
          #(device-best-effort (core getCameraDevice)
             (wait-for-pending-devices)
             (expose event)
             (collect event out-queue)
             (stop-trigger)))))))

(defn execute [event-fns]
  (doseq [event-fn event-fns :while (not (:stop @state))]
    (try (event-fn) (catch Throwable e (ReportingUtils/logError e)))
    (await-resume)))

(defn run-acquisition [this settings out-queue]
  (try
    (def acq-settings settings)
    (prepare-state this)
    (binding [state (.state this)]
      (def last-state state) ; for debugging
      (let [acq-seq (generate-acq-sequence settings @attached-runnables)]
        (def acq-sequence acq-seq)
        (execute (mapcat #(make-event-fns % out-queue) acq-seq))
        (.put out-queue TaggedImageQueue/POISON)
        (cleanup)
        ))
    (catch Throwable t (do (ReportingUtils/showError t "Acquisition failed.")
                           (cleanup)))))

;; generic metadata

(defn convert-settings [^SequenceSettings settings]
  (def seqSettings settings)
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
      :customIntervalsMs       :custom-intervals-ms
    )
    (assoc :frames (range (.numFrames settings))
           :channels (vec (filter :use-channel (map ChannelSpec-to-map (.channels settings))))
           :positions (vec (range (.. settings positions size)))
           :slices (vec (.slices settings))
           :default-exposure (core getExposure)
           :custom-intervals-ms (vec (.customIntervalsMs settings)))))

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
    (let [n (long (core getNumberOfComponents))]
      (set-property default-cam)
      (get {1 1 , 4 3} n))))

(defn get-camera-channel-names []
  (map #(core getCameraChannelName %)
       (range (core getNumberOfCameraChannels))))
       
(defn super-channels [simple-channel camera-channel-names]
  (map #(update-in simple-channel [:name] str "-" %) camera-channel-names))

(defn all-super-channels [simple-channels camera-channel-names]
  (flatten (map #(super-channels % camera-channel-names) simple-channels)))

(defn make-summary-metadata [settings]
  (let [depth (core getBytesPerPixel)
        channels (settings :channels)
        num-camera-channels (core getNumberOfCameraChannels)
        simple-channels (if-not (empty? channels) channels [{:name "Default" :color java.awt.Color/WHITE}])
        super-channels (all-super-channels simple-channels (get-camera-channel-names))]
     (JSONObject. {
      "BitDepth" (core getImageBitDepth)
      "Channels" (count super-channels)
      "ChNames" (JSONArray. (map :name super-channels))
      "ChColors" (JSONArray. (map #(.getRGB (:color %)) super-channels))         
      "ChContrastMax" (JSONArray. (repeat (count super-channels) Integer/MIN_VALUE))
      "ChContrastMin" (JSONArray. (repeat (count super-channels) Integer/MAX_VALUE))
      "Comment" (settings :comment)
      "ComputerName" (.. InetAddress getLocalHost getHostName)
      "Depth" (core getBytesPerPixel)
      "Directory" (if (settings :save) (settings :root) "")
      "Frames" (count (settings :frames))
      "GridColumn" 0
      "GridRow" 0
      "Height" (core getImageHeight)
      "Interval_ms" (settings :interval-ms)
      "CustomIntervals_ms" (JSONArray. (settings :custom-intervals-ms))
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
      "ROI" (JSONArray. (get-camera-roi))
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

(def current-album-tags (atom nil))

(def albums (atom {}))

(def current-album-name (atom nil))

(def snap-window (atom nil))

(defn compatible-to-current-album? [image-tags]
  (select-values-match?
    @current-album-tags
    image-tags
    ["Width" "Height" "PixelType"]))  

(defn initialize-display-ranges [window]  
  (do (.setChannelDisplayRange window 0 0 256)
      (.setChannelDisplayRange window 1 0 256)
      (.setChannelDisplayRange window 2 0 256)))

(defn create-image-window [first-image]
  (let [summary {:interval-ms 0.0, :custom-intervals-ms [] :use-autofocus false, :autofocus-skip 0,
                 :relative-slices true, :keep-shutter-open-slices false, :comment "",
                 :prefix "Untitled", :root "",
                 :time-first false, :positions (), :channels (), :slices-first true,
                 :slices nil, :numFrames 0, :keep-shutter-open-channels false,
                 :zReference 0.0, :frames (), :save false}
		summary-metadata (make-summary-metadata summary)
		cache (doto (MMImageCache. (TaggedImageStorageRam. summary-metadata))
						(.setSummaryMetadata summary-metadata))]
		(doto (VirtualAcquisitionDisplay. cache nil "Untitled")
                           (.promptToSave false)
                           (initialize-display-ranges))))

(defn create-basic-event []
  {:position-index 0, :position nil,
   :frame-index 0, :slice 0.0, :channel-index 0, :slice-index 0, :frame 0
   :channel {:name (core getCurrentConfig (core getChannelGroup))},
   :exposure (core getExposure), :relative-z true,
   :wait-time-ms 0})

(defn create-basic-state []
  {:init-width (core getImageWidth)
   :init-height (core getImageHeight)
   :pixel-type (get-pixel-type)
   :binning (core getProperty (core getCameraDevice) "Binning")})

(defn acquire-tagged-image []
  (binding [state (atom (create-basic-state))]
    (let [event (create-basic-event)]
      (core snapImage)
      (annotate-image (collect-snap-image event nil) event @state nil))))
    
(defn show-image [display tagged-img focus]
  (let [myTaggedImage (make-TaggedImage tagged-img)
        cache (.getImageCache display)]
    (.putImage cache myTaggedImage)
    (.showImage display (. myTaggedImage tags) true false) 
    (when focus
      (.show display))))

(defn add-to-album []
  (.addToAlbum gui (make-TaggedImage (acquire-tagged-image))))


;; java interop

(defn -init []
  [[] (atom {:stop false})])

(defn -run [this acq-settings acq-eng]
  (def last-acq this)
  (def eng acq-eng)
  (load-mm)
  (swap! (.state this) assoc :stop false :pause false :finished false)
  (let [out-queue (GentleLinkedBlockingQueue.)
        settings (convert-settings acq-settings)
        acq-thread (Thread. #(run-acquisition this settings out-queue)
                     "Acquisition Engine Thread (Clojure)")
        processors (ProcessorStack. out-queue (.getTaggedImageProcessors acq-eng))
        out-queue-2 (.begin processors)
        summary-metadata (make-summary-metadata settings)
        live-acq (LiveAcq. mmc out-queue-2 summary-metadata
                  (:save settings) acq-eng)]
    (swap! (.state this) assoc :image-cache (.getImageCache live-acq)
                               :acq-thread acq-thread
                               :summary-metadata summary-metadata)
    (def outq out-queue)
    (when-not (:stop @(.state this))
      (if (. gui getLiveMode)
        (. gui enableLiveMode false))
      (.start acq-thread)
      (swap! (.state this) assoc :display live-acq)
      (.start live-acq))))

(defn -acquireSingle [this]
  (load-mm)
  (add-to-album))

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
  (if-let [acq-thread (:acq-thread @(.state this))]
    (.isAlive acq-thread)
    false))

(defn -isFinished [this]
  (or (get @(.state this) :finished) false))

(defn -isPaused [this]
  (:pause @(.state this)))

(defn -stopHasBeenRequested [this]
  (:stop @(.state this)))

(defn -nextWakeTime [this]
  (or (:next-wake-time @(.state this)) -1))

;; attaching runnables

(defn -attachRunnable [this f p c s runnable]
  (let [template (into {}
          (for [[k v]
                {:frame-index f :position-index p
                 :channel-index c :slice-index s}
                 :when (not (neg? v))]
            [k v]))]
    (swap! attached-runnables conj [template runnable])))
  
(defn -clearRunnables [this]
  (reset! attached-runnables (vec nil)))


(defn -getImageCache [this]
  (:image-cache @(.state this)))

;; testing

(defn create-acq-eng []
  (doto
    (proxy [AcquisitionWrapperEngine] []
      (runPipeline [^SequenceSettings settings]
        (-run settings this)
    (.setCore mmc (.getAutofocusManager gui))
    (.setParentGUI gui)
    (.setPositionList (.getPositionList gui))))))

(defn test-dialog [eng]
  (.show (AcqControlDlg. eng (Preferences/userNodeForPackage (.getClass gui)) gui)))

(defn run-test []
  (test-dialog (create-acq-eng)))

(defn stop []
  (when-let [acq-thread (:acq-thread (.state last-acq))]
    (.stop acq-thread)))

