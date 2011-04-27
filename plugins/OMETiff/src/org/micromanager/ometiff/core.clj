(ns org.micromanager.ometiff.core
  (:import [java.io RandomAccessFile]
           [java.nio.channels FileChannel$MapMode]))

(defn create-header
  "Creates a big-endian tiff image file header."
  []
  [77 77 0 42 0 0 0 8])

(defn make-bytes [val n]
  (let [chars
        (reverse
          (map #(bit-and 0xFF %)
           (take n (iterate #(bit-shift-right % 8) val))))]
    (for [char chars]
      (if (> char 0x7F) (- char 0xFF) char))))

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

(def type-length {:byte 1, :ascii 1, :short 2, :long 4, :rational 8})

(defn make-tag
  "Create a big-endian tiff image IFD entry"
  [tag type count value]
  (let [tag-code (tag tag-codes)
        type-code (type type-codes)
        type-length (type type-length)]
    (concat
      (make-bytes tag-code 2)
      (make-bytes type-code 2)
      (make-bytes count 4)
      (make-bytes value type-length)
      (make-bytes 0 (- 4 type-length)))))

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
  (flatten
    (let [ifd-entry-count 11
          pixel-size-cm (* 1e-4 pixel-size-um)
          pixels-per-image (* width height)
          bytes-per-image (* width height (bit-shift-right bits-per-pixel 1))
          pixel-size-offset (+ 2 (* 12 ifd-entry-count) current-offset)
          image-offset (+ pixel-size-offset 8)
          ifd-entries
            (map #(apply make-tag %)
              [[:ImageWidth :long 1 width]
               [:ImageLength :long 1 height]
               [:BitsPerSample :short 1 bits-per-pixel]
               [:Compression :short 1 1] ; No compression
               [:PhotometricInterpretation :short 1 1] ; (Black=0;White=Max)
               [:StripOffsets :long 1 image-offset]
               [:RowsPerStrip :long 1 height]
               [:StripByteCounts :long 1 (* width height)]
               [:XResolution :rational 1 pixel-size-offset]
               [:YResolution :rational 1 pixel-size-offset]
               [:ResolutionUnit :short 1 (if pixel-size-cm 3)]])
              ]
      (concat
        (make-bytes ifd-entry-count 2)
        (list ifd-entries)
        (make-bytes (if final-image? 0 (+ image-offset bytes-per-image)) 4)
        (rational-value pixel-size-cm)))))


;; Memory-mapped files

(defn open-mmap [file size]
  (let [rwChannel (.getChannel (RandomAccessFile. file "rwd"))
        dbb (.map rwChannel FileChannel$MapMode/READ_WRITE 0 size)
        dsb (.asShortBuffer dbb)]
    {:rwChannel rwChannel :byte-buf dbb :short-buf dsb}))
        
(defn write-seq [mmap byte-vals]
  (.put (:byte-buf mmap) (byte-array (map byte byte-vals))))  

(defn write-bytes [mmap bytes]
  (.put (:byte-buf mmap) bytes))

(defn get-pos [mmap]
  (.position (:byte-buf mmap)))

(defn close-mmap [mmap]
  (let [chan (:rwChannel mmap)]
    (doto chan
      (.truncate (get-pos mmap))
      .close)))

; (.position dbb) <-- gets cursor pos
; (.position dbb 10) <-- sets cursor pos
; (.get dbb q) <-- read array

;; testing

;;;; image generation

(defn rand-byte []
  (byte (- (rand-int 256) 128)))

(defn rand-bytes [n]
  (let [array (byte-array n)]
    (doseq [i (range n)]
      (aset-byte array i (rand-byte)))
    array))

(defn rand-byte-image [w h nchannels]
  (rand-bytes (* w h nchannels )))

;; write minimal one-page tiff
   
(defn write-one-page-tiff [pix w h]
  (let [mmap (open-mmap "test1.tif" 1000000000)]
    (write-seq mmap
      (create-header))
    (write-seq mmap
      (create-IFD (get-pos mmap) w h 8 1.0 true)) 
    (write-bytes mmap pix)
    (close-mmap mmap)))
   
   