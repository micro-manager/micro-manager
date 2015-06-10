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
  (:use
    [org.micromanager.mm :only
     [ChannelSpec-to-map MultiStagePosition-to-map attempt-all core
      data-object-to-map do-when double-vector get-camera-roi
      get-current-time-str get-msp get-msp-z-position get-pixel-type
      get-property get-property-value get-system-config-cached gui json-to-data
      load-mm log map-config mmc rekey set-msp-z-position store-mmcore
      str-vector when-lets with-core-setting]]
    [org.micromanager.sequence-generator :only [generate-acq-sequence]])
  (:require
    [clojure.set]
    [org.micromanager.mm :as mm])
  (:import
    [ij ImagePlus]
    [java.io EOFException] ; abused to indicate canceled burst image collection
    [java.net InetAddress UnknownHostException]
    [java.util Date UUID]
    [java.util.concurrent CountDownLatch LinkedBlockingQueue TimeUnit]
    [mmcorej Configuration Metadata TaggedImage]
    [org.json JSONArray JSONObject]
    [org.micromanager.acquisition MMAcquisition TaggedImageQueue]
    [org.micromanager.api PositionList SequenceSettings]
    [org.micromanager.utils MDUtils ReportingUtils])
  (:gen-class
    :name org.micromanager.AcquisitionEngine2010
    :implements [org.micromanager.api.IAcquisitionEngine2010]
    :init init
    :constructors {[org.micromanager.api.ScriptInterface] [] [mmcorej.CMMCore] []}
    :state state))

;; test utils

(defn random-error [prob]
  (when (< (rand) prob)
    (throw (Exception. "Simulated error"))))

;; globals

(def ^:dynamic state nil)

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
                                  msp (get-msp (state :position-list) pos)]
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
   (merge-with #(or %2 %1) ; only overwrite tags if generated tag is not nil
     (:tags img)
     (generate-metadata event state)
     {"ElapsedTime-ms" elapsed-time-ms}
     )}) ;; include any existing metadata

(defn unwrap-tagged-image
  "Take a TaggedImage (as from core) and return a clojure data object,
   with keys :pix and :tags."
  [^TaggedImage tagged-image]
  {:pix (.pix tagged-image)
   :tags (json-to-data (.tags tagged-image))})

