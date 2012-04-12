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

MultiCamera.jar:  \
	$(srcdir)/MultiCamera/src/org/micromanager/multicamera/*.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(srcdir)/MultiCamera.jar:$(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/MultiCamera/src/org/micromanager/multicamera/*.java -d build
	cd build && jar cf ../MultiCamera.jar * 

SplitView.jar:  \
	$(srcdir)/SplitView/src/org/micromanager/splitview/*.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(srcdir)/SplitView.jar:$(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/SplitView/src/org/micromanager/splitview/*.java -d build
	cd build && jar cf ../SplitView.jar * 

ImageFlipper.jar:  \
	$(srcdir)/ImageFlipper/src/org/micromanager/imageflipper/*.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(srcdir)/ImageFlipper.jar:$(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/ImageFlipper/src/org/micromanager/imageflipper/*.java -d build
	cd build && jar cf ../ImageFlipper.jar * 

Big.jar:  \
	$(srcdir)/Big/src/edu/umassmed/big/*.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(srcdir)/Big.jar:$(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/Big/src/edu/umassmed/big/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../Big.jar * 

CRISP.jar:  \
	$(srcdir)/CRISP/src/org/micromanager/CRISP/*.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):$(JFREECHARTJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/CRISP/src/org/micromanager/CRISP/*.java -d build
	cd build && jar cf ../CRISP.jar * 

Gaussian.jar:  \
	$(srcdir)/Gaussian/source/GaussianTrack_.java \
	$(srcdir)/Gaussian/source/edu/valelab/GaussianFit/DataCollectionForm.java \
	$(srcdir)/Gaussian/source/edu/valelab/GaussianFit/FindLocalMaxima.java \
	$(srcdir)/Gaussian/source/edu/valelab/GaussianFit/FitAllThread.java \
	$(srcdir)/Gaussian/source/edu/valelab/GaussianFit/GaussCanvas.java \
	$(srcdir)/Gaussian/source/edu/valelab/GaussianFit/GaussianFit.java \
	$(srcdir)/Gaussian/source/edu/valelab/GaussianFit/GaussianFitStackThread.java \
	$(srcdir)/Gaussian/source/edu/valelab/GaussianFit/GaussianInfo.java \
	$(srcdir)/Gaussian/source/edu/valelab/GaussianFit/GaussianSpotData.java \
	$(srcdir)/Gaussian/source/edu/valelab/GaussianFit/GaussianTrackThread.java \
	$(srcdir)/Gaussian/source/edu/valelab/GaussianFit/GaussianUtils.java \
	$(srcdir)/Gaussian/source/edu/valelab/GaussianFit/MainForm.java \
	$(srcdir)/Gaussian/source/edu/valelab/GaussianFit/MultiVariateGaussianFunction.java \
	$(srcdir)/Gaussian/source/edu/valelab/GaussianFit/NearestPoint2D.java \
	$(srcdir)/Gaussian/source/edu/valelab/GaussianFit/ParametricGaussianFunction.java \
	$(srcdir)/Gaussian/source/edu/valelab/GaussianFit/ProgressThread.java \
	$(srcdir)/Gaussian/source/edu/valelab/GaussianFit/Terms.java \
	$(srcdir)/Gaussian/source/ags/utils/KdTree.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):$(JFREECHARTJARPATH):../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:$(CLASSEXT)/TSFProto.jar:$(CLASSEXT)/gproto.jar:$(CLASSEXT)/lwm.jar:. $(srcdir)/Gaussian/source/GaussianTrack_.java $(srcdir)/Gaussian/source/edu/valelab/GaussianFit/*.java $(srcdir)/Gaussian/source/ags/utils/*.java -d build
	cp $(srcdir)/Gaussian/source/edu/valelab/GaussianFit/*.txt build/edu/valelab/GaussianFit/
	cd build && jar cf ../Gaussian.jar * 

DataBrowser.jar: \
	$(srcdir)/DataBrowser/src/org/micromanager/browser/*.clj
	test -d DataBrowser/classes || mkdir DataBrowser/classes
	rm -rf DataBrowser/classes/*
	cp -R DataBrowser/src/org DataBrowser/classes/
	java -cp DataBrowser/classes/:$(CLASSEXT)/clojure.jar:$(CLASSEXT)/clojure-contrib.jar:$(CLASSEXT)/data.json.jar:$(IJJARPATH):../MMCoreJ_wrap/MMCoreJ.jar:../mmstudio/MMJ_.jar:$(CLASSEXT)/bsh-2.0b4.jar:../acqEngine/MMAcqEngine.jar:DataBrowser/src -Djava.awt.headless=true -Dclojure.compile.path=DataBrowser/classes -server clojure.lang.Compile org.micromanager.browser.plugin
	jar cvf DataBrowser.jar -C DataBrowser/classes/ .


ClojureEditor.jar:  \
	$(srcdir)/ClojureEditor/src/org/micromanager/clojureeditor/*.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(srcdir)/ClojureEditor.jar:$(CLASSEXT)/*:../bin/MMCoreJ.jar:../mmstudio/MMJ_.jar:. $(srcdir)/ClojureEditor/src/org/micromanager/clojureeditor/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../ClojureEditor.jar * 

all: PixelCalibrator.jar RecallPlugin.jar StageControl.jar MultiCamera.jar CRISP.jar Big.jar ImageFlipper.jar SlideExplorer.jar DataBrowser.jar ClojureEditor.jar SplitView.jar Gaussian.jar

install: 
	test -d $(DESTDIR)$(IJPATH)/mmplugins || mkdir $(DESTDIR)$(IJPATH)/mmplugins
	## cp MMTracker_.jar $(DESTDIR)$(IJPATH)/mmplugins
	cp PixelCalibrator.jar $(DESTDIR)$(IJPATH)/mmplugins
	cp SlideExplorer.jar $(DESTDIR)$(IJPATH)/mmplugins
	cp RecallPlugin.jar $(DESTDIR)$(IJPATH)/mmplugins
	cp StageControl.jar $(DESTDIR)$(IJPATH)/mmplugins
	cp MultiCamera.jar $(DESTDIR)$(IJPATH)/mmplugins
	cp CRISP.jar $(DESTDIR)$(IJPATH)/mmplugins
	cp Big.jar $(DESTDIR)$(IJPATH)/mmplugins
	cp DataBrowser.jar $(DESTDIR)$(IJPATH)/mmplugins
	cp ClojureEditor.jar $(DESTDIR)$(IJPATH)/mmplugins
	cp SplitView.jar $(DESTDIR)$(IJPATH)/mmplugins
	cp ImageFlipper.jar $(DESTDIR)$(IJPATH)/mmplugins
	cp Gaussian.jar $(DESTDIR)$(IJPATH)/mmplugins
	
clean:
	test -f MMTracker_.jar && rm MMTracker_.jar || echo "MMTracker_.jar not found"
	test -f PixelCalibrator.jar && rm PixelCalibrator.jar || echo "PixelCalibrator.jar not found"
	test -f SlideExplorer.jar && rm SlideExplorer.jar || echo "SlideExplorer.jar not found"
	test -f Projector.jar && rm Projector.jar || echo "Projector.jar not found"
	test -f StageControl.jar && rm StageControl.jar || echo "StageControl.jar not found"
	test -f MultiCamera.jar && rm MultiCamera.jar || echo "MultiCamera.jar not found"
	test -f CRISP.jar && rm CRISP.jar || echo "CRISP.jar not found"
	test -f RecallPlugin.jar && rm RecallPlugin.jar || echo "RecallPlugin.jar not found"
	test -f Big.jar && rm Big.jar || echo "Big.jar not found"
	test -f ImageFlipper.jar && rm ImageFlipper.jar || echo "ImageFlipper.jar not found"
	test -f Gaussian.jar && rm Gaussian.jar || echo "Gaussian.jar not found"
	
EXTRA_DIST = src build.xml
