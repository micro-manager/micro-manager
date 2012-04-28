(ns slide-explorer.imagej
  (:import (ij ImagePlus)
           (mmcorej TaggedImage))
  (:use [org.micromanager.mm :only (core load-mm gui)]))

(defn setup [acq-name {:keys [frames positions slices channels]} [width height depth]]
  (.openAcquisition gui acq-name nil frame channel slice true false)
  (.initializeAcquisition gui acq-name width height depth))

(defn get-acquisition-display [acq-name]
  (.. gui (getAcquisition acq-name) getAcquisitionWindow))

(defn get-acquisition-image [acq-name]
  (.. (get-acquisition-display acq-name) getImagePlus getImage))

(defn get-acquisition-image-cache [acq-name]
  (.. gui (getAcquisition acq-name) getImageCache)) ;(. gui getAcquisitionImageCache))

(defn add-image [acq-name tagged-image {:keys [position slice frame channel]}]
  (.addImage gui acq-name tagged-image frame channel slice position true))
