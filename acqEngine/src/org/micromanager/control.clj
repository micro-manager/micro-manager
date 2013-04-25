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
            2 (mm/core setPosition stage (first pos) (second pos)))))))
            
(defn set-camera-settings!
  "Sets the specified camera settings. The input argument should be something like
   {\"Camera\" {:exposure 10}}."
  [camera-settings]
  (doseq-parallel [[camera {:keys [exposure]}] camera-settings]
    (mm/core setExposure camera exposure)))

(defn set-hardware-state!
  "Sets the state of the hardware. Example state:
   {:properties
   {[\"Dichroic\" \"Label\"] \"Q505LP\"
    [\"Emission\" \"Label\"] \"Chroma-HQ535\"
    [\"Excitation\" \"Label\"] \"Chroma-HQ480\"}
   :stage-positions
   {\"Z\" 3.0
    \"XY\" [10 20]}
   :camera-settings
   {\"Camera\" {:exposure 10}}}"
  [{:keys [properties stage-positions camera-settings] :as state}]
  (future (set-properties! properties))
  (future (set-stage-positions! stage-positions))
  (future (set-camera-settings! camera-settings))
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
  (let [update-function (fn [_ _ old-state new-state]
                          (update-hardware-state! old-state new-state))]
    (doto (atom {})
      (add-watch update-function update-function))))

;; testing

(def test-state-1
  {:properties
   {["Dichroic" "Label"] "Q505LP"
    ["Emission" "Label"] "Chroma-HQ535"
    ["Excitation" "Label"] "Chroma-HQ480"}
   :stage-positions
   {"Z" 3.0
    "XY" [10 20]}
   :camera-settings
   {"Camera" {:exposure 10}}})

(def test-state-2
  {:properties
   {["Dichroic" "Label"] "Q505LP"; "400DCLP",
    ["Emission" "Label"] "Chroma-HQ620",
    ["Excitation" "Label"] "Chroma-D360"}
   :stage-positions
   {"Z" 5.0
    "XY" [30 10]}
   :camera-settings
   {"Camera" {:exposure 20}}})