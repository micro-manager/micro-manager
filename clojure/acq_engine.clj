(ns acq-engine
  (:import MMStudioPlugin
           [org.micromanager.navigation MultiStagePosition]))
  
(def gui (MMStudioPlugin/getMMStudioMainFrameInstance))
(def mmc (.getMMCore gui))
(def acq (.getAcquisitionEngine gui))

(defn snap-image [event auto-shutter]
  (if (and auto-shutter (. mmc getShutterOpen))
    (. mmc setShutterOpen true))
  (. mmc snapImage)
  (if (and auto-shutter (event :close-shutter))
     (. mmc setShutterOpen false))
  (. mmc getImage))

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
          
(defn clock-ms []
  (quot (System/nanoTime) 1000000))

(defn acq-sleep [event last-wake-time]
  (let [sleep-time (-
                     (+ last-wake-time (event :interval-ms))
                     (clock-ms))]
    (when (pos? sleep-time)
      (Thread/sleep sleep-time)))
  (clock-ms))
  
(defn run-task [event]
  (let [last-wake-time (atom 0)]
    (update-channel event)
    (update-position event)
    (reset! last-wake-time
      (acq-sleep event @last-wake-time))
    (run-autofocus event)
    (update-slice event)
    (snap-image event)))

   