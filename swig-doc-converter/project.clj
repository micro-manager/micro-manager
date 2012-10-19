(defproject swig-doc-converter "0.0.1"
  :description "a utility for copying C++ doxygen documentation
                to a java file (for generating javadocs)"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [enlive "1.0.1"]]
  :main doc-converter.core)
