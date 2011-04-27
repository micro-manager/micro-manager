(ns org.micromanager.ometiff.core
  (:import [java.io RandomAccessFile]
           [java.nio.channels FileChannel$MapMode]))

(defn create-header
  "Creates a big-endian tiff image file header."
  []
  (byte-array (map byte [77 77 0 42 0 0 0 8])))

(defn make-bytes [val n]
  (reverse
    (map #(bit-and 0xFF %)
         (take n (iterate #(bit-shift-right % 8) val)))))

(def tag-codes
  {
   :ImageWidth 0x100
   :ImageLength 0x101
   :BitsPerSample 0x102
   :Compression 0x103
   :PhotometricInterpretation 0x106
   :StripOffsets 0x111
   :RowsPerStrip 0x116
   :StripByteCounts 0x117
   :XResolution 0x11A
   :YResolution 0x11B
   :ResolutionUnit 0x128
  })

(def type-codes {:byte 1, :ascii 2, :short 3, :long 4, :rational 5})

(defn make-tag
  "Create a big-endian tiff image IFD entry"
  [tag type count value]
  (let [tag-code (tag tag-codes)
        type-code (type type-codes)]
    (concat
      (make-bytes tag-code 2)
      (make-bytes type-code 2)
      (make-bytes count 4)
      (make-bytes value 4))))

(defn rational-value [x]
  (apply concat
    (map #(make-bytes % 4)
      (let [int-x (int x)]
        (if (= x int-x)
          [int-x 1]
          ((juxt numerator denominator)
            (rationalize (float x)))))))) ;; Use float to truncate

(defn create-IFD
  "Create a big-endian tiff image IFD."
  [current-offset width height bits-per-pixel
   pixel-size-um final-image?]
  (let [pixel-size-cm (* 1e-4 pixel-size-um)
        pixel-size-offset (+ 2 (* 12 11) current-offset)
        image-offset (+ pixel-size-offset 8)
        pixels-per-image (* width height)
        bytes-per-image (* width height (bit-shift-right bits-per-pixel 1))]
    (concat
      (make-bytes 1 2)
      (apply concat
        (map #(apply make-tag %)
          [[:ImageWidth :long 1 width]
           [:ImageWidth :long 1 height]
           [:BitsPerSample :short 1 bits-per-pixel]
           [:Compression :short 1 1] ; No compression
           [:PhotometricInterpretation :short 1 1] ; (Black=0;White=Max)
           [:StripOffsets :long 1 image-offset]
           [:RowsPerStrip :long 1 height]
           [:StripByteCounts :long 1 (* width height)]
           [:XResolution :rational 1 pixel-size-offset]
           [:YResolution :rational 1 pixel-size-offset]
           [:ResolutionUnit :short 1 (if pixel-size-cm 3)]]))
       (make-bytes (if final-image? 0 (+ image-offset bytes-per-image)) 4)
       (rational-value pixel-size-cm))))


;; Memory-mapped files

(defn open-mmap [file size]
  (let [rwChannel (.getChannel (RandomAccessFile. file "rwd"))
        dbb (.map rwChannel FileChannel$MapMode/READ_WRITE 0 size)
        dsb (.asShortBuffer dbb)]
    {:rwChannel rwChannel :byte-buf dbb :short-buf dsb}))
        
(defn write-header [mmap]
  (.put (:byte-buf mmap) (create-header)))

;(defn write-image [pix w h bits-per-pixel pixel-size-um final-image?]
  

; (.position dbb) <-- gets cursor pos
; (.position dbb 10) <-- sets cursor pos
; (.put dbb (byte-array (map byte (range 10)))) 
; (def q (byte-array 10))
; (.get dbb q) <-- read array
; (.position dsb (bit-shift-right (.position dbb) 1))


;; testing


;;;; image generation

(defn rand-byte []
  (byte (- (rand-int 256) 128)))

(defn rand-bytes [n]
  (byte-array (repeatedly n rand-byte)))

(defn rand-image [w h nchannels bytes-per-pixel]
  (rand-bytes (* w h nchannels )))

