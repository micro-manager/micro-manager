(ns mm
  (:import MMStudioPlugin
           [org.micromanager.navigation MultiStagePosition]))
 
(def gui (MMStudioPlugin/getMMStudioMainFrameInstance))
(def mmc (.getMMCore gui))
(def acq (.getAcquisitionEngine gui))

(defn get-positions []
  (vec (.getPositions (. gui getPositionList))))