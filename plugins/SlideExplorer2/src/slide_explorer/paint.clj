(ns slide-explorer.paint
  (:import (java.awt Graphics Graphics2D RenderingHints)
           (org.micromanager.utils GUIUpdater))
  (:require [slide-explorer.reactive :as reactive]))

(defn enable-anti-aliasing
  "Turn on (off) anti-aliasing for a graphics context."
  ([^Graphics g]
    (enable-anti-aliasing g true))
  ([^Graphics g on]
    (let [graphics2d (cast Graphics2D g)]
      (.setRenderingHint graphics2d
                         RenderingHints/KEY_ANTIALIASING
                         (if on
                           RenderingHints/VALUE_ANTIALIAS_ON
                           RenderingHints/VALUE_ANTIALIAS_OFF)))))

(defn draw-string-center
  "Draw a string centered at position x,y."
  [^Graphics2D graphics ^String text x y]
  (let [context (.getFontRenderContext graphics)
        height (.. graphics
                   getFont (createGlyphVector context text)
                   getVisualBounds
                   getHeight)
        width (.. graphics
                  getFontMetrics
                  (getStringBounds text graphics)
                  getWidth)]
    (.drawString graphics text
                 (float (- x (/ width 2)))
                 (float (+ y (/ height 2))))))


(defn draw-image
  "Draw an image with upper-left corner at position x,y."
  [^Graphics2D g image x y]
  (.drawImage g image x y nil))

(defn repaint-on-change 
  "Adds a watch such that panel is repainted whenever
   the values in reference have changed."
  [panel references]
  (doseq [reference references]
    (reactive/add-watch-simple reference
      (fn [old-state new-state]
        (when-not (identical? old-state new-state)
          ;(println (meta reference))
          ;(Thread/sleep 60)
          (.repaint panel))))))