(defn make-TaggedImage
  "Take a clojure map with keys :pix and :tags and generate a TaggedImage."
  [annotated-img]
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
        (do (log "second attempt") (successful? (attempt#))))
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
  (when (not= exp (get-in @state [:cameras camera :exposure]))
    (device-best-effort camera (core setExposure exp))
    (swap! state assoc-in [:cameras camera :exposure] exp)))

(defn wait-for-pending-devices []
  (log "pending devices: " @pending-devices)
  (dorun (map wait-for-device @pending-devices)))

(defn get-z-stage-position [stage]
  (if-not (empty? stage) (core getPosition stage) 0))

(defn get-xy-stage-position [stage]
  (if-not (empty? stage)
    (let [x (double-array 1) y (double-array 1)]
      (core getXYPosition stage x y)
      [(get x 0) (get y 0)])))

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
    (log "running autofocus" (-> @state :autofocus-device .getDeviceName))
    (let [z (-> @state :autofocus-device .fullFocus)]
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
  (when (not= property-sequences @active-property-sequences)
    (doseq [[[d p] s] property-sequences]
      (core loadPropertySequence d p (str-vector s)))
    (reset! active-property-sequences property-sequences)))

(defn load-slice-sequence [slice-sequence relative-z]
  (when slice-sequence
    (let [z (core getFocusDevice)
          ref (@state :reference-z)
          adjusted-slices (vec (if relative-z
                                 (map #(+ ref %) slice-sequence)
                                 slice-sequence))]
      (when (not= [z adjusted-slices] @active-slice-sequence)
        (core loadStageSequence z (double-vector adjusted-slices))
        (reset! active-slice-sequence [z adjusted-slices]))
      adjusted-slices)))

(defn start-property-sequences [property-sequences]
  (doseq [[[d p] vals] property-sequences]
    (core startPropertySequence d p)
    (swap! state assoc-in [:last-property-settings d p] (last vals))))

(defn start-slice-sequence [slices]
  (let [z-stage (@state :default-z-drive)]
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
    (core startSequenceAcquisition
          (if (first-trigger-missing?)
            (inc length)
            length)
          0
          true)))

(defn pop-tagged-image []
  (try (. mmc popNextTaggedImage)
       (catch Exception e nil)))

(defn pop-tagged-image-timeout
  [timeout-ms]
  (log "waiting for burst image with timeout" timeout-ms "ms")
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (when (@state :stop)
        (log "halting image collection due to engine stop")
        (throw (EOFException. "(Aborted)")))
      (if-let [image (pop-tagged-image)]
        image
        (if (< deadline (System/currentTimeMillis))
          (do
            (log "halting image collection due to timeout")
            (throw-exception "Timed out waiting for image to arrive from camera."))
          (do
            (when (. mmc isBufferOverflowed)
              (log "halting image collection due to circular buffer overflow")
              (throw-exception "Circular buffer overflowed."))
            (Thread/sleep 1)
            (recur)))))))

(defn pop-burst-image
  [timeout-ms]
  (unwrap-tagged-image (pop-tagged-image-timeout timeout-ms)))

(defn queuify
  "Runs zero-arg function n times on a new thread. Returns
   a queue that will eventually receive n return values.
   Will block whenever queue reaches queue-size. If function
   throws an exception, this exception will be placed on
   the queue, the thread will stop, and the final call to .take
   on the queue will re-throw the exception (wrapped in RuntimeException)."
  [n queue-size function]
  (let [queue (proxy [LinkedBlockingQueue] [queue-size]
                (take [] (let [item (proxy-super take)]
                           (if (instance? Throwable item)
                             (throw item)
                             item))))]
    (future (try
              (dotimes [_ n]
                (try (.put queue (function))
                  (catch Throwable t
                    (.put queue t)
                    (throw t))))
              (catch Throwable t nil)))
    queue))

(defn pop-burst-images
  [n timeout-ms]
  (queuify n 10 #(pop-burst-image timeout-ms)))

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
  (log "burst-cleanup")
  (core stopSequenceAcquisition)
  (while (and (not (@state :stop)) (. mmc isSequenceRunning))
    (Thread/sleep 5)))

(defn assoc-if-nil [m k v]
  (if (nil? (m k))
    (assoc m k v)
    m))

(defn show [x]
  (do (prn x)
      x))

(defn tag-burst-image [image burst-events camera-channel-names camera-index-tag
                       image-number-offset]
  (swap! state assoc-if-nil :burst-time-offset
         (- (elapsed-time @state)
            (core-time-from-tags (image :tags))))
  (let [cam-chan (if-let [cam-chan-str (get-in image [:tags camera-index-tag])]
                   (Long/parseLong cam-chan-str)
                   0)
        image-number (+ image-number-offset
                        (Long/parseLong (get-in image [:tags "ImageNumber"])))
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
    (annotate-image image event @state time-stamp)))

(defn send-tagged-image
  "Send out image to output queue, but avoid hanging if we stop while blocking
  on the output queue"
  [out-queue tagged-image]
  (loop []
    (when (@state :stop)
      (log "canceling image output due to engine stop")
      (throw (EOFException. "(Aborted)")))
    (when (not (.offer out-queue tagged-image 1000 (TimeUnit/MILLISECONDS)))
      (recur))))

(defn produce-burst-images
  "Pops images from circular buffer, tags them, and sends them to output queue."
  [burst-events camera-channel-names timeout-ms out-queue]
  (let [total (* (count burst-events)
                 (count camera-channel-names))
        camera-index-tag (str (. mmc getCameraDevice) "-CameraChannelIndex")
        image-number-offset (if (first-trigger-missing?) -1 0)
        image-queue (pop-burst-images total timeout-ms)]
    (try
      (doseq [i (range total)]
        (send-tagged-image
          out-queue
          (let [image (try
                        (.take image-queue)
                        (catch RuntimeException e
                          (if (.getCause e) ; unwrap rethrown exception
                            (throw (.getCause e))
                            (throw e))))]
            (-> image
              (tag-burst-image burst-events camera-channel-names camera-index-tag
                               image-number-offset)
              make-TaggedImage))))
      (finally (burst-cleanup)))))

(defn collect-burst-images [event out-queue]
  (let [pop-timeout-ms (+ 20000 (* 10 (:exposure event)))]
    (when (first-trigger-missing?)
      (pop-burst-image pop-timeout-ms)) ; drop first image if first trigger doesn't happen
    (swap! state assoc :burst-time-offset nil)
    (let [burst-events (vec (assign-z-offsets (event :burst-data)))
          camera-channel-names (get-camera-channel-names)]
      (produce-burst-images burst-events camera-channel-names pop-timeout-ms out-queue))))

(defn collect-snap-image [event out-queue]
  (let [image (unwrap-tagged-image (core getTaggedImage (event :camera-channel-index)))]
    (select-keys event [:position-index :frame-index
                        :slice-index :channel-index])
    (when out-queue
      (send-tagged-image out-queue
            (make-TaggedImage (annotate-image image event @state (elapsed-time @state)))))
    image))

(defn return-config []
  (dorun (map set-property
    (clojure.set/difference
      (set (@state :init-system-state))
      (set (get-system-config-cached))))))

(defn stop-triggering []
  (doseq [[[d p] _] @active-property-sequences]
    (core stopPropertySequence d p))
  (when @active-slice-sequence
    (core stopStageSequence (first @active-slice-sequence))))

;; sleeping

(defn await-resume []
  (while (and (:pause @state) (not (:stop @state))) (Thread/sleep 5)))

(defn interruptible-sleep [time-ms]
  (let [sleepy (CountDownLatch. 1)]
    (swap! state assoc :sleepy sleepy :next-wake-time (+ (jvm-time-ms) time-ms))
    (.await sleepy time-ms TimeUnit/MILLISECONDS)))

(defn acq-sleep [interval-ms]
  (log "acq-sleep")
  (when (and (@state :init-continuous-focus)
             (not (core isContinuousFocusEnabled)))
    (try (enable-continuous-focus true) (catch Throwable t nil))) ; don't quit if this fails
  (let [target-time (+ (@state :last-wake-time) interval-ms)
        delta (- target-time (jvm-time-ms))]
     (when (and gui
                (< 1000 delta)
                (@state :live-mode-on)
                (not (.isLiveModeOn gui)))
      (.enableLiveMode gui true))
    (when (pos? delta)
      (interruptible-sleep delta))
    (await-resume)
    (when gui
      (swap! state assoc :live-mode-on (.isLiveModeOn gui))
      (when (.isLiveModeOn gui)
        (.enableLiveMode gui false)))
    (let [now (jvm-time-ms)
          wake-time (if (> now (+ target-time 10)) now target-time)]
      (swap! state assoc :last-wake-time wake-time))))

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
  (log "collecting image(s)")
  (try
    (condp = (:task event)
      :snap (doseq [sub-event (make-multicamera-events event)]
              (collect-snap-image sub-event out-queue))
      :burst (collect-burst-images event out-queue))
    (catch EOFException eat
      (log "halted image collection and output due to engine stop"))))

(defn z-in-msp [msp z-drive]
  (-> msp MultiStagePosition-to-map :axes (get z-drive) first))

(defn compute-z-position [event]
  (let [z-ref (or (-> (get-msp (@state :position-list) (:position event))
                      (z-in-msp (@state :default-z-drive)))
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
  (when-let [msp (get-msp (@state :position-list) msp-index)]
    (dotimes [i (.size msp)]
      (let [stage-pos (.get msp i)
            stage-name (.stageName stage-pos)]
        (when (= 1 (.numAxes stage-pos))
          (when (z-stage-needs-adjustment stage-name)
            (set-msp-z-position (@state :position-list) msp-index stage-name
                                (get-z-stage-position stage-name))))))))

(defn recall-z-reference [current-position]
  (let [z-drive (@state :default-z-drive)]
    (when (z-stage-needs-adjustment z-drive)
      (set-stage-position z-drive
        (or (get-msp-z-position (@state :position-list) current-position z-drive)
            (@state :reference-z)))
      (wait-for-device z-drive))))

(defn store-z-reference [current-position]
  (let [z-drive (@state :default-z-drive)]
    (when-not (empty? z-drive)
      (when (z-stage-needs-adjustment z-drive)
        (let [z (get-z-stage-position z-drive)]
          (swap! state assoc :reference-z z))))))

;; startup and shutdown

(defn prepare-state [state position-list autofocus-device]
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
           :autofocus-device autofocus-device
           :position-list position-list
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
      (swap! state assoc :finished true :display nil)
      (when (core isSequenceRunning)
        (core stopSequenceAcquisition))
      (stop-triggering)
      (reset! active-property-sequences nil)
      (reset! active-slice-sequence nil)
      (return-config)
      (core setAutoShutter (@state :init-auto-shutter))
      (set-exposure (core getCameraDevice) (@state :init-exposure))
      (set-stage-position (@state :default-z-drive) (@state :init-z-position))
      (set-shutter-open (@state :init-shutter-state))
      (when (and (@state :init-continuous-focus)
                 (not (core isContinuousFocusEnabled)))
        (enable-continuous-focus true))
      (when gui (.enableRoiButtons gui true)))
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
            ; The items of the flattened list get executed without stopping or
            ; pausing in between (except when throwing)
            (flatten
              (list
                #(log "#####" "BEGIN acquisition event:" event)
                (when (:new-position event)
                  (for [[axis pos]
                        (:axes (MultiStagePosition-to-map
                                 (get-msp (@state :position-list) current-position)))
                        :when pos]
                    #(do
                       (log "BEGIN set position of stage" axis)
                       (apply set-stage-position axis pos)
                       (log "END set position of stage" axis))))
                #(log "BEGIN channel properties and exposure")
                (for [prop (get-in event [:channel :properties])]
                  #(set-property prop))
                #(when-lets [exposure (:exposure event)
                             camera (core getCameraDevice)]
                            (set-exposure camera exposure))
                #(log "END channel properties and exposure")
                #(when check-z-ref
                   (log "BEGIN recall-z-reference")
                   (recall-z-reference current-position)
                   (log "END recall-z-reference"))
                #(when-let [wait-time-ms (:wait-time-ms event)]
                   (acq-sleep wait-time-ms))
                #(when (get event :autofocus)
                   (wait-for-pending-devices)
                   (run-autofocus))
                #(when check-z-ref
                   (log "BEGIN store/update z reference")
                   (store-z-reference current-position)
                   (update-z-positions current-position)
                   (log "END store/update z reference"))
                #(when z-drive
                   (log "BEGIN set z position")
                   (let [z (compute-z-position event)]
                     (set-stage-position z-drive z))
                   (log "END set z position"))
                (for [runnable (event :runnables)]
                  #(do
                     (log "BEGIN run one runnable")
                     (.run runnable)
                     (log "END run one runnable")))
                #(do
                   (wait-for-pending-devices)
                   (log "BEGIN acquire")
                   (expose event)
                   (collect event out-queue)
                   (stop-triggering)
                   (log "END acquire"))
                #(log "#####" "END acquisition event"))))))

