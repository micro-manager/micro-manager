(ns org.micromanager.control
  (:require [clojure.data :as data]
            [org.micromanager.mm :as mm]))

;; controlling the non-acquiring state of the system

(defmacro doseq-parallel
  "Just like doseq, but runs each iteration in a future."
  [vec & args]
  `(doseq ~vec
     (future @~args)))

(defn set-properties!
  "Sets the given properties to specified states. The input argument should be
   a map that looks something like: 
   {[\"Dichroic\" \"Label\"] \"Q505LP\"
    [\"Emission\" \"Label\"] \"Chroma-HQ535\"
    [\"Excitation\" \"Label\"] \"Chroma-HQ480\"}."
  [properties]
  (doseq-parallel [[[d p] v] properties]
    (mm/core setProperty d p v)))

(defn set-property-sequences!
  "Loads the given property sequences."
  [property-sequences]
  (doseq-parallel [[[dev prop] prop-seq] property-sequences]
    (when prop-seq
      (mm/core loadPropertySequence dev prop (mm/str-vector prop-seq)))))

(defn set-stage-positions!
  "Sets the position of all stages specified. The input argument should be a
   map that looks something like: {\"Z\" 3.0 \"XY\" [10 20]}."
  [stage-positions]
  (doseq-parallel [[stage pos] stage-positions]
    (if (number? pos)
      (mm/core setPosition stage pos)
      (when (vector? pos)
        (condp = (count pos)
          1 (mm/core setPosition stage (first pos))
          2 (mm/core setXYPosition stage (first pos) (second pos)))))))
            
(defn set-stage-position-sequences!
  "Sets the sequence for specified stages. Example argument:
   {\"Camera\" [10 30 40]}"
  [stage-position-sequences]
  (doseq-parallel [[stage position-sequence] stage-position-sequences]
    (when stage-position-sequences
      (mm/core loadStageSequence stage (mm/str-vector position-sequence)))))

(defn set-camera-exposures!
  "Sets the specified camera exposure settings. The input argument should be
   something like {\"Camera\" 10}."
  [camera-exposures]
  (doseq-parallel [[camera exposure] camera-exposures]
    (mm/core setExposure camera exposure)))

(defn set-camera-exposure-sequences!
  "Sets the exposure sequence for specified cameras. Example argument:
   {\"Camera\" [10 30 40]}"
  [camera-exposure-sequences]
  (doseq-parallel [[camera exposure-sequence] camera-exposure-sequences]
    (mm/core loadExposureSequence camera (mm/double-vector exposure-sequence))))

(defn set-shutter-states!
  "Sets shutters open or closed."
  [shutter-states]
  (doseq-parallel [[shutter state] shutter-states]
    (mm/core setShutterOpen shutter (condp = state :open true :closed false))))

(defn set-hardware-state!
  "Sets the state of the hardware. Example state:
   {:properties
    {[\"Dichroic\" \"Label\"] \"Q505LP\"
     [\"Emission\" \"Label\"] \"Chroma-HQ535\"
     [\"Excitation\" \"Label\"] \"Chroma-HQ480\"}
    :property-sequences
    {[\"Objective\" \"State\"] [\"10X\" \"20X\"]}
    :stage-positions
    {\"Z\" 3.0
     \"XY\" [10 20]}
    :stage-position-sequences
    {\"Z\" [3.0 6.0 9.0]}
    :camera-exposures
    {\"Camera\" exposure}
    :camera-exposure-sequences
    {\"Camera\" [10 30 40]}
    :shutter-states
    {\"Shutter\" :open}}"
  [{:keys [properties stage-positions camera-exposures
           property-sequences stage-position-sequences
           camera-exposure-sequences shutter-states] :as state}]
  (set-stage-position-sequences! stage-position-sequences)
  (set-property-sequences! property-sequences)
  (set-camera-exposure-sequences! camera-exposure-sequences)
  (set-properties! properties)
  (set-stage-positions! stage-positions)
  (set-camera-exposures! camera-exposures)
  (set-shutter-states! shutter-states)
  nil)

(defn new-values
  "Computes the new values found in new-state but not in old-state." 
  [old-state new-state]
  (first (data/diff new-state old-state)))

(defn update-hardware-state!
  "Update the hardware state from an old-state to a new-state.
   Don't re-apply settings in the old state."
  [old-state new-state]
  (set-hardware-state! (new-values old-state new-state)))

(def hardware-state-atom
  "Current hardware state. Changing values will cause hardware commands to be sent."
  (let [update-function (fn [_ _ old-state new-state]
                          (update-hardware-state! old-state new-state))]
    (doto (atom {})
      (add-watch update-function update-function))))

;; other hardware controls

(defn sleep-until!
  "Sleep until a designated time on the system clock."
  [target-time-ms]
  (let [interval (- target-time-ms (System/currentTimeMillis))]
    (when (pos? interval)
      (Thread/sleep interval))))

(defn collect-snap!
  "Snap a tagged image, and, in another thread, retrieve the image from MM core,
   attach extra metadata, and push the image into the out-queue."
  [event out-queue]
  (mm/core snapImage)
  (future (dotimes [i (mm/core getNumberOfCameraChannels)]
            (.put out-queue (mm/core getTaggedImage i)))
          out-queue)) 

(defn run-event!
  [{:keys [state acquire-time expose-fn]} out-queue]
  (swap! hardware-state-atom merge state)
  (sleep-until acquire-time) 
  (expose-fn out-queue))

(defn create-event
  [{:keys [frame position slice channel] :as plane}
   {:keys [channels positions exposure default-camera
           interval-ms use-autofocus autofocus-skip] :as acq-settings}
   {:keys [last-time] :as last-event}]
  {:state
     {:stage-positions positions
      :camera-exposures {default-camera (or (get-in channels [channel :exposure])
                                            exposure)}
      :properties (get-in channels [channel :properties])}
   :acquire-time (+ interval-ms last-time)
   :close-shutter true
   :use-autofocus (zero? (mod frame (inc autofocus-skip)))})
   

;; testing

(def test-state-1
  {:properties
   {["Dichroic" "Label"] "Q505LP"
    ["Emission" "Label"] "Chroma-HQ535"
    ["Excitation" "Label"] "Chroma-HQ480"}
   :property-sequences
   {["Objective" "State"] ["10X" "20X" "40X"]}
   :stage-positions
   {"Z" 3.0
    "XY" [10 20]}
   :stage-position-sequences
   {"Z" [3.0 6.0 9.0]}
   :camera-exposures
   {"Camera" 10}
   :camera-exposure-sequences
   {"Camera" [10 20 30]}
   :shutter-states
   {"Shutter" :open}})

(def test-state-2
  {:properties
   {["Dichroic" "Label"] "Q505LP"; "400DCLP",
    ["Emission" "Label"] "Chroma-HQ620",
    ["Excitation" "Label"] "Chroma-D360"}
   :property-sequences
   {["Objective" "State"] ["10X" "20X" "40X"]}
   :stage-positions
   {"Z" 5.0
    "XY" [30 10]}
   :stage-position-sequences
   {"Z" [3.0 6.0 9.0]}
   :camera-exposures
   {"Camera" 20}
   :camera-exposure-sequences
   {"Camera" [10 20 30]}
   :shutter-states
   {"Shutter" :closed}})

(def test-acq-settings
  {:default-camera "Camera"
   :autofocus-skip 0,
   :channels
   {"Cy5"
    {;:color #<Color java.awt.Color[r=255,g=0,b=0]>,
     :exposure 10.0,
     :name "Cy5",
     :properties
     {["Dichroic" "Label"] "400DCLP",
      ["Emission" "Label"] "Chroma-HQ700",
      ["Excitation" "Label"] "Chroma-HQ570"},
     :skip-frames 0,
     :use-channel true,
     :use-z-stack true,
     :z-offset 0.0}
    "DAPI" {;:color #<Color java.awt.Color[r=0,g=204,b=51]>,
     :exposure 10.0,
     :properties
     {["Dichroic" "Label"] "400DCLP",
      ["Emission" "Label"] "Chroma-HQ620",
      ["Excitation" "Label"] "Chroma-D360"},
     :skip-frames 0,
     :use-channel true,
     :use-z-stack true,
     :z-offset 0.0}
    "FITC"
    {;:color #<Color java.awt.Color[r=255,g=0,b=0]>,
     :exposure 10.0,
     :name "FITC",
     :properties
     {["Dichroic" "Label"] "Q505LP",
      ["Emission" "Label"] "Chroma-HQ535",
      ["Excitation" "Label"] "Chroma-HQ480"},
     :skip-frames 0,
     :use-channel true,
     :use-z-stack true,
     :z-offset 0.0}
    },
   :comment "",
   :custom-intervals-ms [],
   :default-exposure 10.0,
   :frames '(0 1 2),
   :interval-ms 0.0,
   :keep-shutter-open-channels false,
   :keep-shutter-open-slices false,
   :numFrames 3,
   :prefix nil,
   :relative-slices true,
   :root nil,
   :save false,
   :slices [0.0 1.0 2.0],
   :slices-first false,
   :time-first false,
   :use-autofocus false,
   :zReference 3.0,
   :positions {"Pos1" {"XY" [1023.99 1.005], "Z" [10.0]},
               "Pos0" {"XY" [-0.0 -0.0], "Z" [0.0]}}})

