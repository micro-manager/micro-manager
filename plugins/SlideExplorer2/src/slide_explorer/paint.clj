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
                   getFont
                   (createGlyphVector context text)
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

(def last-frame (atom 0))

(defn print-frame-time []
  (let [last-time @last-frame
        now (System/currentTimeMillis)]
    (reset! last-frame now)
    (println (- now last-time))))

(defn repaint-on-change 
  "Adds a watch such that panel is repainted whenever
   the values in reference have changed."
  [panel references]
  (doseq [reference references]
    (reactive/add-watch-simple reference
      (fn [old-state new-state]
        (when (.getParent panel)
          (when-not (identical? old-state new-state)
            (.repaint panel)))))))

(defn graphics-buffer-image
  "Create an image that can be rapdily rendered to screen."
  [w h]
  (.. java.awt.GraphicsEnvironment
      getLocalGraphicsEnvironment
      getDefaultScreenDevice
      getDefaultConfiguration
      (createCompatibleImage w h java.awt.Transparency/TRANSLUCENT)))

;(def graphics-buffer-image-memo (memoize graphics-buffer-image))

(defn paint-buffered
  "Instead of calling (paint-function graphics), run the
   painting offscreen and then draw the resulting image
   to graphics."
  [^Graphics graphics paint-function]
  (let [clip-rect (.getClipRect graphics)
        x (.x clip-rect)
        y (.y clip-rect)
        w (.width clip-rect)
        h (.height clip-rect)
        buffer-image (graphics-buffer-image w h)
        buffer-graphics (.getGraphics buffer-image)
        ]
    ;(print-frame-time)
    (paint-function buffer-graphics)
    (.drawImage graphics buffer-image x y nil)
    ))