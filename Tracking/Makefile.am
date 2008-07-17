## Process this file with automake to produce Makefile.in
Tracker_.jar:  \
	$(srcdir)/src/MMLiveTrackerPlugin_.java \
	$(srcdir)/src/MMTrackerPlugin_.java  \
	$(srcdir)/src/TrackerControlDlg.java \
	$(srcdir)/src/MMStackReaderPlugin_.java \
	$(srcdir)/src/Tracker.jar
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath ../MMCoreJ_wrap:$(srcdir)/src:. -classpath $(IJJARPATH):$(BSHJARPATH):$(topsrcdir)/bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/src/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../Tracker_.jar * 

all: Tracker_.jar

install: 
	test -d $(IJPATH)/plugins/Micro-Manager || mkdir $(IJPATH)/plugins/Micro-Manager
	cp Tracker_.jar $(IJPATH)/plugins/Micro-Manager

clean:
	rm Tracker_.jar

javadoc:
	test -d $(srcdir)/doc || mkdir $(srcdir)/doc
	rm -rf $(srcdir)/doc/*
	javadoc -public -classpath $(IJJARPATH):$(BSHJARPATH):$(IJPATH)/plugins/Micro-Manager/MMCoreJ.jar:$(IJPATH)/plugins/Micro-Manager/MMJ_.jar:. -d doc $(srcdir)/src/org/micromanager/*.java $(srcdir)/src/org/micromanager/*/*.java

EXTRA_DIST = bin src build.xml license.txt
