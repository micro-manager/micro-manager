(ns org.micromanager.browser.plugin
  (:use [org.micromanager.browser.core :only (start-browser)])
  (:gen-class
   :name org.micromanager.browser.Data_Browser
   :implements [org.micromanager.api.MMPlugin]))

(defn -dispose [] )
(defn -setApp [app] )
(defn -show [] (start-browser))

;; Don nothing:
(defn -configurationChanged [])
(defn -getDescription [])
(defn -getInfo [])
(defn -getVersion [])
(defn -getCopyright [])