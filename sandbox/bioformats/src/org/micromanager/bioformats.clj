(ns org.micromanager.bioformats
  (:import [loci.common RandomAccessInputStream]
           [loci.common.services ServiceFactory]
           [loci.formats ImageWriter]
           [loci.formats.services OMEXMLService]
           [loci.formats.tiff TiffParser TiffSaver]
           [ome.xml.model.enums DimensionOrder PixelType]
           [ome.xml.model.primitives PositiveInteger]
           [java.io ByteArrayInputStream])
  (:require [clojure.xml]))

;; xml <--> str

(defn str-to-xml [s]
  (clojure.xml/parse (ByteArrayInputStream. (.getBytes s "UTF-8"))))

(defn xml-to-str [xml]
  (with-out-str (clojure.xml/emit xml)))

;; OMEXMLMetadata functions

(def ome-xml-service (. (ServiceFactory.) (getInstance OMEXMLService)))

(defn new-ome-meta []
  (. ome-xml-service createOMEXMLMetadata))

(defn get-ome-xml-string [m]
  (.getOMEXML ome-xml-service m))
                    
(defn ome-to-xml [m]
  (-> m get-ome-xml-string str-to-xml))

(defn +int [n] (PositiveInteger. n))

(defn populate-meta [m w h nslices nchannels nframes ncomponents]
  (doto m
    .createRoot
    (.setImageID "Image:0" 0)
    (.setPixelsID "Pixels:0" 0)
    (.setPixelsBinDataBigEndian true 0 0)
    (.setPixelsDimensionOrder DimensionOrder/XYZCT 0)
    (.setPixelsSizeX (+int w) 0)
    (.setPixelsSizeY (+int h) 0)
    (.setPixelsSizeZ (+int nslices) 0)
    (.setPixelsSizeC (+int nchannels) 0)
    (.setPixelsSizeT (+int nframes) 0)
    (.setPixelsType PixelType/UINT8 0)
    (.setChannelID "Channel:0:0" 0 0)
    (.setChannelSamplesPerPixel (+int ncomponents) 0 0)))

;; read/write tiff files

(defn write-ome-tiff [filename pixels metadata]
  (doto (ImageWriter.)
    (.setMetadataRetrieve metadata)
    (.setId filename)
    (.saveBytes 0 pixels)
    (.close)))

(defn get-tiff-comment [filename]
  (.getComment (TiffParser. filename)))

(defn read-tiff-xml [filename]
  (-> filename get-tiff-comment parse-xml-str))

(defn overwrite-comment [filename comment]
  (.overwriteComment
    (TiffSaver. filename)
    (RandomAccessInputStream. filename)
    comment))

(defn overwrite-tiff-xml [filename xml]
  (overwrite-comment filename (xml-to-str xml)))

;; testing

;;;; image generation

(defn rand-byte []
  (byte (- (rand-int 256) 128)))

(defn rand-bytes [n]
  (byte-array (repeatedly n rand-byte)))

(defn rand-image [w h nchannels bytes-per-pixel]
  (rand-bytes (* w h nchannels )))

;;;; metadata tests

(def test-meta (populate-meta (new-ome-meta) 512 512 2 2 2 2))

(defn test-write-image []
  (write-ome-tiff
    "blah.tiff"
    (rand-image 512 512 2 2)
    test-meta))

(defn overwrite-metadata [filename metadata]
  (overwrite-comment filename (get-ome-xml-string metadata)))

