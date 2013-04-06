; FILE:         acq_engine.clj
; PROJECT:      Micro-Manager
; SUBSYSTEM:    mmstudio acquisition engine
; ----------------------------------------------------------------------------
; AUTHOR:       Arthur Edelstein, arthuredelstein@gmail.com, 2010-2012
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
           get-property-value edt attempt-all]]
        [org.micromanager.sequence-generator :only [generate-acq-sequence
                                                    make-property-sequences]])
  (:require [clojure.set]
            [org.micromanager.mm :as mm])
  (:import [org.micromanager AcqControlDlg]
           [org.micromanager.api AcquisitionEngine TaggedImageAnalyzer]
           [org.micromanager.acquisition AcquisitionWrapperEngine LiveAcq TaggedImageQueue
                                         ProcessorStack SequenceSettings
                                         MMAcquisition
                                         TaggedImageStorageRam]
           [org.micromanager.utils ReportingUtils]
           [mmcorej TaggedImage Configuration Metadata]
           (java.util.concurrent Executors TimeUnit)
           [java.util.prefs Preferences]
           [java.net InetAddress]
           [java.util.concurrent LinkedBlockingQueue TimeUnit CountDownLatch]
           [org.micromanager.utils MDUtils
                                   ReportingUtils]
           [org.json JSONObject JSONArray]
           [java.util Date UUID]
           [javax.swing SwingUtilities]
           [ij ImagePlus])
   (:gen-class
     :name org.micromanager.AcquisitionEngine2010
     :implements [org.micromanager.api.IAcquisitionEngine2010]
     :init init
     :constructors {[org.micromanager.api.ScriptInterface] []}
     :state state))

;; test utils

(defn random-error [prob]
  (when (< (rand) prob)
    (throw (Exception. "Simulated error"))))

;; globals

(def ^:dynamic state (atom {:stop false}))

(defn state-assoc! [& args]
  (apply swap! state assoc args))

(def attached-runnables (atom (vec nil)))

