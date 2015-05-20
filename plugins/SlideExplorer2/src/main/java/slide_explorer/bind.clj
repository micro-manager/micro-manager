(ns slide-explorer.bind)

(defn addressify
  "Prepares argument to be used in assoc-in, get-in, etc.
   If a keyword, wraps in a vector."
  [address]
  (if (sequential? address)
    address
    [address]))

(defn empty-address?
  "Returns true if argument is nil or an empty sequence."
  [ks]
  (or (nil? ks)
      (and (sequential? ks) (empty? ks))))

(defn get-at
  "If ks is a vector, returns (get-in m ks). If ks is
   a keyword, returns (get m ks). If ks is empty, returns m."
  [m ks]
  (if (empty-address? ks)
    m
    (get-in m (addressify ks))))

(defn assoc-at
  "Set values at key-path in map. Like assoc-in, but can
   handle a single keyword. If ks is empty, returns v."
  [m ks v]
  (if (empty-address? ks)
    v
    (assoc-in m (addressify ks) v)))

(defn update-at
  "Like update-in, but if ks is empty, applies function directly
   to m. A single keyword also can be used."
  [m ks function & args]
  (if (empty-address? ks)
    (apply function m args) 
    (apply update-in m (addressify ks) function args)))

(defn clip-value
  "Restricts a value so that it is between min-value and
   max-value."
  [value min-value max-value]
  (-> value
      (min (or max-value Double/POSITIVE_INFINITY))
      (max (or min-value Double/NEGATIVE_INFINITY))))

(defn very-close-numbers?
  "Are these numbers different by less than 1e-10?"
  [a b]
  (when (and (number? a) (number? b))
    (> 1.0e-10 (Math/abs (- a b)))))

(defn handle-change-at
  "When the value of ref changes at address, run
   function with the new value at that address.
   If address is nil, use the whole value of ref."
  [ref address key function]
  (add-watch ref [address key]
             (fn [_ _ old-map new-map]
               (let [old-val (get-at old-map address)
                     new-val (get-at new-map address)]
                 (when-not (or (= old-val new-val)
                               (very-close-numbers? old-val new-val))
                   (function new-val))))))

(defn handle-change
  "When the value of ref changes, run function with
   the new value of ref as its argument."
  [ref key function]
  (handle-change-at ref nil key function))

(defn follow-function
  "Whenever ref1 changes at address1 to value1, then value2, in ref2
   at address2, is set to (function value1)."
  [[ref1 address1] function [ref2 address2]]
  (handle-change-at ref1 address1 [ref2 address2]
                    #(let [val (function %)]
                       (when-not (= val ::?)
                         (swap! ref2 assoc-at address2 val)))))

(defn follow-linear
  "When ref1 changes at address1 to value1, then value2, in ref2 at
   address2, is set to (+ offset (* factor value1))."
  [[ref1 address1] [factor offset] [ref2 address2 [min2 max2]]]
  (follow-function [ref1 address1]
                   #(clip-value (+ offset (* factor %)) min2 max2)
                   [ref2 address2]))

(defn follow-map
  "When ref1 changes at address1 to value1, then value2, in ref2
   at address2, is set to (m value1 ::?)."
  [[ref1 address1] m [ref2 address2]]
  (follow-function [ref1 address1] #(m % ::?) [ref2 address2]))

(defn follow
  "When ref1 changes at address1 to value1, then value2, in ref2
   at address2 is set to value1."
  [[ref1 address1] [ref2 address2]]
  (follow-function [ref1 address1] identity [ref2 address2]))
  
(defn unfollow
  "End an existing follow relationship."
  [[ref1 address1] [ref2 address2]]
  (remove-watch ref1 [address1 [ref2 address2]]))

(defn bind-function
  "The value of ref1 at address1 and the value of ref2 at address2
   are linked, so that if one changes, then the other changes to
   maintain (== value2 (function value1))."
  [[ref1 address1] function inverse-function [ref2 address2]]
  (follow-function [ref1 address1] function [ref2 address2])
  (follow-function [ref2 address2] inverse-function [ref1 address1]))

(defn bind-linear
  "The value of ref1 at address1 and the value of ref2 at address2
   are linked, so that if one changes, then the other changes to
   maintain (== value2 (+ offset (* factor value1)))."
  [[ref1 address1 [min1 max1]]
   [factor offset]
   [ref2 address2 [min2 max2]]]
  (follow-linear [ref1 address1] [factor offset] [ref2 address2 [min2 max2]])
  (follow-linear [ref2 address2] [(/ factor) (- (/ offset factor))] [ref1 address1 [min1 max1]]))

(defn bind-range
  "The value of ref1 at address1 and the value of ref2 at address2
   are linked, so that if one changes, then the other changes to
   maintain a linear interpolation between the corresponding
   endpoints."
  [[ref1 address1 [min1 max1]]
   [ref2 address2 [min2 max2]]]
  (let [delta1 (- max1 min1)
        delta2 (- max2 min2)
        factor (/ delta2 delta1)
        offset (- min2 (* min1 factor))]
    (bind-linear [ref1 address1 [min1 max1]]
                 [factor offset]
                 [ref2 address2 [min2 max2]])))

(defn bind-map
  "The value of ref1 at address1 and the value of ref2 at address2
   are linked so that if one changes, the other changes
   to maintain (== value2 (m value1))."
  [[ref1 address1] m [ref2 address2]]
  (let [m-inverse (clojure.set/map-invert m)]
    (bind-function [ref1 address1] m m-inverse [ref2 address2])))

(defn bind
  "The value of ref1 at address1 and the value of ref2 at address2
   are linked so that if one changes, then the other changes
   to keep the two values identical."
  [[ref1 address1] [ref2 address2]]
  (follow [ref1 address1] [ref2 address2])
  (follow [ref2 address2] [ref1 address1]))

(defn unbind
  "The binding relationship between address1 in ref1 and
   address2 in ref2 is ended."
  [[ref1 address1] [ref2 address2]]
  (unfollow [ref1 address1] [ref2 address2])
  (unfollow [ref2 address2] [ref1 address1]))
