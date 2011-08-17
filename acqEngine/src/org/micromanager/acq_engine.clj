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
  (:use [org.micromanager.mm :only
          [when-lets map-config get-config get-positions load-mm
           get-default-devices core log log-cmd mmc gui with-core-setting
           do-when if-args get-system-config-cached select-values-match?
           get-property get-camera-roi parse-core-metadata reload-device
           json-to-data get-pixel-type get-msp-z-position set-msp-z-position
           get-msp MultiStagePosition-to-map ChannelSpec-to-map
           get-pixel-type get-current-time-str rekey
           data-object-to-map str-vector double-vector
           get-property-value]]
        [org.micromanager.sequence-generator :only [generate-acq-sequence
                                                    make-property-sequences]])
  (:import [org.micromanager AcqControlDlg]
           [org.micromanager.api AcquisitionEngine TaggedImageAnalyzer]
           [org.micromanager.acquisition AcquisitionWrapperEngine LiveAcqDisplay TaggedImageQueue
                                         ProcessorStack SequenceSettings MMImageCache
                                         TaggedImageStorageRam VirtualAcquisitionDisplay]
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

(def state (atom {:running false :stop false}))

(def settings (atom nil))

(defn state-assoc! [& args]
  (apply swap! state assoc args))
   
(def attached-runnables (atom (vec nil)))

(def pending-devices (atom #{}))

(defn add-to-pending [dev]
  (swap! pending-devices conj dev))

(def active-property-sequences (atom nil))

(def active-slice-sequence (atom nil))

;; time

(defn clock-ms []
  (quot (System/nanoTime) 1000000))

(defn compute-time-from-core [tags]
  (when (@state :burst-init-time)
    (when-let [t (tags "ElapsedTime-ms")]
      (log "ElapsedTime-ms" t)
      (+ (Double/parseDouble t)
         (@state :burst-init-time)))))

(defn elapsed-time [state]
  (if (state :start-time) (- (clock-ms) (state :start-time)) 0))

;; image metadata

(defn generate-metadata [event state]
  (merge
    (map-config (core getSystemStateCache))
    (:metadata event)
    (let [[x y] (let [xy-stage (state :default-xy-stage)]
                  (when-not (empty? xy-stage)
                    (get-in state [:last-positions xy-stage])))]
      {
       "AxisPositions" (when-let [axes (get-in event [:position :axes])]
                         (JSONObject. axes))
       "Binning" (state :binning)
       "BitDepth" (state :bit-depth)
       "Channel" (get-in event [:channel :name])
       "ChannelIndex" (:channel-index event)
       "Exposure-ms" (:exposure event)
       "Frame" (:frame-index event)
       "Height" (state :init-height)
       "NextFrame" (:next-frame-index event)
       "PixelSizeUm" (state :pixel-size-um)
       "PixelType" (state :pixel-type)
       "PositionIndex" (:position-index event)
       "PositionName" (if-let [pos (:position event)] (if-args #(.getLabel %) (get-msp pos)))
       "Slice" (:slice-index event)
       "SlicePosition" (:slice event)
       "Source" (state :source)
       "Time" (get-current-time-str)
       "UUID" (UUID/randomUUID)
       "Width"  (state :init-width)
       "XPositionUm" x
       "YPositionUm" y
       "ZPositionUm" (get-in state [:last-positions (state :default-z-drive)])
      })))
   
(defn annotate-image [img event state]
  {:pix (:pix img)
   :tags 
   (merge
     (generate-metadata event state)
     (:tags img))}) ;; include any existing metadata

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
      (catch Exception e (println "wait for device" dev "failed.")))))

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
      (core setPosition stage pos)))

(defn set-stage-position
  ([stage-dev z]
    (log "set-stage-position" (@state :last-positions) "," stage-dev)
    (when (not= z (get-in @state [:last-positions stage-dev]))
      (device-best-effort stage-dev (set-z-stage-position stage-dev z))
      (swap! state assoc-in [:last-positions stage-dev] z)))
  ([stage-dev x y]
    ;(log (@state :last-positions) "," stage-dev)
    (when (and x y
               (not= [x y] (get-in @state [:last-positions stage-dev])))
      (device-best-effort stage-dev (core setXYPosition stage-dev x y))
      (swap! state assoc-in [:last-positions stage-dev] [x y]))))

