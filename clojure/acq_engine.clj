(ns acq-engine
  (:import MMStudioPlugin))
  
(def gui (MMStudioPlugin/getMMStudioMainFrameInstance))
(def mmc (.getMMCore gui))
(def acq (.getAcquisitionEngine gui))

(defn snap-image []
  (. (doto mmc .snapImage) getImage))

