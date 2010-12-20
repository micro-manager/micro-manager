; FILE:         acq_engine.clj
; PROJECT:      Micro-Manager
; SUBSYSTEM:    mmstudio acquisition engine
; ----------------------------------------------------------------------------
; AUTHOR:       Arthur Edelstein, arthuredelstein@gmail.com, Dec 14, 2010
;               Adapted from the acq eng by Nenad Amodaj and Nico Stuurman
; COPYRIGHT:    University of California, San Francisco, 2006-2010
; LICENSE:      This file is distributed under the BSD license.
;               License text is included with the source distribution.
;               This file is distributed in the hope that it will be useful,
;               but WITHOUT ANY WARRANTY; without even the implied warranty
;               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
;               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
;               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
;               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

(ns mm
  (:import MMStudioPlugin
           [org.micromanager.navigation MultiStagePosition]
           [mmcorej Configuration]))

(declare gui)
(declare mmc)

(defn load-mm []
  (def gui (MMStudioPlugin/getMMStudioMainFrameInstance))
  (def mmc (.getMMCore gui)))

(defn get-default-devices []
  {:camera          (. mmc getCameraDevice)
   :shutter         (. mmc getShutterDevice)
   :focus           (. mmc getFocusDevice)
   :xy-stage        (. mmc getXYStageDevice)
   :autofocus       (. mmc getAutoFocusDevice)
   :image-processor (. mmc getImageProcessorDevice)})
   
(defn map-config [^Configuration config]
  (let [n (.size config)
        props (map #(.getSetting config %) (range n))]
    (into {}
      (for [prop props]
        [(str (.getDeviceLabel prop) "-" (.getPropertyName prop))
         (.getPropertyValue prop)]))))

(defn get-config [group config]
  (let [data (. mmc getConfigData group config)
        n (.size data)
        props (map #(.getSetting data %) (range n))]
    (for [prop props]
      [(.getDeviceLabel prop)
       (.getPropertyName prop)
       (.getPropertyValue prop)])))
       
(defn get-positions []
  (vec (.. gui getPositionList getPositions)))