(defn set-property
  ([prop] (let [[d p v] prop]
            (device-best-effort d (core setProperty d p v)))))

(defn run-autofocus []
  (.. gui getAutofocusManager getDevice fullFocus)
  (log "running autofocus " (.. gui getAutofocusManager getDevice getDeviceName))
  (state-assoc! :reference-z-position (core getPosition (core getFocusDevice))))

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
    (println slice-sequence relative-z)
    (let [z (core getFocusDevice)
          ref (@state :reference-z-position)
          adjusted-slices (if relative-z
                            (map #(+ ref %) slice-sequence)
                            slice-sequence)
          new-seq (not= [z adjusted-slices] @active-slice-sequence)]
      (println "adjusted-slices: " adjusted-slices ";" "reference-z-position" (@state :reference-z-position))
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
  (println "autoshutter:" (core getAutoShutter))
  (load-property-sequences (:properties trigger-sequence))
  (let [absolute-slices (load-slice-sequence (:slices trigger-sequence) relative-z)]
    (start-property-sequences (:properties trigger-sequence))
    (when absolute-slices
      (start-slice-sequence))
    (swap! state assoc :burst-init-time (elapsed-time @state))
    (core startSequenceAcquisition (if (first-trigger-missing?) (inc length) length) 0 true)
    (swap! state assoc-in [:last-positions (core getFocusDevice)]
           (last absolute-slices))))
  
(defn pop-burst-image []
  (while (and (. mmc isSequenceRunning) (zero? (. mmc getRemainingImageCount)))
    (Thread/sleep 5))
  (let [md (Metadata.)
        pix (core popNextImageMD md)
        tags (parse-core-metadata md)
        t (compute-time-from-core tags)
        tags (if (and t (pos? t))
               (assoc tags "ElapsedTime-ms" t)
               (dissoc tags "ElapsedTime-ms"))]
    (log "compute-time t")
    {:pix pix :tags (dissoc tags "StartTime-ms")}))

(defn collect-burst-images [event out-queue]
  (when (first-trigger-missing?) (pop-burst-image)) ; drop first image if first trigger doesn't happen
  (doseq [event-data (event :burst-data) :while (not (@state :stop))]
    (let [image (pop-burst-image)]
      (.put out-queue (make-TaggedImage (annotate-image image event-data @state))))
   (if (core isBufferOverflowed)
      (do (swap! state assoc :stop true)
          (ReportingUtils/showError "Circular buffer overflowed."))))
  (while (and (not (@state :stop)) (. mmc isSequenceRunning))
    (Thread/sleep 5)))

(defn collect-snap-image [event out-queue]
  (let [image
         {:pix (core getImage)
          :tags {"ElapsedTime-ms" (@state :last-image-time)}}]
    (do (log "collect-snap-image: "
               (select-keys event [:position-index :frame-index
                                   :slice-index :channel-index]))
      (when out-queue
          (.put out-queue
                (make-TaggedImage (annotate-image image event @state)))))
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
    (state-assoc! :sleepy sleepy :next-wake-time (+ (clock-ms) time-ms))
    (.await sleepy time-ms TimeUnit/MILLISECONDS)))

(defn acq-sleep [interval-ms]
  (log "acq-sleep")
  (set-stage-position (core getFocusDevice) (@state :reference-z-position))
  (wait-for-device (core getFocusDevice))
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
      (state-assoc! :last-wake-time wake-time)
      (when-not (core isContinuousFocusEnabled)
        (println "reference z set")
        (state-assoc! :reference-z-position 
                      (get-z-stage-position (core getFocusDevice)))))))

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
                :snap (collect-snap-image event out-queue)
                :burst (collect-burst-images event out-queue)))

