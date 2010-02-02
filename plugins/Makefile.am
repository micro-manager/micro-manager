## Process this file with automake to produce Makefile.in
MMTracker_.jar:  \
	$(srcdir)/Tracker/src/com/imaging100x/tracker/TrackerControl.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/Tracker/src/com/imaging100x/tracker/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../MMTracker_.jar * 

all: MMTracker_.jar

install: 
	test -d $(IJPATH)/mmplugins || mkdir $(IJPATH)/mmplugins
	cp MMTracker_.jar $(IJPATH)/mmplugins

clean:
	test -f MMTracker_.jar && rm MMTracker_.jar || echo "MMTracker_.jar not found"

EXTRA_DIST = src build.xml
