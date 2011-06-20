(ns refs.core
  (use [clojure.xml :as xml]))

(def url-stub "http://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?")

(defn read-paras [file]
  (.split (slurp file) "\n\n"))

(defn get-xml [doi]
  (clojure.xml/parse (str url-stub "term=" doi)))

(defn get-pmid [xml]
  (first
    (for [x (xml-seq xml) :when (= (:tag x) :Id)]
      (first (:content x)))))
    
(defn doi-to-pmid [doi]
  (get-pmid (get-xml doi)))

(def test-doi "10.1074/jbc.M109.031203")

(def doi-re #"http://dx.doi.org/(.*?)\s")

(defn has-pmid [para]
  (.contains para "PMID"))

(defn has-pmcid [para]
  (.contains para "{{PMCID|"))

(defn read-doi [para]
  (second (re-find doi-re para)))
  
(defn append-pmid [para]
  (or
    (when-not (has-pmid para)
      (when-let [doi (read-doi para)]
        (when-let [pmid (doi-to-pmid doi)]
          (str para
               " PMID "
               pmid
               "."))))
    para))

(def pmcid-url-stub
  (str "http://www.ncbi.nlm.nih.gov/sites/pmctopmid?"
       "PMCRALayout.PMCIDS.PMCIDS_Portlet.Db=pubmed&p%24a=PMCRALayout.PMCIDS.PMCIDS_Portlet.Convert"
       "&p%24l=PMCRALayout&p%24st=pmctopmid&PMCRALayout.PMCIDS.PMCIDS_Portlet.Ids="))

(def pmc-re #">PMC(\d*?)</td>")

(defn pmid-to-pmcid [pmid]
  (second (re-find pmc-re (slurp (str pmcid-url-stub pmid)))))

(defn read-pmid [para]
  (second (re-find #"PMID (\d*+)" (str para " "))))

(defn append-pmcid [para]
  (or
    (when-not (has-pmcid para)
      (when-let [pmid (read-pmid para)]
        (when-let [pmcid (pmid-to-pmcid pmid)]
          (str para
            " {{PMCID|"
            pmcid
            "}}"))))
    para)) 

(defn write-paras [paras file]
  (spit file (apply str (interpose "\n\n" paras))))
    
(defn doi-to-pmcid [doi]
  (-> doi doi-to-pmid pmid-to-pmcid))