(defn execute [event-fns]
  (doseq [event-fn event-fns :while (not (:stop @state))]
    (event-fn)
    (await-resume)))

(defn run-acquisition [settings out-queue cleanup? position-list autofocus-device]
    (try
      (def acq-settings settings) ; for debugging
      (log "Starting MD Acquisition:" settings)
      (when gui
        (doto gui
          (.enableLiveMode false)
          (.enableRoiButtons false)))
      (prepare-state state (when (:use-position-list settings) position-list) autofocus-device)
      (def last-state state) ; for debugging
      (let [acq-seq (generate-acq-sequence settings @attached-runnables)]
        (def acq-sequence acq-seq) ; for debugging
        (execute (mapcat #(make-event-fns % out-queue) acq-seq)))
      (catch Throwable t
             (def acq-error t) ; for debugging
             ; XXX There ought to be a way to get errors programmatically...
             (future (ReportingUtils/showError t "Acquisition failed.")))
      (finally
        (when cleanup?
          (cleanup))
        (if (:stop @state)
          ; In the case where we canceled the acquisition via stop, it is
          ; possible that the out-queue is full. But we have already given up
          ; on sending images in that case, so it can't do any further harm to
          ; drain the queue.
          (if (.offer out-queue TaggedImageQueue/POISON)
            nil
            (do
              (.clear out-queue)
              (.put out-queue TaggedImageQueue/POISON)))
          (.put out-queue TaggedImageQueue/POISON))
        (log "acquisition thread exiting"))))

;; generic metadata

(defn convert-settings
  ([^SequenceSettings settings]
    (convert-settings settings nil))
  ([^SequenceSettings settings ^PositionList position-list]
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
              :usePositionList         :use-position-list 
              :channelGroup            :channel-group
              )
            (assoc :frames (range (.numFrames settings))
                   :channels (vec (filter :use-channel
                                          (map #(ChannelSpec-to-map (.channelGroup settings) %)
                                               (.channels settings))))
                   :positions (when position-list
                                (vec (range (if (.usePositionList settings)
                                              (.getNumberOfPositions position-list)
                                              0))))
                   :slices (vec (.slices settings))
                   :default-exposure (core getExposure)
                   :custom-intervals-ms (vec (.customIntervalsMs settings)))
            ))))

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

