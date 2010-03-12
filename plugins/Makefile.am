## Process this file with automake to produce Makefile.in
MMTracker_.jar:  \
	$(srcdir)/Tracker/src/com/imaging100x/tracker/TrackerControl.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/Tracker/src/com/imaging100x/tracker/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../MMTracker_.jar * 

PixelCalibrator.jar:  \
	$(srcdir)/PixelCalibrator/src/org/micromanager/pixelcalibrator/CalibrationThread.java \
	$(srcdir)/PixelCalibrator/src/org/micromanager/pixelcalibrator/PixelCalibratorDialog.java \
	$(srcdir)/PixelCalibrator/src/org/micromanager/pixelcalibrator/PixelCalibratorPlugin.java \
	$(srcdir)/PixelCalibrator/src/org/micromanager/pixelcalibrator/PixelCalibratorDialog.form 
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/PixelCalibrator/src/org/micromanager/pixelcalibrator/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../PixelCalibrator.jar * 


Surveyor.jar:  \
	$(srcdir)/Surveyor/src/org/micromanager/surveyor/*.java \
	$(srcdir)/Surveyor/src/org/micromanager/surveyor/*.form
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(srcdir)/PixelCalibrator.jar:$(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/Surveyor/src/org/micromanager/surveyor/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../Surveyor.jar * 

all: MMTracker_.jar PixelCalibrator.jar Surveyor.jar

install: 
	test -d $(IJPATH)/mmplugins || mkdir $(IJPATH)/mmplugins
	cp MMTracker_.jar $(IJPATH)/mmplugins
	cp PixelCalibrator.jar $(IJPATH)/mmplugins
	cp Surveyor.jar $(IJPATH)/mmplugins

clean:
	test -f MMTracker_.jar && rm MMTracker_.jar || echo "MMTracker_.jar not found"
	test -f PixelCalibrator.jar && rm PixelCalibrator.jar || echo "PixelCalibrator.jar not found"
	test -f Surveyor.jar && rm Surveyor.jar || echo "Surveyor.jar not found"

EXTRA_DIST = src build.xml
