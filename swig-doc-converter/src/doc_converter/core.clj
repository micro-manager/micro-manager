(ns doc-converter.core
  (:use [clojure.java.io :only (file)])
  (:import (java.net URL)
           (java.util.regex Pattern))
  (:require [net.cgrand.enlive-html :as enlive]
            [clojure.string :as string]))

(defn disp [val]
  (doto val println))

(defn trim [s]
  (when s
    (when-let [trimmed (string/trim s)]
      (when-not (empty? trimmed)
        trimmed))))

(defn text [node]
  (-> node first enlive/text trim))

(defmacro select-text [nodes selector]
  `(text (enlive/select ~nodes ~selector)))

(defn param-rows [method-node]
  (map :content
       (enlive/select method-node
                      [:div.memdoc :> :dl :> :dd :> :table :> :tr])))

(defn param [param-row]
  (let [reverse-param-row (reverse param-row)]
    {:doc (when-let [node (first reverse-param-row)]
            (-> node enlive/text trim))
     :name (-> reverse-param-row second (select-text [:em]))}))

(defn method-properties [method-node]
  {:doc (select-text method-node [:div.memdoc :> :p])
   :name (-> (select-text method-node [:td.memname])
             (.split "::") last)
   :arg-count (count (enlive/select method-node [:td.paramtype]))
   :returns (-> (select-text method-node [:dl.return :> :dd]))
   :parameters (filter #(and (:doc %) (:name %))
                       (map param (param-rows method-node)))
   })

(defn method-nodes [nodes]
  (enlive/select nodes [:div.memitem]))  
  
(defn scrape-doxygen [url]
  (remove #(-> % :name (.contains "~"))
          (map method-properties (method-nodes (enlive/html-resource url)))))

(def cmmcore-java-file
  "../MMCoreJ_wrap/mmcorej/CMMCore.java")

(defn method-declaration-pattern [{:keys [name arg-count]}]
  (re-pattern (str "\\ \\ public .*?" (Pattern/quote name)
                   "\\("
                   (if (pos? arg-count)
                     (str "\\S+(\\s*[^,\\s]+,){" (dec arg-count) "}[^,]*")
                     "(\\s*)")
                   "\\)")))

(defn doc-text [{:keys [doc parameters returns]}]
  (with-out-str
    (println "  /**")
    (println "   *" doc)
    (when-not (empty? parameters)
      (println "   *")
      (doseq [{:keys [doc name]} parameters]
        (println "   * @param" name doc)))
    (when returns
      (println "   *")
      (println  "   * @return" returns))
    (println "   */")))

(defn add-doc [java-file {:keys [name arg-count] :as method}]
  (println name)
  (string/replace java-file (method-declaration-pattern method)
                  #(str (doc-text method) (first %))))

(defn add-docs [java-file methods]
  (reduce #(add-doc %1 %2) java-file methods))

(def mmcore-doxygen
  (file "doxygen.html"))
;  (URL. "https://valelab.ucsf.edu/~MM/doc/MMCore/html/class_c_m_m_core.html"))

(defn run []
  (spit "CMMCore.java"
        (add-docs (slurp cmmcore-java-file)
                  (scrape-doxygen mmcore-doxygen))))
