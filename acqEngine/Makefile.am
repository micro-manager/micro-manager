
MMAcqEngine.jar: \
	$(srcdir)/src/org/micromanager/mm.clj \
	$(srcdir)/src/org/micromanager/acq_engine.clj \
	$(srcdir)/src/org/micromanager/sequence_generator.clj
	rm -rf build
	$(MKDIR_P) build
	cp -R src/org build
	$(JAVA) -server -cp ../MMCoreJ_wrap/MMCoreJ.jar:$(CLASSEXT)/clojure.jar:$(IJJARPATH):../mmstudio/MMJ_.jar:$(CLASSEXT)/bsh-2.0b4.jar:./src -Djava.library.path=. -Djava.awt.headless=true -Dclojure.compile.path=build -server clojure.lang.Compile org.micromanager.acq-engine
	$(JAR) cf MMAcqEngine.jar -C build .


jar_DATA = MMAcqEngine.jar


CLEANFILES = MMAcqEngine.jar

clean-local:
	-rm -rf build