(def pending-devices (atom #{}))

(defn add-to-pending [dev]
  (swap! pending-devices conj dev))

(def active-property-sequences (atom nil))

(def active-slice-sequence (atom nil))

(def pixel-type-depths {"GRAY8" 1 "GRAY16" 2 "RGB32" 4 "RGB64" 8})

(defn throw-exception [msg]
  (throw (Exception. msg)))

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

;; channels

(defn get-camera-channel-names []
  (vec (map #(core getCameraChannelName %)
       (range (core getNumberOfCameraChannels)))))

(defn super-channel-name [simple-channel-name camera-channel-name num-camera-channels]
  (if (>= 1 num-camera-channels)
    simple-channel-name
    (if (or (empty? simple-channel-name)
            (= "Default" simple-channel-name))
      camera-channel-name
      (str simple-channel-name "-" camera-channel-name))))

;; image metadata

(defn generate-metadata [event state]
  (merge
    (state :system-state)
    (:metadata event)
    (let [[x y] (let [xy-stage (state :default-xy-stage)]
                  (when-not (empty? xy-stage)
                    (get-in state [:last-stage-positions xy-stage])))]
      {
       "AxisPositions" (when-let [axes (get-in event [:position :axes])]
                         (JSONObject. axes))
       "Binning" (state :binning)
       "BitDepth" (state :bit-depth)
       "Camera" (:camera event)
       "CameraChannelIndex" (:camera-channel-index event)
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
       "PositionName" (when-lets [pos (:position event)
                                  msp (get-msp pos)]
                                 (.getLabel msp))
       "Slice" (:slice-index event)
       "SliceIndex" (:slice-index event)
       "SlicePosition" (:slice event)
       "Summary" (state :summary-metadata)
       "Source" (state :source)
       "Time" (get-current-time-str)
       "UUID" (UUID/randomUUID)
       "WaitInterval" (:wait-time-ms event)
       "Width"  (state :init-width)
       "XPositionUm" x
       "YPositionUm" y
       "ZPositionUm" (get-in state [:last-stage-positions (state :default-z-drive)])
      })
    (when-let [runnables (event :runnables)]
      {"AttachedTasks" (JSONArray. (map str runnables))})))

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
  (assert (not (empty? body)))
  `(let [attempt# #(do (wait-for-device ~device)
                       (add-to-pending ~device)
                       ~@body)]
    (when-not
      (or
        (successful? (attempt#)) ; first attempt
        (do (log "second attempt") (successful? (attempt#)))
        (when false
          (do (log "reload and try a third time")
          (successful? (reload-device ~device) (attempt#))))) ; third attempt after reloading
      (throw-exception (str "Device failure: " ~device))
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
  (when (not= exp (get-in @state [camera :exposure]))
    (device-best-effort camera (core setExposure exp))
    (swap! state assoc-in [camera :exposure] exp)))

(defn wait-for-pending-devices []
  (log "pending devices: " @pending-devices)
  (dorun (map wait-for-device @pending-devices)))

(defn get-z-stage-position [stage]
  (if-not (empty? stage) (core getPosition stage) 0))

(defn get-xy-stage-position [stage]
  (if-not (empty? stage)
    (let [xy (.getXYStagePosition gui)]
      [(.x xy) (.y xy)])))

(defn enable-continuous-focus [on?]
  (let [autofocus (core getAutoFocusDevice)]
    (device-best-effort autofocus
                        (core enableContinuousFocus on?))))

(defn set-shutter-open [open?]
  (let [shutter (core getShutterDevice)]
    (device-best-effort shutter
      (when (not= open? (get-in @state [:shutter-states shutter]))
        (core setShutterOpen open?)
        (swap! state assoc-in [:shutter-states shutter] open?)))))

(defn is-continuous-focus-drive [stage]
  (when-not (empty? stage)
    (core isContinuousFocusDrive stage)))

(defn set-z-stage-position [stage pos]
  (when (and (@state :init-continuous-focus)
             (not (is-continuous-focus-drive stage))
             (core isContinuousFocusEnabled))
    (enable-continuous-focus false))
  (device-best-effort stage (core setPosition stage pos)))

(defn set-stage-position
  ([stage-dev z]
    (when (and (not (empty? stage-dev))
               (not= z (get-in @state [:last-stage-positions stage-dev])))
      (set-z-stage-position stage-dev z)
      (swap! state assoc-in [:last-stage-positions stage-dev] z)))
  ([stage-dev x y]
    (when (and x y
               (not= [x y] (get-in @state [:last-stage-positions stage-dev])))
      (device-best-effort stage-dev (core setXYPosition stage-dev x y))
      (swap! state assoc-in [:last-stage-positions stage-dev] [x y]))))

(defn set-property
  [prop]
  (let [[[d p] v] prop]
    (when (not= v (get-in @state [:last-property-settings d p]))
      (device-best-effort d (core setProperty d p v))
      (swap! state assoc-in [:last-property-settings d p] v))))

(defn run-autofocus []
  (let [z-drive (@state :default-z-drive)
        z0 (get-z-stage-position z-drive)]
  (try
    (log "running autofocus " (.. gui getAutofocusManager getDevice getDeviceName))
    (let [z (.. gui getAutofocusManager getDevice fullFocus)]
      (swap! state assoc-in [:last-stage-positions (@state :default-z-drive)] z))
    (catch Exception e
           (ReportingUtils/logError e "Autofocus failed.")
           (set-stage-position z-drive (+ 1.0e-6 z0))))))

(defn snap-image [open-before close-after]
  (with-core-setting [getAutoShutter setAutoShutter false]
    (let [shutter (core getShutterDevice)]
      (wait-for-pending-devices)
      (when open-before
        (set-shutter-open true)
        (wait-for-device shutter))
      (device-best-effort (core getCameraDevice) (core snapImage))
      (swap! state assoc :last-image-time (elapsed-time @state))
      (when close-after
        (set-shutter-open false)
        (wait-for-device shutter)))))

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
  (doseq [[[d p] vals] property-sequences]
    (core startPropertySequence d p)
    (swap! state assoc-in [:last-property-settings d p] (last vals))))

(defn start-slice-sequence [slices]
  (let [z-stage (core getFocusDevice)]
    (core startStageSequence z-stage)
    (swap! state assoc-in [:last-stage-positions z-stage] (last slices))))

(defn first-trigger-missing? []
  (= "1" (get-property-value (core getCameraDevice) "OutputTriggerFirstMissing")))

(defn extra-triggers []
  (if-let [trigger-str (get-property-value (core getCameraDevice) "ExtraTriggers")]
    (Long/parseLong trigger-str)
    0))

(defn offset-cycle [offset coll]
  (when coll
    (let [n (count coll)
          to-drop (mod (+ n offset) n)]
      (->> (cycle coll)
           (drop to-drop)
           (take n)))))

(defn offset-if-extra-trigger [coll]
  (offset-cycle (- (extra-triggers)) coll))

(defn compensate-for-extra-trigger [slices]
  (when slices
    (concat (repeat (extra-triggers) (first slices)) slices)))

(defn apply-to-map-vals [f m]
  (into {}
        (for [[k v] m]
          [k (f v)])))

(defn init-burst [length trigger-sequence relative-z]
  (core setAutoShutter (@state :init-auto-shutter))
  (load-property-sequences
    (apply-to-map-vals offset-if-extra-trigger (:properties trigger-sequence)))
  (let [absolute-slices (load-slice-sequence
                          (compensate-for-extra-trigger (:slices trigger-sequence))
                           relative-z)]
    (start-property-sequences (:properties trigger-sequence))
    (when absolute-slices
      (start-slice-sequence (:slices trigger-sequence)))
    (core startSequenceAcquisition (if (first-trigger-missing?) (inc length) length) 0 true)
    (swap! state assoc-in [:last-stage-positions (core getFocusDevice)]
           (last absolute-slices))))

(defn pop-tagged-image []
  (try (core popNextTaggedImage)
       (catch Exception e nil)))

(defn pop-tagged-image-timeout
  [timeout-ms]
  (let [start-time (System/currentTimeMillis)]
    (loop []
      (if-let [image (pop-tagged-image)]
        image
        (if (< timeout-ms (- (System/currentTimeMillis) start-time))
          (throw-exception (str (- (System/currentTimeMillis) start-time)
                                " Timed out waiting for image\nto arrive from camera."))
          (do (when (core isBufferOverflowed)
                (throw-exception "Circular buffer overflowed."))
              (Thread/sleep 1)
              (recur)))))))

(defn pop-burst-image
  [timeout-ms]
  (let [tagged-image (pop-tagged-image-timeout timeout-ms)]
    {:pix (.pix tagged-image)
     :tags (json-to-data (.tags tagged-image))}))

(defn make-multicamera-channel [raw-channel-index camera-channel num-camera-channels]
  (+ camera-channel (* num-camera-channels (or raw-channel-index 0))))

(defn make-multicamera-events [event]
  (let [num-camera-channels (core getNumberOfCameraChannels)
        camera-channel-names (get-camera-channel-names)]
    (for [camera-channel (range num-camera-channels)]
      (-> event
          (update-in [:channel-index] make-multicamera-channel
                     camera-channel num-camera-channels)
          (update-in [:channel :name]
                     super-channel-name
                     (camera-channel-names camera-channel)
                     num-camera-channels)
          (assoc :camera-channel-index camera-channel)
          (assoc :camera (camera-channel-names camera-channel))))))

(defn assign-z-offsets [burst-events]
 (if-let [slices (second @active-slice-sequence)]
   (map #(assoc %1 :slice %2) burst-events slices)
   burst-events))

(defn burst-cleanup []
 (when (core isBufferOverflowed)
   (throw-exception "Circular buffer overflowed."))
 (while (and (not (@state :stop)) (. mmc isSequenceRunning))
   (Thread/sleep 5)))

(defn assoc-if-nil [m k v]
  (if (nil? (m k))
    (assoc m k v)
    m))

(defn show [x]
  (do (prn x)
      x))

(defn tag-burst-image [image burst-events camera-channel-names camera-index-tag]
  (swap! state assoc-if-nil :burst-time-offset
         (- (elapsed-time @state)
            (core-time-from-tags (image :tags))))
  (let [cam-chan (if-let [cam-chan-str (get-in image [:tags camera-index-tag])]
                   (Long/parseLong cam-chan-str)
                   0)
        image-number (Long/parseLong (get-in image [:tags "ImageNumber"]))
        image-number (if (first-trigger-missing?) (dec image-number) image-number)
        burst-event (nth burst-events image-number)
        camera-channel-name (nth camera-channel-names cam-chan)
        num-camera-channels (count camera-channel-names)
        event (-> burst-event
                  (update-in [:channel-index]
                             make-multicamera-channel
                             cam-chan num-camera-channels)
                  (update-in [:channel :name]
                             super-channel-name
                             camera-channel-name num-camera-channels)
                  (assoc :camera-channel-index cam-chan))
        time-stamp (burst-time (:tags image) @state)]
    ;image))
    (annotate-image image event @state time-stamp)))

(defn produce-burst-images
  "Pops images from circular buffer and sends them to output queue."
  [burst-events camera-channel-names timeout-ms out-queue]
  (let [total (* (count burst-events)
                 (count camera-channel-names))
        camera-index-tag (str (. mmc getCameraDevice) "-CameraChannelIndex")]
    (doseq [_ (range total)]
      (.put out-queue
            (-> (pop-burst-image timeout-ms)
                (tag-burst-image burst-events camera-channel-names camera-index-tag)
                make-TaggedImage
                ))))
  (burst-cleanup))

(defn collect-burst-images [event out-queue]
  (let [pop-timeout-ms (+ 20000 (* 10 (:exposure event)))]
    (when (first-trigger-missing?)
      (pop-burst-image pop-timeout-ms)) ; drop first image if first trigger doesn't happen
    (swap! state assoc :burst-time-offset nil)
    (let [burst-events (vec (assign-z-offsets (event :burst-data)))
          camera-channel-names (get-camera-channel-names)]
      (produce-burst-images burst-events camera-channel-names pop-timeout-ms out-queue))))

(defn collect-snap-image [event out-queue]
  (let [image
        {:pix (core getImage (event :camera-channel-index))
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
    (try (enable-continuous-focus true) (catch Throwable t nil))) ; don't quit if this fails
  (let [target-time (+ (@state :last-wake-time) interval-ms)
        delta (- target-time (jvm-time-ms))]
     (when (and (< 1000 delta)
                (@state :live-mode-on)
                (not (.isLiveModeOn gui)))
      (.enableLiveMode gui true))
    (when (pos? delta)
      (interruptible-sleep delta))
    (await-resume)
    (swap! state assoc :live-mode-on (.isLiveModeOn gui))
    (when (.isLiveModeOn gui)
      (.enableLiveMode gui false))
    (let [now (jvm-time-ms)
          wake-time (if (> now (+ target-time 10)) now target-time)]
      (state-assoc! :last-wake-time wake-time))))

;; higher level

(defn expose [event]
  (let [shutter-states
         (if (core getAutoShutter)
           [true (:close-shutter event)]
           [false false])]
    (swap! state assoc :system-state (map-config (core getSystemStateCache)))
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

(defn z-stage-needs-adjustment [stage-name]
  (not (and (@state :init-continuous-focus)
            (core isContinuousFocusEnabled)
            (not (is-continuous-focus-drive stage-name)))))

(defn update-z-positions [msp-index]
  (when-let [msp (get-msp msp-index)]
    (dotimes [i (.size msp)]
      (let [stage-pos (.get msp i)
            stage-name (.stageName stage-pos)]
        (when (= 1 (.numAxes stage-pos))
          (when (z-stage-needs-adjustment stage-name)
            (set-msp-z-position msp-index stage-name
                                (get-z-stage-position stage-name))))))))

(defn recall-z-reference [current-position]
  (let [z-drive (@state :default-z-drive)]
    (when (z-stage-needs-adjustment z-drive)
      (set-stage-position z-drive
        (or (get-msp-z-position current-position z-drive)
            (@state :reference-z)))
      (wait-for-device z-drive))))

(defn store-z-reference [current-position]
  (let [z-drive (@state :default-z-drive)]
    (when-not (empty? z-drive)
      (when (z-stage-needs-adjustment z-drive)
        (let [z (get-z-stage-position z-drive)]
          (state-assoc! :reference-z z))))))

;; startup and shutdown

(defn prepare-state [state]
  (let [default-z-drive (core getFocusDevice)
        default-xy-stage (core getXYStageDevice)
        z (get-z-stage-position default-z-drive)
        xy (get-xy-stage-position default-xy-stage)
        exposure (core getExposure)]
    (swap! state assoc
           :pause false
           :stop false
           :finished false
           :last-wake-time (jvm-time-ms)
           :last-stage-positions (into {} [[default-z-drive z]
                                           [default-xy-stage xy]])
           :reference-z z
           :start-time (jvm-time-ms)
           :init-auto-shutter (core getAutoShutter)
           :init-exposure exposure
           :init-shutter-state (core getShutterOpen)
           :exposure {(core getCameraDevice) exposure}
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
    (attempt-all
      (log "cleanup")
      (state-assoc! :finished true :display nil)
      (when (core isSequenceRunning)
        (core stopSequenceAcquisition))
      (stop-trigger)
      (return-config)
      (core setAutoShutter (@state :init-auto-shutter))
      (set-exposure (core getCameraDevice) (@state :init-exposure))
      (set-stage-position (@state :default-z-drive) (@state :init-z-position))
      (set-shutter-open (@state :init-shutter-state))
      (when (and (@state :init-continuous-focus)
                 (not (core isContinuousFocusEnabled)))
        (enable-continuous-focus true))
      (. gui enableRoiButtons true))
    (catch Throwable t 
           (ReportingUtils/showError t "Acquisition cleanup failed."))))

;; running events

(defn make-event-fns [event out-queue]
  (let [current-position (:position event)
        z-drive (@state :default-z-drive)
        check-z-ref (and z-drive
                         (or (:autofocus event)
                             (when-let [t (:wait-time-ms event)]
                               (< 1000 t))))]
    (filter identity
      (flatten
        (list
          #(log event)
          (when (:new-position event)
            (for [[axis pos] (:axes (MultiStagePosition-to-map
                                      (get-msp current-position)))
                  :when pos]
              #(apply set-stage-position axis pos)))
          (for [prop (get-in event [:channel :properties])]
            #(set-property prop))
          #(when-lets [exposure (:exposure event)
                       camera (core getCameraDevice)]
             (set-exposure camera exposure))
          #(when check-z-ref
             (recall-z-reference current-position))
          #(when-let [wait-time-ms (:wait-time-ms event)]
             (acq-sleep wait-time-ms))
          #(when (get event :autofocus)
             (wait-for-pending-devices)
             (run-autofocus))
          #(when check-z-ref
             (store-z-reference current-position)
             (update-z-positions current-position))
          #(when z-drive
             (let [z (compute-z-position event)]
               (set-stage-position z-drive z)))
          (for [runnable (event :runnables)]
            #(.run runnable))
          #(do
            (wait-for-pending-devices)
            (expose event)
            (collect event out-queue)
            (stop-trigger)))))))

(defn execute [event-fns]
  (doseq [event-fn event-fns :while (not (:stop @state))]
    (event-fn)
    (await-resume)))

(defn run-acquisition [settings out-queue cleanup?]
    (try
      (def acq-settings settings)
      (log "Starting MD Acquisition: " settings)
      (. gui enableLiveMode false)
      (. gui enableRoiButtons false)
      (prepare-state state)
      (def last-state state) ; for debugging
      (let [acq-seq (generate-acq-sequence settings @attached-runnables)]
        (def acq-sequence acq-seq)
        (execute (mapcat #(make-event-fns % out-queue) acq-seq)))
      (catch Throwable t
             (def acq-error t)
             (ReportingUtils/showError t "Acquisition failed."))
      (finally
        (when cleanup?
          (cleanup))
        (.put out-queue TaggedImageQueue/POISON))))

;; generic metadata

(defn convert-settings [^SequenceSettings settings]
  (def seqSettings settings)
  (into (sorted-map)
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
                   :channels (vec (filter :use-channel
                                          (map ChannelSpec-to-map (.channels settings))))
                   :positions (vec (range (.. settings positions size)))
                   :slices (vec (.slices settings))
                   :default-exposure (core getExposure)
                   :custom-intervals-ms (vec (.customIntervalsMs settings)))
            )))

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
            (get-in channel [:properties ["Core" "Camera"]])
            default-cam)]
    (set-property chan-cam)
    (let [n (long (core getNumberOfComponents))]
      (set-property default-cam)
      (get {1 1 , 4 3} n))))

(defn super-channels [simple-channel camera-channel-names]
  (let [n (count camera-channel-names)]
    (if (< 1 n)
      (map #(update-in simple-channel [:name] super-channel-name % n)
           camera-channel-names)
      simple-channel)))

(defn all-super-channels [simple-channels camera-channel-names]
  (flatten (map #(super-channels % camera-channel-names) simple-channels)))

(defn channel-colors [simple-channels super-channels channel-names]
  (if (= (count simple-channels) (count super-channels))
    (map #(.getRGB (:color %)) super-channels)
    (map #(. MMAcquisition getMultiCamDefaultChannelColor % (channel-names %))
         (range (count super-channels)))))

(defn make-summary-metadata [settings]
  (let [depth (core getBytesPerPixel)
        channels (:channels settings)
        num-camera-channels (core getNumberOfCameraChannels)
        simple-channels (if-not (empty? channels)
                          channels
                          [{:name "Default" :color java.awt.Color/WHITE}])
        super-channels (all-super-channels simple-channels 
                                           (get-camera-channel-names))
        ch-names (vec (map :name super-channels))]
     (JSONObject. {
      "BitDepth" (core getImageBitDepth)
      "Channels" (max 1 (count super-channels))
      "ChNames" (JSONArray. ch-names)
      "ChColors" (JSONArray. (channel-colors simple-channels super-channels ch-names))
      "ChContrastMax" (JSONArray. (repeat (count super-channels) 65536))
      "ChContrastMin" (JSONArray. (repeat (count super-channels) 0))
      "Comment" (:comment settings)
      "ComputerName" (.. InetAddress getLocalHost getHostName)
      "Depth" (core getBytesPerPixel)
      "Directory" (if (:save settings) (settings :root) "")
      "Frames" (max 1 (count (:frames settings)))
      "GridColumn" 0
      "GridRow" 0
      "Height" (core getImageHeight)
      "Interval_ms" (:interval-ms settings)
      "CustomIntervals_ms" (JSONArray. (or (:custom-intervals-ms settings) []))
      "IJType" (get-IJ-type depth)
      "KeepShutterOpenChannels" (:keep-shutter-open-channels settings)
      "KeepShutterOpenSlices" (:keep-shutter-open-slices settings)
      "MicroManagerVersion" (.getVersion gui)
      "MetadataVersion" 10
      "PixelAspect" 1.0
      "PixelSize_um" (core getPixelSizeUm)
      "PixelType" (get-pixel-type)
      "Positions" (max 1 (count (:positions settings)))
      "Prefix" (if (:save settings) (:prefix settings) "")
      "ROI" (JSONArray. (get-camera-roi))
      "Slices" (max 1 (count (:slices settings)))
      "SlicesFirst" (:slices-first settings)
      "Source" "Micro-Manager"
      "TimeFirst" (:time-first settings)
      "UserName" (System/getProperty "user.name")
      "UUID" (UUID/randomUUID)
      "Width" (core getImageWidth)
      "z-step_um" (get-z-step-um (:slices settings))
     })))

;; acquire button

(def current-album-tags (atom nil))

(def albums (atom {}))

(def current-album-name (atom nil))

(defn compatible-to-current-album? [image-tags]
  (select-values-match?
    @current-album-tags
    image-tags
    ["Width" "Height" "PixelType"]))

(defn initialize-display-ranges [window]
  (do (.setChannelDisplayRange window 0 0 256)
      (.setChannelDisplayRange window 1 0 256)
      (.setChannelDisplayRange window 2 0 256)))

(defn create-basic-event []
  {:position-index 0, :position nil,
   :frame-index 0, :slice 0.0, :channel-index 0, :slice-index 0, :frame 0
   :channel {:name (core getCurrentConfig (core getChannelGroup))},
   :exposure (core getExposure), :relative-z true,
   :wait-time-ms 0})

(defn create-basic-state []
  {:init-width (core getImageWidth)
   :init-height (core getImageHeight)
   :summary-metadata (make-summary-metadata nil)
   :pixel-type (get-pixel-type)
   :bit-depth (core getImageBitDepth)
   :binning (core getProperty (core getCameraDevice) "Binning")})

(defn acquire-tagged-images []
  (core snapImage)
  (let [events (make-multicamera-events (create-basic-event))]
    (for [event events]
      (annotate-image (collect-snap-image event nil) event
                      (create-basic-state) nil))))

(defn add-to-album []
  (doseq [img (acquire-tagged-images)]
    (def x img)
    (.addToAlbum gui (make-TaggedImage img))))

(defn run [this settings cleanup?]
  (def last-acq this)
    (reset! (.state this) {:stop false :pause false :finished false})
    (let [out-queue (LinkedBlockingQueue. 10)
          acq-thread (Thread. #(binding [state (.state this)]
                                 (run-acquisition settings out-queue cleanup?))
                              "AcquisitionSequence2010 Thread (Clojure)")]
      (reset! (.state this)
              {:stop false
               :pause false
               :finished false
               :acq-thread acq-thread
               :summary-metadata (make-summary-metadata settings)})
      (def outq out-queue)
      (when-not (:stop @(.state this))
        (.start acq-thread)
        out-queue)))

;; java interop -- implements org.micromanager.api.Pipeline

(defn -init [script-gui]
  [[] (do (load-mm script-gui)
          (atom {:stop false}))])

(defn -run
  ([this acq-settings cleanup?]
    (let [settings (convert-settings acq-settings)]
      (run this settings cleanup?)))
  ([this acq-settings]
    (-run this acq-settings true)))

(defn -getSummaryMetadata [this]
  (:summary-metadata @(.state this)))

(defn -acquireSingle [this]
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

;; testing

(defn create-acq-eng []
  (doto
    (proxy [AcquisitionWrapperEngine] []
      (runPipeline [^SequenceSettings settings]
        (-run settings this)
    (.setCore mmc (.getAutofocusManager gui))
    (.setParentGUI gui)
    (.setPositionList (.getPositionList gui))))))

;(defn test-dialog [eng]
;  (.show (AcqControlDlg. eng (Preferences/userNodeForPackage (.getClass gui)) gui)))

;(defn run-test []
;  (test-dialog (create-acq-eng)))

(defn stop []
  (when-let [acq-thread (:acq-thread (.state last-acq))]
    (.stop acq-thread)))

(defn drain-queue [blocking-queue]
  (future (loop [i 0]
            (let [obj (.take blocking-queue)]
              (when (zero? (mod i 100))
                (println i (core getRemainingImageCount)))
              (when (not= obj blocking-queue)
                (recur (inc i)))))))

(defn null-queue []
  (doto (LinkedBlockingQueue.)
    drain-queue))

