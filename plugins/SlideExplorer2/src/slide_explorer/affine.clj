(ns slide-explorer.affine
  (:import 
    (java.awt Point Shape)
    (java.awt.geom AffineTransform Point2D$Double)))

(defn point-to-vector
  "Converts a point to a clojure vector [x y]."
  [point]
  [(.x point) (.y point)])

(defprotocol AffineTransformable
  (transform [this aff] "Apply affine transform to object.")
  (inverse-transform [this aff] "Apply inverse transform to object."))
   
(extend-protocol AffineTransformable
  Point2D$Double
    (transform [object aff] (.transform aff object nil))
    (inverse-transform [object aff] (.inverseTransform aff object nil))
  Point
    (transform [object aff] (transform (Point2D$Double. (.x object) (.y object)) aff))
    (inverse-transform [object aff] (inverse-transform (Point2D$Double. (.x object) (.y object)) aff))
  clojure.lang.PersistentVector
    (transform [object aff]
      (let [[x y] object]
        (point-to-vector (transform (Point2D$Double. x y) aff))))
    (inverse-transform [object aff] (transform object (.createInverse aff)))
  Shape
    (transform [object aff] (.createTransformedShape aff object))
    (inverse-transform [object aff] (transform object (.createInverse aff))))

(defn set-destination-origin
  "Produces an affine transform which is same as the original except that
   the offsets have been modifed such that source-point maps to the
   origin (0,0) in the destination coordinate space."
  [^AffineTransform aff ^Point2D$Double source-point]
  (let [[x y] (point-to-vector (transform source-point aff))
        new-affine (.clone aff)]
    (doto new-affine
      (.preConcatenate (AffineTransform/getTranslateInstance (- x) (- y))))))
  
