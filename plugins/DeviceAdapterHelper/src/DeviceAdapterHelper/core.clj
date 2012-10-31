(ns org.micromanager.reloader
  (:use [org.micromanager.mm :only (load-mm mmc core)]))


(load-mm (org.micromanager.MMStudioMainFrame/getInstance))

(defn get-loaded-modules []
  (set (remove empty?
               (map #(core getDeviceLibrary %)
                    (core getLoadedDevices)))))

;; testing

(defonce test-lib (first (get-loaded-modules)))

(defn test-unload []
  (core unloadLibrary test-lib))