(defn compute-z-position [event]
  (if-let [z-drive (@state :default-z-drive)]
    (+ (or (get-in event [:channel :z-offset]) 0)
       (or (:slice event) 0)
       (if (and (:slice event) (not (:relative-z event)))
         0
         (@state :reference-z-position)))))

;; startup and shutdown

(defn prepare-state [this]
  (let [default-z-drive (core getFocusDevice)
        default-xy-stage (core getXYStageDevice)
        z (get-z-stage-position default-z-drive)
        xy (get-xy-stage-position default-xy-stage)]
    (swap! (.state this) assoc
      :pause false
      :stop false
      :running true
      :finished false
      :last-wake-time (clock-ms)
      :last-positions (merge {default-z-drive z}
                             {default-xy-stage xy})       
      :reference-z-position z
      :start-time (clock-ms)
      :init-auto-shutter (core getAutoShutter)
      :init-exposure (core getExposure)
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
  (log "cleanup")
  (do-when #(.update %) (:display @state))
  (state-assoc! :finished true, :running false, :display nil)
  (when (core isSequenceRunning)
    (core stopSequenceAcquisition))
  (stop-trigger)
  (core setAutoShutter (@state :init-auto-shutter))
  (core setExposure (@state :init-exposure))
  (when (not= (get-in @state [:last-positions (core getFocusDevice)]) (@state :init-z-position))
    (set-z-stage-position (core getFocusDevice) (@state :init-z-position)))
  (when (and (@state :init-continuous-focus)
             (not (core isContinuousFocusEnabled)))
    (core enableContinuousFocus true))
  (return-config))

;; iterations

(defn create-presnap-actions [event]
  (concat
    (for [[axis pos] (:axes (MultiStagePosition-to-map (get-msp (:position event)))) :when pos]
      [axis #(apply set-stage-position axis pos)])
    (for [[d p v] (get-in event [:channel :properties])]
      [d #(set-property [d p v])])
    (when-lets [exposure (:exposure event)
                camera (core getCameraDevice)]
      (list [camera #(device-best-effort camera (core setExposure exposure))]))))

(defn run-actions [action-map]
  (doseq [[dev action] action-map]
    (action)))

(defn make-event-fns [event out-queue]
  (list
    #(log event)
    #(doall (map (fn [x] (.run x)) (event :runnables)))
    #(when-let [wait-time-ms (event :wait-time-ms)]
      (acq-sleep wait-time-ms))
    #(run-actions (create-presnap-actions event))
    #(when (:autofocus event)
      (set-msp-z-position (:position event) (@state :default-z-drive) (run-autofocus)))
    #(when-let [z-drive (@state :default-z-drive)]
      (let [z (compute-z-position event)]
        (set-stage-position z-drive z)))
    #(do (wait-for-pending-devices)
         (expose event)
         (collect event out-queue)
         (stop-trigger))))

(defn execute [event-fns]
  (doseq [event-fn event-fns :while (not (:stop @state))]
    (try (event-fn) (catch Throwable e (ReportingUtils/logError e)))
    (await-resume)))

(defn run-acquisition [this settings out-queue]
  (def acq-settings settings)
  (prepare-state this)
  (binding [state (.state this)]
    (def last-state state)
      (let [acq-seq (generate-acq-sequence settings @attached-runnables)]
        (execute (mapcat #(make-event-fns % out-queue) acq-seq))
        (.put out-queue TaggedImageQueue/POISON)
        (cleanup)
        (def acq-sequence acq-seq))))

;; generic metadata

(defn convert-settings [^SequenceSettings settings]
  (println "SequenceSettings slices:" (.slices settings))
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
    )
    (assoc :frames (range (.numFrames settings))
           :channels (vec (filter :use-channel (map ChannelSpec-to-map (.channels settings))))
           :positions (vec (range (.. settings positions size)))
           :slices (vec (.slices settings))
           :default-exposure (core getExposure))))

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
      "BitDepth" (core getImageBitDepth)
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

(def current-album (atom nil))

(def snap-window (atom nil))

(defn compatible-image? [display annotated-image]
  (select-values-match?
    (json-to-data (.. display getImageCache getSummaryMetadata))
    (:tags annotated-image)
    ["Width" "Height" "PixelType"]))  

(defn create-image-window [first-image]
  (let [summary {:interval-ms 0.0, :use-autofocus false, :autofocus-skip 0,
                 :relative-slices true, :keep-shutter-open-slices false, :comment "",
                 :prefix "Untitled", :root "",
                 :time-first false, :positions (), :channels (), :slices-first true,
                 :slices nil, :numFrames 0, :keep-shutter-open-channels false,
                 :zReference 0.0, :frames (), :save false}
		summary-metadata (make-summary-metadata summary)
		cache (doto (MMImageCache. (TaggedImageStorageRam. summary-metadata))
						(.setSummaryMetadata summary-metadata))]
		(doto (VirtualAcquisitionDisplay. cache nil))))

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
        (annotate-image (collect-snap-image event nil) event @state))))
    
(defn show-image [display tagged-img focus]
  (let [myTaggedImage (make-TaggedImage tagged-img)
        cache (.getImageCache display)]
    (.putImage cache myTaggedImage)
    (.showImage display myTaggedImage)
    (when focus
      (.show display))))
    
(defn add-to-album []
  (let [tagged-image (acquire-tagged-image)]
    (when-not (and @current-album
                   (compatible-image? @current-album tagged-image)
	           (not (.windowClosed @current-album)))
      (reset! current-album (create-image-window tagged-image)))
    (let [count (.getNumPositions @current-album)
	  my-tagged-image
	    (update-in tagged-image [:tags] merge
	      {"PositionIndex" count "PositionName" (str "Snap" count)})]
      (show-image @current-album my-tagged-image true))))

(defn reset-snap-window [tagged-image]
  (when-not (and @snap-window
                   (compatible-image? @snap-window tagged-image)
                   (not (.windowClosed @snap-window)))
    (when @snap-window (.close @snap-window))
    (reset! snap-window (create-image-window tagged-image))))

(defn do-snap []
  (let [tagged-image (acquire-tagged-image)]
    (reset-snap-window tagged-image)
    (show-image @snap-window tagged-image true)))

;; live mode

(def live-mode-running (ref false))

(defn enable-live-mode [^Boolean on]
      (if on
        (let [event (create-basic-event)
              state (create-basic-state)
              first-image (atom true)]
          (dosync (ref-set live-mode-running true))
          (core startContinuousSequenceAcquisition 0)
          (log "started sequence acquisition")
          (.start (Thread.
                    #(do (while @live-mode-running
                           (let [raw-image {:pix (core getLastImage) :tags nil}
                                 img (annotate-image raw-image event state)]
                              (reset-snap-window img)
                              (show-image @snap-window img @first-image)
                              (reset! first-image false)))
                         (core stopSequenceAcquisition))
                    "Live mode (clojure)")))
        (dosync (ref-set live-mode-running false))))

;; java interop

(defn -init []
  [[] (atom {:running false :stop false})])

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
        display (LiveAcqDisplay. mmc out-queue-2 (make-summary-metadata settings)
                  (:save settings) acq-eng)]
    (def outq out-queue)
    (when-not (:stop @(.state this))
      (if (. gui getLiveMode)
        (. gui enableLiveMode false))
      (.start acq-thread)
      (swap! (.state this) assoc :display display)
      (.start display))))

(defn -acquireSingle [this]
  (load-mm)
  (add-to-album))

(defn -doSnap [this]
  (load-mm)
  (do-snap))

(defn -isLiveRunning [this]
  @live-mode-running)

(defn -enableLiveMode [this ^Boolean on]
  (load-mm)
  (enable-live-mode on))

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

;; testing

(defn create-acq-eng []
  (doto
    (proxy [AcquisitionWrapperEngine] []
      (runPipeline [^SequenceSettings settings]
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

(defn stop []
  (swap! (.state last-acq) assoc :running false))

