## Process this file with automake to produce Makefile.in

TARGETS= ASIdiSPIM.jar PixelCalibrator.jar Projector.jar RecallPlugin.jar StageControl.jar MultiCamera.jar CRISP.jar Big.jar NewImageFlipper.jar SlideExplorer.jar DataBrowser.jar ClojureEditor.jar SplitView.jar Gaussian.jar IntelligentAcquisition.jar BFCorrector.jar MultiChannelShading.jar HCS.jar

ASIdiSPIM.jar:  \
	$(srcdir)/ASIdiSPIM/src/org/micromanager/asidispim/*.java \
	$(srcdir)/ASIdiSPIM/src/org/micromanager/asidispim/Data/*.java \
	$(srcdir)/ASIdiSPIM/src/org/micromanager/asidispim/Utils/*.java 
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) $(JAVACFLAGS) -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):$(JFREECHARTJARPATH):$(CLASSEXT)/miglayout-4.0-swing.jar:../mmstudio/MMJ_.jar:. $(srcdir)/ASIdiSPIM/src/org/micromanager/asidispim/*.java $(srcdir)/ASIdiSPIM/src/org/micromanager/asidispim/Utils/*.java $(srcdir)/ASIdiSPIM/src/org/micromanager/asidispim/Data/*.java -d build
	cd build && jar cf ../ASIdiSPIM.jar * 

IntelligentAcquisition.jar:  \
	$(srcdir)/IntelligentAcquisition/src/org/micromanager/intelligentacquisition/*.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) $(JAVACFLAGS) -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):$(JFREECHARTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/IntelligentAcquisition/src/org/micromanager/intelligentacquisition/*.java -d build
	cd build && jar cf ../IntelligentAcquisition.jar * 

MMTracker_.jar:  \
	$(srcdir)/Tracker/src/com/imaging100x/tracker/TrackerControl.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) $(JAVACFLAGS) -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/Tracker/src/com/imaging100x/tracker/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../MMTracker_.jar * 

PixelCalibrator.jar:  \
	$(srcdir)/PixelCalibrator/src/org/micromanager/pixelcalibrator/CalibrationThread.java \
	$(srcdir)/PixelCalibrator/src/org/micromanager/pixelcalibrator/PixelCalibratorDialog.java \
	$(srcdir)/PixelCalibrator/src/org/micromanager/pixelcalibrator/PixelCalibratorPlugin.java \
	$(srcdir)/PixelCalibrator/src/org/micromanager/pixelcalibrator/PixelCalibratorDialog.form 
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) $(JAVACFLAGS) -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/PixelCalibrator/src/org/micromanager/pixelcalibrator/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../PixelCalibrator.jar * 

Projector.jar:  \
	$(srcdir)/Projector/src/org/micromanager/projector/Galvo.java  \
	$(srcdir)/Projector/src/org/micromanager/projector/OnStateListener.java  \
	$(srcdir)/Projector/src/org/micromanager/projector/ProjectionDevice.java  \
	$(srcdir)/Projector/src/org/micromanager/projector/ProjectorControlForm.java  \
	$(srcdir)/Projector/src/org/micromanager/projector/ProjectorController.java  \
	$(srcdir)/Projector/src/org/micromanager/projector/ProjectorPlugin.java  \
	$(srcdir)/Projector/src/org/micromanager/projector/SLM.java
	test -d build-Projector || mkdir build-Projector
	rm -rf build-Projector/*
	$(JAVAC) $(JAVACFLAGS) -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/Projector/src/org/micromanager/projector/*.java -d build-Projector
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build-Projector && jar cf ../Projector.jar * 

SlideExplorer.jar:  \
	$(srcdir)/SlideExplorer/src/org/micromanager/slideexplorer/*.java \
	$(srcdir)/SlideExplorer/src/org/micromanager/slideexplorer/*.form
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) $(JAVACFLAGS) -sourcepath $(srcdir):. -classpath $(srcdir)/PixelCalibrator.jar:$(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/SlideExplorer/src/org/micromanager/slideexplorer/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../SlideExplorer.jar * 

RecallPlugin.jar:  \
	$(srcdir)/Recall/src/org/micromanager/recall/*.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) $(JAVACFLAGS) -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/Recall/src/org/micromanager/recall/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../RecallPlugin.jar * 

StageControl.jar:  \
	$(srcdir)/StageControl/src/org/micromanager/stagecontrol/*.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) $(JAVACFLAGS) -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/StageControl/src/org/micromanager/stagecontrol/*.java -d build
	cp -r $(srcdir)/StageControl/src/org/micromanager/stagecontrol/icons build/org/micromanager/stagecontrol/
	cd build && jar cf ../StageControl.jar * 

MultiCamera.jar:  \
	$(srcdir)/MultiCamera/src/org/micromanager/multicamera/*.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) $(JAVACFLAGS) -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/MultiCamera/src/org/micromanager/multicamera/*.java -d build
	cd build && jar cf ../MultiCamera.jar * 

SplitView.jar:  \
	$(srcdir)/SplitView/src/org/micromanager/splitview/*.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) $(JAVACFLAGS) -sourcepath $(srcdir):. -classpath $(srcdir)/SplitView.jar:$(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/SplitView/src/org/micromanager/splitview/*.java -d build
	cd build && jar cf ../SplitView.jar * 

NewImageFlipper.jar:  \
	$(srcdir)/NewImageFlipper/src/org/micromanager/newimageflipper/*.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) $(JAVACFLAGS) -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/NewImageFlipper/src/org/micromanager/newimageflipper/*.java -d build
	cd build && jar cf ../NewImageFlipper.jar * 

Big.jar:  \
	$(srcdir)/Big/src/edu/umassmed/big/*.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) $(JAVACFLAGS) -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/Big/src/edu/umassmed/big/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../Big.jar * 

CRISP.jar:  \
	$(srcdir)/CRISP/src/org/micromanager/CRISP/*.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) $(JAVACFLAGS) -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):$(JFREECHARTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/CRISP/src/org/micromanager/CRISP/*.java -d build
	cd build && jar cf ../CRISP.jar * 

BFCorrector.jar:  \
	$(srcdir)/BFCorrector/src/org/micromanager/bfcorrector/*.java
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) $(JAVACFLAGS) -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/BFCorrector/src/org/micromanager/bfcorrector/*.java -d build
	cd build && jar cf ../BFCorrector.jar * 

MultiChannelShading.jar: \
	$(srcdir)/MultiChannelShading/src/org/micromanager/multichannelshading/*.java \
	$(srcdir)/MultiChannelShading/src/org/micromanager/multichannelshading/*.form
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) $(JAVACFLAGS) -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/MultiChannelShading/src/org/micromanager/multichannelshading/*.java -d build
	cd build && jar cf ../MultiChannelShading.jar *

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
	$(JAVAC) $(JAVACFLAGS) -sourcepath $(srcdir):. -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):$(JFREECHARTJARPATH):../mmstudio/MMJ_.jar:$(CLASSEXT)/TSFProto.jar:$(CLASSEXT)/gproto.jar:$(CLASSEXT)/lwm.jar:. $(srcdir)/Gaussian/source/GaussianTrack_.java $(srcdir)/Gaussian/source/edu/valelab/GaussianFit/*.java $(srcdir)/Gaussian/source/edu/valelab/GaussianFit/utils/*.java $(srcdir)/Gaussian/source/ags/utils/*.java -d build
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
	$(JAVAC) $(JAVACFLAGS) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath $(srcdir)/ClojureEditor.jar:$(CLASSEXT)/*:../mmstudio/MMJ_.jar:. $(srcdir)/ClojureEditor/src/org/micromanager/clojureeditor/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../ClojureEditor.jar * 


HCS.jar:  \
	$(srcdir)/HCS/src/org/micromanager/hcs/AFPlane.java \
	$(srcdir)/HCS/src/org/micromanager/hcs/HCSAbout.java \
	$(srcdir)/HCS/src/org/micromanager/hcs/HCSException.java \
	$(srcdir)/HCS/src/org/micromanager/hcs/ParentPlateGUI.java \
	$(srcdir)/HCS/src/org/micromanager/hcs/PlatePanel.java \
	$(srcdir)/HCS/src/org/micromanager/hcs/SBSPlate.java \
	$(srcdir)/HCS/src/org/micromanager/hcs/SiteGenerator.java \
	$(srcdir)/HCS/src/org/micromanager/hcs/TestSBSPlate.java \
	$(srcdir)/HCS/src/org/micromanager/hcs/WellPositionList.java 
	test -d build || mkdir build
	rm -rf build/*
	$(JAVAC) $(JAVACFLAGS) -source 1.5 -target 1.5 -sourcepath $(srcdir):. -classpath ../mmstudio/MMJ_.jar:. $(srcdir)/HCS/src/org/micromanager/hcs/*.java -d build
	## Work around bug in 'jar': jar cf MM_.jar -C bin/ bin/* does not work
	cd build && jar cf ../HCS.jar * 

all: ${TARGETS}

install: 
	test -d $(DESTDIR)$(IJPATH)/mmplugins || mkdir $(DESTDIR)$(IJPATH)/mmplugins
	for thisjar in $(TARGETS); \
	do \
		cp $$thisjar $(DESTDIR)$(IJPATH)/mmplugins; \
	done
	
clean:
	for f in $(TARGETS); \
	do \
		test -f $$f && rm $$f || echo "$$f not found"; \
	done

 EXTRA_DIST = src build.xml
