(ns sequence-generator
 ;(:require )
 ;(:use )
 ;(:import )
 [:gen-class]
 )

(defstruct channel :name :exposure :z-offset :use-z-stack :skip-frames)

(def my-channels
  [(struct channel "Cy3" 100 0 true 0)
   (struct channel "Cy5"  50 0 false 0)
   (struct channel "DAPI" 50 0 true 0)])

(defstruct acq-settings :frames :positions :channels :slices :slices-first
  :time-first :keep-shutter-open-slices :keep-shutter-open-channels
  :use-autofocus :autofocus-skip :relative-slices :exposure)

(def default-settings
  (struct-map acq-settings
    :frames (range 100) :positions [{:name "a" :x 1 :y 2} {:name "b" :x 4 :y 5}]
    :channels my-channels :slices (range 5)
    :interval 5 :slices-first true :time-first true
    :keep-shutter-open-slices false :keep-shutter-open-channels true
    :use-autofocus true :autofocus-skip 3 :relative-slices true :exposure 100
    :interval-ms 1000))

(def settings default-settings)

(defn pairs [x]
  (partition 2 1 (concat x [nil])))

(defn nest-loop [events dim-vals dim]
  (if dim-vals
    (for [dim-val dim-vals event events]
      (assoc event dim dim-val))
    events))

(defn make-dimensions []
  (let [{:keys [slices channels frames positions
                slices-first time-first]} settings
        a [[slices :slice] [channels :channel]]
        a (if slices-first a (reverse a))
        b [[frames :frame] [positions :position]]
        b (if time-first b (reverse b))]
    (concat a b)))

(defn create-loops [dimensions]
  (reduce #(apply (partial nest-loop %1) %2) [{:task :snap}] dimensions))

(defn make-main-loops []
  (create-loops (make-dimensions)))

(defn manage-shutter [events]
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

(defn process-skip-z-stack [events]
  (let [slices (settings :slices)
        middle-slice (nth slices (int (/ (count slices) 2)))]
    (filter
      #(or
         (nil? (% :channel))
         (-> % :channel :use-z-stack)
         (= middle-slice (% :slice)))
      events)))

(defn process-channel-skip-frames [events]
  (filter
    #(or
       (nil? (% :channel))
       (-> % :channel :skip-frames zero?)
       (not= 0 (mod (% :frame) (-> % :channel :skip-frames inc))))
    events))

(defn process-use-autofocus [events]
  (for [event events]
    (assoc event :autofocus
      (and (settings :use-autofocus)
        (or
          (nil? (event :frame))
          (zero? (mod (event :frame) (inc (settings :autofocus-skip)))))))))

(defn process-wait-time [events]
  (cons
    (assoc (first events) :wait-time-ms 0)
    (for [[e1 e2] (pairs events) :when e2]
      (assoc e2 :wait-time-ms
        (if (= (:frame e1) (:frame e2))
          0
          (settings :interval-ms))))))

; Testing:



(def test-settings
  (struct-map acq-settings
    :frames (range 10) :positions [{:name "a" :x 1 :y 2} {:name "b" :x 4 :y 5}]
    :channels my-channels :slices (range 5)
    :interval 5 :slices-first true :time-first true
    :keep-shutter-open-slices false :keep-shutter-open-channels true
    :use-autofocus true :autofocus-skip 3 :relative-slices true :exposure 100
    :interval-ms 100))

(def null-settings
  (struct-map acq-settings
    :frames (range 100) :positions [{:name "a" :x 1 :y 2} {:name "b" :x 4 :y 5}]
    :channels my-channels :slices (range 5)
    :interval 5 :slices-first true :time-first true
    :keep-shutter-open-slices false :keep-shutter-open-channels true
    :use-autofocus true :autofocus-skip 3 :relative-slices true :exposure 100
    :interval-ms 100))

(defn generate-acq-sequence [seq-settings]
  (binding [settings seq-settings]
    (-> (make-main-loops)
      process-skip-z-stack
      manage-shutter
      process-use-autofocus
      process-wait-time)))


(def result (generate-acq-sequence null-settings))

(count result)