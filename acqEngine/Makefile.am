
MMAcqEngine.jar: \
	$(srcdir)/src/org/micromanager/mm.clj \
	$(srcdir)/src/org/micromanager/acq_engine.clj \
	$(srcdir)/src/org/micromanager/sequence_generator.clj
	test -d classes || mkdir classes
	rm -rf classes/*
	cp -R src/org classes/
	java -server -cp ../MMCoreJ_wrap/MMCoreJ.jar:$(CLASSEXT)/clojure.jar:$(IJJARPATH):../mmstudio/MMJ_.jar:$(CLASSEXT)/bsh-2.0b4.jar:./src -Djava.library.path=. -Djava.awt.headless=true -Dclojure.compile.path=classes -server clojure.lang.Compile org.micromanager.acq-engine
	jar cf MMAcqEngine.jar -C classes/ .


jardir = $(pkgdatadir)/jars
jar_DATA = MMAcqEngine.jar


CLEANFILES = MMAcqEngine.jar

clean-local:
	-rm -rf classes
