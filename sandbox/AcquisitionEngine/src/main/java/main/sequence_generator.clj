(ns sequence-generator
;(:require )
;(:use )
;(:import )
 [:gen-class]
)

(defstruct channel :name :exposure :z-offset :z-stack :skip-frames)

(defstruct acq-settings :frames :positions :channels :slices :slices-first
  :time-first :keep-shutter-open-slices :keep-shutter-open-channels
  :use-autofocus :autofocus-skip :relative-slices :exposure)

(defn tails [x]
  (loop [xi x tails []]
    (if xi
      (recur (next xi) (conj tails xi))
      tails)))

(defn pairs [x]
  (for [xi (tails x)]
    [(first xi) (second xi)]))

(defn make-cs-loop [settings]
  (if (settings :slices-first)
    (for [channel (settings :channels) slice (settings :slices)]
      {:channel channel :slice slice})
    (for [slice (settings :slices) channel (settings :channels)]
      {:channel channel :slice slice})))

(defn make-fp-loop [cs-events settings]
  (if (settings :time-first)
    (for [position (settings :positions)
          frame (settings :frames)
          event cs-events]
       (assoc event :position position :frame frame))
    (for [frame (settings :frames)
          position (settings :positions)
          event cs-events]
       (assoc event :position position :frame frame))))

(defn make-main-loops [settings]
  (-> (make-cs-loop settings) (make-fp-loop settings)))

(defn manage-shutter [events settings]
  (for [[e1 e2] (pairs events)]
    (assoc e1 :close-shutter
      (if e2 (or
        (and
          (not (settings :keep-shutter-open-channels))
          (not= (e1 :channel) (e2 :channel)))
        (and
          (not (settings :keep-shutter-open-slices))
          (not= (e1 :slice) (e2 :slice)))
        (not= (e1 :frame) (e2 :frame))
        (not= (e1 :position) (e2 :position)))
        true))))

(defn process-skip-z-stack [events settings]
  (let [slices (settings :slices)
        middle-slice (nth slices (int (/ (count slices) 2)))]
    (filter
      #(or
        (-> % :channel :z-stack)
        (= middle-slice (% :slice)))
      events)))

(defn process-channel-skip-frames [events settings]
  (filter
    #(or
       (-> % :channel :skip-frames zero?)
       (not= 0 (mod (% :frame) (-> % :channel :skip-frames inc))))
    events))

(defn compute-use-autofocus [event settings]
  (assoc event :autofocus
    (and (settings :use-autofocus)
      (zero? (mod (event :frame) (inc (settings :autofocus-skip)))))))

(defn compute-wait-time [events settings]
  (cons
    (assoc (first events) :wait-time-ms 0)
    (for [[e1 e2] (pairs events) :when e2]
      (assoc e2 :wait-time-ms
        (if (= (:frame e1) (:frame e2))
          0
          (settings :interval-ms))))))

; Testing:

(def my-channels
  [(struct channel "Cy3" 100 0 true 0)
   (struct channel "Cy5"  50 0 false 0)
   (struct channel "DAPI" 50 0 true 0)])

(def test-settings
  (struct-map acq-settings
    :frames (range 100) :positions [{:name "a" :x 1 :y 2} {:name "b" :x 4 :y 5}]
    :channels my-channels :slices (range 5)
    :interval 5 :slices-first false
    :keep-shutter-open-slices false :keep-shutter-open-channels true
    :use-autofocus true :autofocus-skip 3 :relative-slices true :exposure 100
    :interval-ms 1000))

(def test-output (->  (make-main-loops test-acq)
                      (process-skip-z-stack test-acq)
                      (manage-shutter test-acq)
                      (process-use-autofocus test-acq)))
