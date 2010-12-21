#! sh
mkdir classes
cp -R src/org classes/
java -cp /Users/arthur/Programs/ImageJ/plugins/*:/Users/arthur/Programs/ImageJ/plugins/Micro-Manager/*:/Users/arthur/Programs/ImageJ/ij.jar:classes:./src -Dclojure.compile.path=classes -server clojure.lang.Compile org.micromanager.acq-engine
jar cvf mm_clojure.jar -C classes/ .

