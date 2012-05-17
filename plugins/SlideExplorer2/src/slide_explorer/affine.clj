(ns slide-explorer.affine
  (:import 
    (java.awt Point Shape)
    (java.awt.geom AffineTransform Point2D$Double)))

(defprotocol AffineTransformable
  (transform [object affine] "Apply affine transform to object.")
  (inverse-transform [object affine] "Apply inverse transform to object."))
              
(extend-protocol AffineTransformable
  Point2D$Double
    (transform [object affine] (.transform affine object nil))
    (inverse-transform [object affine] (.inverseTransform affine object nil))
  Point
    (transform [object affine] (transform (Point2D$Double. (.x object) (.y object)) affine))
    (inverse-transform [object affine] (inverse-transform (Point2D$Double. (.x object) (.y object)) affine))
  Shape
    (transform [object affine] (.createTransformedShape affine object))
    (inverse-transform [object affine] (transform object (.createInverse affine))))

(defn point-to-vector [point]
  [(.x point) (.y point)])

(defn set-destination-origin
  "Produces an affine transform which is same as the original except that
   the offsets have been modifed such that source-point maps to the
   origin (0,0) in the destination coordinate space."
  [^AffineTransform affine ^Point2D$Double source-point]
  (let [[x y] (point-to-vector (transform source-point affine))
        new-affine (.clone affine)]
    (doto new-affine
      (.preConcatenate (AffineTransform/getTranslateInstance (- x) (- y))))))
  
  
  