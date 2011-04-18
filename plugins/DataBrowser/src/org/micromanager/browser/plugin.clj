(ns org.micromanager.browser.plugin
  (:use [org.micromanager.browser.core :only (start-browser)])
  (:gen-class
   :name org.micromanager.browser.Data_Browser
   :implements [org.micromanager.api.MMPlugin]))

(defn -dispose [this] )
(defn -setApp [this app] )
(defn -show [this] (start-browser))


;; Do nothing:
(defn -configurationChanged [this])
(defn -getDescription [this])
(defn -getInfo [this])
(defn -getVersion [this])
(defn -getCopyright [this])