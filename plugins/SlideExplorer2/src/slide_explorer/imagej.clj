(ns slide-explorer.imagej
  (:import
    (ij ImagePlus)))

(defn main-window []
  (ImagePlus. "Slide Explorer II" 