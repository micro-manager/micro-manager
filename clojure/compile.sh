#! sh
java -cp /Users/arthur/Programs/ImageJ/plugins/*:/Users/arthur/Programs/ImageJ/plugins/Micro-Manager/*:/Users/arthur/Programs/ImageJ/ij.jar:classes:. -Dclojure.compile.path=classes -server clojure.lang.Compile acq-engine

