; FILE:         DLLAutoReloader/plugin.clj
; PROJECT:      Micro-Manager DeviceAdapterHelper
; ----------------------------------------------------------------------------
; AUTHOR:       Arthur Edelstein, arthuredelstein@gmail.com, 2012
; COPYRIGHT:    University of California, San Francisco, 2011
; LICENSE:      This file is distributed under the BSD license.
;               License text is included with the source distribution.
;               This file is distributed in the hope that it will be useful,
;               but WITHOUT ANY WARRANTY; without even the implied warranty
;               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
;               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
;               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
;               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.


(ns DLLAutoReloader.plugin
  (:require [DLLAutoReloader.core :as core])
  (:gen-class
    :init init
    :name org.micromanager.DLLAutoReloader
    :implements [org.micromanager.api.MMPlugin]
    :state state))

(defn -init [] [[] (atom nil)])
(defn -dispose [this] (core/handle-exit))
(defn -setApp [this app] (swap! (.state this) assoc :app app))
(defn -show [this] (core/show-plugin (:app @(.state this))))


;; Do nothing:
(defn -configurationChanged [this])
(defn -getDescription [this])
(defn -getInfo [this])
(defn -getVersion [this])
(defn -getCopyright [this])