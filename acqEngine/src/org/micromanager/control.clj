(ns org.micromanager.control
  (:require [clojure.data :as data]
            [org.micromanager.mm :as mm]))

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
    {\"Camera\" [10 30 40]}}"
  [{:keys [properties stage-positions camera-exposures
           property-sequences stage-position-sequences
           camera-exposure-sequences] :as state}]
  (set-stage-position-sequences! stage-position-sequences)
  (set-property-sequences! property-sequences)
  (set-camera-exposure-sequences! camera-exposure-sequences)
  (set-properties! properties)
  (set-stage-positions! stage-positions)
  (set-camera-exposures! camera-exposures)
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

(defn sleep-until [target-time-ms]
  "Sleep until a designated time on the system clock."
  (let [interval (- target-time-ms (System/currentTimeMillis))]
    (when (pos? interval)
      (Thread/sleep interval))))

(defn collect-snap [tags out-queue]
  (mm/core snapImage)
  (future (dotimes [i (mm/core getNumberOfCameraChannels)]
            (.put out-queue (mm/core getTaggedImage i)))
          out-queue))

(defn acquire-event [{:keys [state acquire-time expose-fn]} out-queue]
  (swap! hardware-state-atom merge state)
  (sleep-until acquire-time) 
  (expose-fn out-queue))                 

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
   {"Camera" [10 20 30]}})

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
   {"Camera" [10 20 30]}})
