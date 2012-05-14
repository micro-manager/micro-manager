(ns slide-explorer.paint
  (:import (java.awt Graphics Graphics2D RenderingHints)))


(defn enable-anti-aliasing
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
  "Draws a string centered at position x,y."
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