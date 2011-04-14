; FILE:         sequence_generator.clj
; PROJECT:      Micro-Manager
; SUBSYSTEM:    mmstudio acquisition engine
; ----------------------------------------------------------------------------
; AUTHOR:       Arthur Edelstein, arthuredelstein@gmail.com, Dec 14, 2010
;               Adapted from the acq eng by Nenad Amodaj and Nico Stuurman
; COPYRIGHT:    University of California, San Francisco, 2006-2011
; LICENSE:      This file is distributed under the BSD license.
;               License text is included with the source distribution.
;               This file is distributed in the hope that it will be useful,
;               but WITHOUT ANY WARRANTY; without even the implied warranty
;               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
;               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
;               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
;               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

(ns org.micromanager.sequence-generator
  (:use [org.micromanager.mm :only [select-values-match?]]))

(defstruct channel :name :exposure :z-offset :use-z-stack :skip-frames)

(defstruct stage-position :stage-device :axes)

(defstruct acq-settings :frames :positions :channels :slices :slices-first
  :time-first :keep-shutter-open-slices :keep-shutter-open-channels
  :use-autofocus :autofocus-skip :relative-slices :exposure :interval-ms)

(defn pairs [x]
  (partition 2 1 (lazy-cat x (list nil))))

(defn pairs-back [x]
  (partition 2 1 (lazy-cat (list nil) x)))

(defn if-assoc [pred m k v]
  (if pred (assoc m k v) m))

(defn make-dimensions [settings]
  (let [{:keys [slices channels frames positions
                slices-first time-first]} settings
        a [[slices :slice :slice-index] [channels :channel :channel-index]]
        a (if slices-first a (reverse a))
        b [[frames :frame :frame-index] [positions :position :position-index]]
        b (if time-first b (reverse b))]
    (concat a b)))
        
