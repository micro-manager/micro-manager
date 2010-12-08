(ns acq-engine
  (:use [mm :only [mmc gui acq]]))

; engine

(defn snap-image [event auto-shutter]
  (if (and auto-shutter (. mmc getShutterOpen))
    (. mmc setShutterOpen true)
    (. mmc waitForDevice (. mmc getShutterDevice)))
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
  (. waitForDevice (. mmc getFocusDevice))
  ;; more
  )
  
(defn run-task [event last-wake-time]
    (update-channel event)
    (update-position event)
    (let [new-wake-time (acq-sleep event last-wake-time)]
      (run-autofocus event)
      (update-slice event)
      (wait-for-devices event)
      (snap-image event)
      new-wake-time))

   