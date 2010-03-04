## Process this file with automake to produce Makefile.in
MMTracker_.jar:  \
	$(srcdir)/Tracker/src/com/imaging100x/tracker/TrackerControl.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/Tracker/src/com/imaging100x/tracker/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../MMTracker_.jar * 

PixelCalibrator_.jar:  \
	$(srcdir)/PixelCalibrator/src/org/micromanager/pixelcalibrator/CalibrationThread.java \
	$(srcdir)/PixelCalibrator/src/org/micromanager/pixelcalibrator/PixelCalibratorDialog.java \
	$(srcdir)/PixelCalibrator/src/org/micromanager/pixelcalibrator/PixelCalibratorPlugin.java \
	$(srcdir)/PixelCalibrator/src/org/micromanager/pixelcalibrator/PixelCalibratorDialog.form 
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/PixelCalibrator/src/org/micromanager/pixelcalibrator/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../PixelCalibrator_.jar * 
all: MMTracker_.jar PixelCalibrator_.jar

install: 
	test -d $(IJPATH)/mmplugins || mkdir $(IJPATH)/mmplugins
	cp MMTracker_.jar $(IJPATH)/mmplugins
	## add when not in beta anymore:
	## cp PixelCalibrator_.jar $(IJPATH)/mmplugins

clean:
	test -f MMTracker_.jar && rm MMTracker_.jar || echo "MMTracker_.jar not found"
	test -f PixelCalibrator_.jar && rm PixelCalibrator_.jar || echo "PixelCalibrator_.jar not found"

EXTRA_DIST = src build.xml
