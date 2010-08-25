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

SlideExplorer.jar:  \
	$(srcdir)/SlideExplorer/src/org/micromanager/slideexplorer/*.java \
	$(srcdir)/SlideExplorer/src/org/micromanager/slideexplorer/*.form
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(srcdir)/PixelCalibrator.jar:$(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/SlideExplorer/src/org/micromanager/slideexplorer/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../SlideExplorer.jar * 

RecallPlugin.jar:  \
	$(srcdir)/Recall/src/org/micromanager/recall/*.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(srcdir)/RecallPlugin.jar:$(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/Recall/src/org/micromanager/recall/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../RecallPlugin.jar * 

StageControl.jar:  \
	$(srcdir)/StageControl/src/org/micromanager/stagecontrol/*.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(srcdir)/StageControl.jar:$(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/StageControl/src/org/micromanager/stagecontrol/*.java -d build
	cp -r $(srcdir)/StageControl/src/org/micromanager/stagecontrol/icons build/org/micromanager/stagecontrol/
	cd build && jar cf ../StageControl.jar * 

all: MMTracker_.jar PixelCalibrator.jar SlideExplorer.jar RecallPlugin.jar StageControl.jar

install: 
	test -d $(IJPATH)/mmplugins || mkdir $(IJPATH)/mmplugins
	cp MMTracker_.jar $(IJPATH)/mmplugins
	cp PixelCalibrator.jar $(IJPATH)/mmplugins
	cp SlideExplorer.jar $(IJPATH)/mmplugins
	cp RecallPlugin.jar $(IJPATH)/mmplugins
	cp StageControl.jar $(IJPATH)/mmplugins
	
clean:
	test -f MMTracker_.jar && rm MMTracker_.jar || echo "MMTracker_.jar not found"
	test -f PixelCalibrator.jar && rm PixelCalibrator.jar || echo "PixelCalibrator.jar not found"
	test -f SlideExplorer.jar && rm SlideExplorer.jar || echo "SlideExplorer.jar not found"
	test -f Projector.jar && rm Projector.jar || echo "Projector.jar not found"
	test -f StageControl.jar && rm StageControl.jar || echo "StageControl.jar not found"
	
EXTRA_DIST = src build.xml
