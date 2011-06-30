; FILE:         browser/plugin.clj
; PROJECT:      Micro-Manager Data Browser Plugin
; ----------------------------------------------------------------------------
; AUTHOR:       Arthur Edelstein, arthuredelstein@gmail.com, April 19, 2011
; COPYRIGHT:    University of California, San Francisco, 2011
; LICENSE:      This file is distributed under the BSD license.
;               License text is included with the source distribution.
;               This file is distributed in the hope that it will be useful,
;               but WITHOUT ANY WARRANTY; without even the implied warranty
;               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
;               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
;               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
;               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.


(ns org.micromanager.browser.plugin
  (:use [org.micromanager.browser.core :only (show-browser handle-exit)])
  (:gen-class
   :name org.micromanager.browser.Data_Browser
   :implements [org.micromanager.api.MMPlugin]))

(defn -dispose [this] (handle-exit))
(defn -setApp [this app] )
(defn -show [this] (show-browser))


;; Do nothing:
(defn -configurationChanged [this])
(defn -getDescription [this])
(defn -getInfo [this])
(defn -getVersion [this])
(defn -getCopyright [this])