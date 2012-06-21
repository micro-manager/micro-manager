(ns slide-explorer.paint
  (:import (java.awt Graphics Graphics2D RenderingHints)
           (org.micromanager.utils GUIUpdater))
  (:use [slide-explorer.reactive :only (add-watch-simple)]))

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


(def display-updater (GUIUpdater.))

(defn repaint-on-change
  "Adds a watch such that panel is repainted
   if the value in reference has changed."
  [panel reference]
  (add-watch-simple reference
             (fn [old-state new-state]
               (when-not (= old-state new-state)
                 (.post display-updater #(.repaint panel))))))