(defn nest-loop [events dim-vals dim dim-index-kw]
  (if (and dim-vals (pos? (count dim-vals)))
    (for [i (range (count dim-vals)) event events]
      (assoc event
        dim-index-kw i
        dim (nth dim-vals i)))
    (map #(assoc % dim-index-kw 0) events)))

(defn create-loops [dimensions]
  (reduce #(apply (partial nest-loop %1) %2) [{:task :snap}] dimensions))

(defn make-main-loops [settings]
  (create-loops (make-dimensions settings)))

(defn build-event [settings event]
  (assoc event
    :exposure (if (:channel event)
                (get-in event [:channel :exposure])
                (:default-exposure settings))
    :relative-z (:relative-slices settings)))

(defn process-skip-z-stack [events slices]
  (if (pos? (count slices))
    (let [middle-slice (nth slices (int (/ (count slices) 2)))]
      (filter
        #(or
           (nil? (% :channel))
           (-> % :channel :use-z-stack)
           (= middle-slice (% :slice)))
        events))
    events))

(defn manage-shutter [events keep-shutter-open-channels keep-shutter-open-slices]
  (for [[e1 e2] (pairs events)]
    (assoc e1 :close-shutter
      (if e2 (or
               (and
                 (not keep-shutter-open-channels)
                 (not= (e1 :channel) (e2 :channel)))
               (and
                 (not keep-shutter-open-slices)
                 (not= (e1 :slice) (e2 :slice)))
               (not= (e1 :frame-index) (e2 :frame-index))
               (not= (e1 :position-index) (e2 :position-index)))
        true))))

(defn process-channel-skip-frames [events]
  (filter
    #(or
       (nil? (% :channel))
       (-> % :channel :skip-frames zero?)
       (zero? (mod (% :frame) (-> % :channel :skip-frames inc))))
    events))

(defn process-use-autofocus [events use-autofocus autofocus-skip]
  (for [[e1 e2] (pairs-back events)]
    (assoc e2 :autofocus
      (and use-autofocus
        (or (not e1)
          (and (zero? (mod (e2 :frame-index) (inc autofocus-skip)))
               (or (not= (:position-index e1) (:position-index e2))
                   (not= (:frame-index e1) (:frame-index e2)))))))))

(defn process-wait-time [events interval-ms]
  (cons
    (assoc (first events) :wait-time-ms 0)
    (for [[e1 e2] (pairs events) :when e2]
      (if-assoc (not= (:frame-index e1) (:frame-index e2))
        e2 :wait-time-ms interval-ms))))
        
(defn burst-valid [e1 e2]
  (and
    (let [wait-time (:wait-time-ms e2)]
      (or (nil? wait-time) (>= (:exposure e2) wait-time)))
    (select-values-match? e1 e2 [:exposure :position :slice :channel])
    (not (:autofocus e2))))
        
(defn make-bursts [events]
  (let [e1 (first events)
        ne (next events)
        [run later] (split-with #(burst-valid e1 %) ne)]
    (when e1
      (if (not (empty? run))
        (lazy-cat (list (assoc e1 :task :init-burst
                                  :burst-length (inc (count run))))
                (map #(assoc % :task :collect-burst) run)
                (make-bursts later))
        (lazy-cat (list e1) (make-bursts later))))))
      
(defn add-next-task-tags [events]
  (for [p (pairs events)]
    (assoc (first p) :next-frame-index (get (second p) :frame-index))))

(defn selectively-update-tag [events event-template key update-fn]
  (let [ks (keys event-template)]
    (for [event events]
      (if (select-values-match? event event-template ks)
        (update-in event [key] update-fn)
        event))))

(defn selectively-append-runnable [events event-template runnable]
  (selectively-update-tag events event-template :runnables
                          #(conj (vec %) runnable)))

(defn attach-runnables [events runnable-list]
  (if (pos? (count runnable-list))
    (recur (apply selectively-append-runnable events (first runnable-list))
           (next runnable-list))
    events))

(defn generate-acq-sequence [settings runnables]
  (let [{:keys [slices keep-shutter-open-channels keep-shutter-open-slices
                use-autofocus autofocus-skip interval-ms relative-slices
                runnable-list]} settings]
   (-> (make-main-loops settings)
     (#(map (partial build-event settings) %))
       (process-skip-z-stack slices)
       (manage-shutter keep-shutter-open-channels keep-shutter-open-slices)
       (process-channel-skip-frames)
       (process-use-autofocus use-autofocus autofocus-skip)
       (process-wait-time interval-ms)
       (attach-runnables runnables)
       (make-bursts)
       (add-next-task-tags)   
    )))

;; Alternative burst computation:

(defn index [dim]
  (if-not (empty? dim) (range (count dim)) '(0)))

(defn generate-channels-and-slices [settings]
  (let [{:keys [channels slices slices-first]} settings]
    (if slices-first
      (for [c (index channels)
            s (index slices)]
        {:channel (nth channels c nil)
         :slice (nth slices s nil)
         :channel-index c
         :slice-index s})
      (for [s (index slices)
            c (index channels)]
        {:channel (nth channels nil)
         :slice (nth slices nil)
         :channel-index c
         :slice-index s}))))

(defn generate-positions-and-frames [settings]
  (let [{:keys [positions frames time-first]} settings]
    (if time-first
      (for [p (index positions)
            f (index frames)]
        {:position (nth positions p nil)
         :frame (nth frames f nil)
         :position-index p
         :frame-index f})
      (for [f (index frames)
            p (index positions)]
        {:position (nth positions p nil)
         :frame (nth frames f nil)
         :position-index p
         :frame-index f}))))   

(defn merge-layers [positions-and-frames channels-and-slices]
  (for [pf positions-and-frames cs channels-and-slices]
    (merge pf cs)))

; Testing:

(def my-channels
  [(struct channel "Cy3" 100 0 true 0)
   (struct channel "Cy5"  50 0 false 0)
   (struct channel "DAPI" 50 0 true 0)])

(def default-settings
  (struct-map acq-settings
    :frames (range 10) :positions [{:name "a" :x 1 :y 2} {:name "b" :x 4 :y 5}]
    :channels my-channels :slices (range 5)
    :slices-first true :time-first true
    :keep-shutter-open-slices false :keep-shutter-open-channels true
    :use-autofocus true :autofocus-skip 3 :relative-slices true :exposure 100
    :interval-ms 1000))


(def test-settings
  (struct-map acq-settings
    :frames (range 10) :positions [{:name "a" :x 1 :y 2} {:name "b" :x 4 :y 5}]
    :channels my-channels :slices (range 5)
    :slices-first true :time-first true
    :keep-shutter-open-slices false :keep-shutter-open-channels true
    :use-autofocus true :autofocus-skip 3 :relative-slices true :exposure 100
    :interval-ms 100))

(def null-settings
  (struct-map acq-settings
    :frames (range 96)
    :positions (range 1536)
    :channels [(struct channel "Cy3" 100 0 true 0)
               (struct channel "Cy5"  50 0 true 0)]
    :slices (range 5)
    :slices-first true :time-first false
    :keep-shutter-open-slices false :keep-shutter-open-channels true
    :use-autofocus true :autofocus-skip 3 :relative-slices true :exposure 100
    :interval-ms 100))

;(def result (generate-acq-sequence null-settings))

;(count result)