(defn summarize-position-list [position-list]
  (let [positions (seq (.getPositions position-list))]
    (JSONArray.
      (for [msp positions
            :let [label (.getLabel msp)
                  grid-row (.getGridRow msp)
                  grid-col (.getGridColumn msp)
                  device-positions (:axes (MultiStagePosition-to-map msp))]]
        (let [json-positions (JSONObject.
                               (into {}
                                     (for [[device coords] device-positions]
                                       [device (JSONArray. coords)])))]
          (JSONObject. {"Label" label
                        "GridRowIndex" grid-row
                        "GridColumnIndex" grid-col
                        "DeviceCoordinatesUm" json-positions}))))))

(defn make-summary-metadata [settings position-list]
  (let [depth (core getBytesPerPixel)
        channels (:channels settings)
        num-camera-channels (core getNumberOfCameraChannels)
        simple-channels (if-not (empty? channels)
                          channels
                          [{:name "Default" :color java.awt.Color/WHITE}])
        super-channels (all-super-channels simple-channels 
                                           (get-camera-channel-names))
        ch-names (vec (map :name super-channels))
        computer (try (.. InetAddress getLocalHost getHostName) (catch UnknownHostException e ""))]
     (JSONObject. {
      "BitDepth" (core getImageBitDepth)
      "Channels" (max 1 (count super-channels))
      "ChNames" (JSONArray. ch-names)
      "ChColors" (JSONArray. (channel-colors simple-channels super-channels ch-names))
      "ChContrastMax" (JSONArray. (repeat (count super-channels) 65536))
      "ChContrastMin" (JSONArray. (repeat (count super-channels) 0))
      "Comment" (:comment settings)
      "ComputerName" computer
      "Depth" (core getBytesPerPixel)
      "Directory" (if (:save settings) (settings :root) "")
      "Frames" (max 1 (count (:frames settings)))
      "GridColumn" 0
      "GridRow" 0
      "Height" (core getImageHeight)
      "InitialPositionList" (when (:use-position-list settings) (summarize-position-list position-list))
      "Interval_ms" (:interval-ms settings)
      "CustomIntervals_ms" (JSONArray. (or (:custom-intervals-ms settings) []))
      "IJType" (get-IJ-type depth)
      "KeepShutterOpenChannels" (:keep-shutter-open-channels settings)
      "KeepShutterOpenSlices" (:keep-shutter-open-slices settings)
      "MicroManagerVersion" (if gui (.getVersion gui) "N/A")
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

(defn run [this settings cleanup? position-list autofocus-device]
  (def last-acq this)
  (def last-state (.state this)) ; for debugging
    (reset! (.state this) {:stop false :pause false :finished false})
    (let [out-queue (LinkedBlockingQueue. 10) ; Q: Why 10?
          acq-thread (Thread. #(binding [state (.state this)]
                                 (run-acquisition settings out-queue cleanup? position-list autofocus-device))
                              "AcquisitionEngine2010 Thread (Clojure)")]
      (reset! (.state this)
              {:stop false
               :pause false
               :finished false
               :acq-thread acq-thread
               :summary-metadata (make-summary-metadata settings position-list)})
      (def outq out-queue) ; for debugging
      (when-not (:stop @(.state this))
        (.start acq-thread)
        out-queue)))

;; java interop -- implements org.micromanager.api.IAcquisitionEngine2010

(defn -init
  ([one-arg]
    [[] (do (if (isa? (type one-arg) org.micromanager.api.ScriptInterface)
              (load-mm one-arg)
              (store-mmcore one-arg))
            (atom {:stop false}))]))

(defn -run
  ([this acq-settings cleanup? position-list autofocus-device]
    (let [settings (convert-settings acq-settings position-list)]
      (run this settings cleanup? position-list autofocus-device)))
  ([this acq-settings cleanup?]
    (let [position-list (.getPositionList gui)
          settings (convert-settings acq-settings position-list)]
      (run this settings cleanup? position-list (.. gui getAutofocusManager getDevice))))
  ([this acq-settings]
    (-run this acq-settings true)))

(defn -getSummaryMetadata [this]
  (:summary-metadata @(.state this)))

(defn -acquireSingle [this]
  (ReportingUtils/logError "Call to deprecated acquireSingle"))

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
    (log "state:" @state)))

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


