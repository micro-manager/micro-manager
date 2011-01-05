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

(ns org.micromanager.mm
  (:import MMStudioPlugin
           [org.micromanager.navigation MultiStagePosition]
           [mmcorej Configuration]))

(declare gui)
(declare mmc)

(defn load-mm
	([gui] (def mmc (.getMMCore gui)))
	([]
		(def gui (MMStudioPlugin/getMMStudioMainFrameInstance))
		(load-mm gui)))
    
(defn log [& x]
  (org.micromanager.utils.ReportingUtils/logMessage (apply str x)))

(defmacro log-cmd [expr]
  `(do (log '~expr)
    (let [result# ~expr]
      (if (nil? result#)
        (log "  --> nil")
        (log "  --> " result#))
      result#)))

(defmacro core [& args]
  `(log-cmd (. mmc ~@args)))

(defmacro when-lets [bindings & body]
  (assert (vector? bindings))
  (let [n (count bindings)]
    (assert (zero? (mod n 2)))
    (assert (<= 2 n))
  (if (= 2 n)
    `(when-let ~bindings ~@body)
    (let [[a b] (map vec (split-at 2 bindings))]     
      `(when-let ~a (when-lets ~b ~@body))))))
      
(defmacro with-setting [gst & body]
  (let [[getter setter temp] gst]
    `(let [perm# (~getter)
           temp# ~temp
           different# (not= perm# temp#)]
    (println perm#)
    (when different# (~setter temp#))
    (do ~@body)
    (when different# (~setter perm#)))))
    
(defmacro with-core-setting [gst & body]
  (let [[getter setter temp] gst]
    `(with-setting [#(core ~getter) #(core ~setter %) ~temp] ~@body)))

(defmacro do-when [f & args]
  (let [args_ args]
    `(when (and ~@args_)
      (~f ~@args_))))

(defn get-default-devices []
  {:camera          (core getCameraDevice)
   :shutter         (core getShutterDevice)
   :focus           (core getFocusDevice)
   :xy-stage        (core getXYStageDevice)
   :autofocus       (core getAutoFocusDevice)
   :image-processor (core getImageProcessorDevice)})
   
(defn map-config [^Configuration config]
  (let [n (.size config)
        props (map #(.getSetting config %) (range n))]
    (into {}
      (for [prop props]
        [(str (.getDeviceLabel prop) "-" (.getPropertyName prop))
         (.getPropertyValue prop)]))))

(defn get-config [group config]
  (let [data (core getConfigData group config)
        n (.size data)
        props (map #(.getSetting data %) (range n))]
    (for [prop props]
      [(.getDeviceLabel prop)
       (.getPropertyName prop)
       (.getPropertyValue prop)])))
       
(defn get-positions []
  (vec (.. gui getPositionList getPositions)))
