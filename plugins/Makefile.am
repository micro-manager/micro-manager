
## Process this file with automake to produce Makefile.in

TARGETS = \
	ASIdiSPIM.jar \
	AcquireMultipleRegions.jar \
	BFCorrector.jar \
	Big.jar \
	CRISP.jar \
	ClojureEditor.jar \
	DataBrowser.jar \
	Gaussian.jar \
	HCS.jar \
	IntelligentAcquisition.jar \
	MMTracker.jar \
	MultiCamera.jar \
	MultiChannelShading.jar \
	NewImageFlipper.jar \
	PixelCalibrator.jar \
	Projector.jar \
	RecallPlugin.jar \
	SlideExplorer.jar \
	SplitView.jar \
	StageControl.jar \
	autolase.jar \
	pgFocus.jar


ASIdiSPIM.jar:  \
	$(srcdir)/ASIdiSPIM/src/org/micromanager/asidispim/*.java \
	$(srcdir)/ASIdiSPIM/src/org/micromanager/asidispim/Data/*.java \
	$(srcdir)/ASIdiSPIM/src/org/micromanager/asidispim/Utils/*.java
	rm -rf build/ASIdiSPIM
	$(MKDIR_P) build/ASIdiSPIM
	$(JAVAC) $(JAVACFLAGS) -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):$(JFREECHARTJARPATH):$(CLASSEXT)/miglayout-4.0-swing.jar:../mmstudio/MMJ_.jar:. $(srcdir)/ASIdiSPIM/src/org/micromanager/asidispim/*.java $(srcdir)/ASIdiSPIM/src/org/micromanager/asidispim/Utils/*.java $(srcdir)/ASIdiSPIM/src/org/micromanager/asidispim/Data/*.java -d build/ASIdiSPIM
	cp -r $(srcdir)/ASIdiSPIM/src/org/micromanager/asidispim/icons build/ASIdiSPIM/org/micromanager/asidispim
	cd build/ASIdiSPIM && jar cf ../../ASIdiSPIM.jar *

IntelligentAcquisition.jar:  \
	$(srcdir)/IntelligentAcquisition/src/org/micromanager/intelligentacquisition/*.java
	rm -rf build/IntelligentAcquisition
	$(MKDIR_P) build/IntelligentAcquisition
	$(JAVAC) $(JAVACFLAGS) -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):$(JFREECHARTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/IntelligentAcquisition/src/org/micromanager/intelligentacquisition/*.java -d build/IntelligentAcquisition
	cd build/IntelligentAcquisition && jar cf ../../IntelligentAcquisition.jar *

MMTracker.jar:  \
	$(srcdir)/Tracker/src/com/imaging100x/tracker/TrackerControl.java \
	$(srcdir)/Tracker/src/com/imaging100x/tracker/TrackerUtils.java
	rm -rf build/Tracker
	$(MKDIR_P) build/Tracker
	$(JAVAC) $(JAVACFLAGS) -classpath $(IJJARPATH):$(BSHJARPATH):$(JFREECHARTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/Tracker/src/com/imaging100x/tracker/*.java -d build/Tracker
	cd build/Tracker && jar cf ../../MMTracker.jar *

PixelCalibrator.jar:  \
	$(srcdir)/PixelCalibrator/src/org/micromanager/pixelcalibrator/CalibrationThread.java \
	$(srcdir)/PixelCalibrator/src/org/micromanager/pixelcalibrator/PixelCalibratorDialog.java \
	$(srcdir)/PixelCalibrator/src/org/micromanager/pixelcalibrator/PixelCalibratorPlugin.java \
	$(srcdir)/PixelCalibrator/src/org/micromanager/pixelcalibrator/PixelCalibratorDialog.form
	rm -rf build/PixelCalibrator
	$(MKDIR_P) build/PixelCalibrator
	$(JAVAC) $(JAVACFLAGS) -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/PixelCalibrator/src/org/micromanager/pixelcalibrator/*.java -d build/PixelCalibrator
	cd build/PixelCalibrator && jar cf ../../PixelCalibrator.jar *

Projector.jar:  \
	$(srcdir)/Projector/src/org/micromanager/projector/Galvo.java  \
	$(srcdir)/Projector/src/org/micromanager/projector/MosaicSequencingFrame.java \
	$(srcdir)/Projector/src/org/micromanager/projector/OnStateListener.java  \
	$(srcdir)/Projector/src/org/micromanager/projector/ProjectionDevice.java  \
	$(srcdir)/Projector/src/org/micromanager/projector/ProjectorControlForm.java  \
	$(srcdir)/Projector/src/org/micromanager/projector/ProjectorController.java  \
	$(srcdir)/Projector/src/org/micromanager/projector/ProjectorPlugin.java  \
	$(srcdir)/Projector/src/org/micromanager/projector/SLM.java
	rm -rf build/Projector
	$(MKDIR_P) build/Projector
	$(JAVAC) $(JAVACFLAGS) -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/Projector/src/org/micromanager/projector/*.java -d build/Projector
	cd build/Projector && jar cf ../../Projector.jar *

SlideExplorer.jar:  \
	$(srcdir)/SlideExplorer/src/org/micromanager/slideexplorer/*.java \
	$(srcdir)/SlideExplorer/src/org/micromanager/slideexplorer/*.form
	rm -rf build/SlideExplorer
	$(MKDIR_P) build/SlideExplorer
	$(JAVAC) $(JAVACFLAGS) -classpath $(srcdir)/PixelCalibrator.jar:$(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/SlideExplorer/src/org/micromanager/slideexplorer/*.java -d build/SlideExplorer
	cd build/SlideExplorer && jar cf ../../SlideExplorer.jar *

RecallPlugin.jar:  \
	$(srcdir)/Recall/src/org/micromanager/recall/*.java
	rm -rf build/RecallPlugin
	$(MKDIR_P) build/RecallPlugin
	$(JAVAC) $(JAVACFLAGS) -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/Recall/src/org/micromanager/recall/*.java -d build/RecallPlugin
	cd build/RecallPlugin && jar cf ../../RecallPlugin.jar *

StageControl.jar:  \
	$(srcdir)/StageControl/src/org/micromanager/stagecontrol/*.java
	rm -rf build/StageControl
	$(MKDIR_P) build/StageControl
	$(JAVAC) $(JAVACFLAGS) -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/StageControl/src/org/micromanager/stagecontrol/*.java -d build/StageControl
	cp -r $(srcdir)/StageControl/src/org/micromanager/stagecontrol/icons build/StageControl/org/micromanager/stagecontrol
	cd build/StageControl && jar cf ../../StageControl.jar *

MultiCamera.jar:  \
	$(srcdir)/MultiCamera/src/org/micromanager/multicamera/*.java
	rm -rf build/MultiCamera
	$(MKDIR_P) build/MultiCamera
	$(JAVAC) $(JAVACFLAGS) -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/MultiCamera/src/org/micromanager/multicamera/*.java -d build/MultiCamera
	cd build/MultiCamera && jar cf ../../MultiCamera.jar *

SplitView.jar:  \
	$(srcdir)/SplitView/src/org/micromanager/splitview/*.java
	rm -rf build/SplitView
	$(MKDIR_P) build/SplitView
	$(JAVAC) $(JAVACFLAGS) -classpath $(srcdir)/SplitView.jar:$(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/SplitView/src/org/micromanager/splitview/*.java -d build/SplitView
	cd build/SplitView && jar cf ../../SplitView.jar *

NewImageFlipper.jar:  \
	$(srcdir)/NewImageFlipper/src/org/micromanager/newimageflipper/*.java
	rm -rf build/NewImageFlipper
	$(MKDIR_P) build/NewImageFlipper
	$(JAVAC) $(JAVACFLAGS) -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/NewImageFlipper/src/org/micromanager/newimageflipper/*.java -d build/NewImageFlipper
	cd build/NewImageFlipper && jar cf ../../NewImageFlipper.jar *

Big.jar:  \
	$(srcdir)/Big/src/edu/umassmed/big/*.java
	rm -rf build/Big
	$(MKDIR_P) build/Big
	$(JAVAC) $(JAVACFLAGS) -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/Big/src/edu/umassmed/big/*.java -d build/Big
	cd build/Big && jar cf ../../Big.jar *

CRISP.jar:  \
	$(srcdir)/CRISP/src/org/micromanager/CRISP/*.java
	rm -rf build/CRISP
	$(MKDIR_P) build/CRISP
	$(JAVAC) $(JAVACFLAGS) -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):$(JFREECHARTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/CRISP/src/org/micromanager/CRISP/*.java -d build/CRISP
	cd build/CRISP && jar cf ../../CRISP.jar *

BFCorrector.jar:  \
	$(srcdir)/BFCorrector/src/org/micromanager/bfcorrector/*.java
	rm -rf build/BFCorrector
	$(MKDIR_P) build/BFCorrector
	$(JAVAC) $(JAVACFLAGS) -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/BFCorrector/src/org/micromanager/bfcorrector/*.java -d build/BFCorrector
	cd build/BFCorrector && jar cf ../../BFCorrector.jar *

MultiChannelShading.jar: \
	$(srcdir)/MultiChannelShading/src/org/micromanager/multichannelshading/*.java \
	$(srcdir)/MultiChannelShading/src/org/micromanager/multichannelshading/*.form
	rm -rf build/MultiChannelShading
	$(MKDIR_P) build/MultiChannelShading
	$(JAVAC) $(JAVACFLAGS) -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):../mmstudio/MMJ_.jar:. $(srcdir)/MultiChannelShading/src/org/micromanager/multichannelshading/*.java -d build/MultiChannelShading
	cd build/MultiChannelShading && jar cf ../../MultiChannelShading.jar *

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
	rm -rf build/Gaussian
	$(MKDIR_P) build/Gaussian
	$(JAVAC) $(JAVACFLAGS) -classpath $(IJJARPATH):$(BSHJARPATH):$(COMMONSMATHJARPATH):$(SWINGLAYOUTJARPATH):$(JFREECHARTJARPATH):../mmstudio/MMJ_.jar:$(CLASSEXT)/TSFProto.jar:$(CLASSEXT)/gproto.jar:$(CLASSEXT)/lwm.jar:. $(srcdir)/Gaussian/source/GaussianTrack_.java $(srcdir)/Gaussian/source/edu/valelab/GaussianFit/*.java $(srcdir)/Gaussian/source/edu/valelab/GaussianFit/utils/*.java $(srcdir)/Gaussian/source/ags/utils/*.java -d build/Gaussian
	cp $(srcdir)/Gaussian/source/edu/valelab/GaussianFit/*.txt build/Gaussian/edu/valelab/GaussianFit
	cd build/Gaussian && jar cf ../../Gaussian.jar *

DataBrowser.jar: \
	$(srcdir)/DataBrowser/src/org/micromanager/browser/*.clj
	rm -rf build/DataBrowser/classes
	$(MKDIR_P) build/DataBrowser/classes
	cp -R $(srcdir)/DataBrowser/src/org build/DataBrowser/classes/
	java -cp build/DataBrowser/classes/:$(CLASSEXT)/clojure.jar:$(CLASSEXT)/clojure-contrib.jar:$(CLASSEXT)/data.json.jar:$(IJJARPATH):../MMCoreJ_wrap/MMCoreJ.jar:../mmstudio/MMJ_.jar:$(CLASSEXT)/bsh-2.0b4.jar:../acqEngine/MMAcqEngine.jar:$(srcdir)/DataBrowser/src -Djava.awt.headless=true -Dclojure.compile.path=build/DataBrowser/classes -server clojure.lang.Compile org.micromanager.browser.plugin
	jar cf DataBrowser.jar -C build/DataBrowser/classes/ .


ClojureEditor.jar:  \
	$(srcdir)/ClojureEditor/src/org/micromanager/clojureeditor/*.java
	rm -rf build/ClojureEditor
	$(MKDIR_P) build/ClojureEditor
	$(JAVAC) $(JAVACFLAGS) -classpath $(srcdir)/ClojureEditor.jar:$(CLASSEXT)/*:../mmstudio/MMJ_.jar:. $(srcdir)/ClojureEditor/src/org/micromanager/clojureeditor/*.java -d build/ClojureEditor
	cd build/ClojureEditor && jar cf ../../ClojureEditor.jar *


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
	rm -rf build/HCS
	$(MKDIR_P) build/HCS
	$(JAVAC) $(JAVACFLAGS) -classpath ../mmstudio/MMJ_.jar:. $(srcdir)/HCS/src/org/micromanager/hcs/*.java -d build/HCS
	cd build/HCS && jar cf ../../HCS.jar *

pgFocus.jar: \
	$(srcdir)/pgFocus/src/edu/umassmed/pgFocus/pgFocus.java \
	$(srcdir)/pgFocus/src/edu/umassmed/pgFocus/pgFocusFrame.java
	rm -rf build/pgFocus
	$(MKDIR_P) build/pgFocus
	$(JAVAC) $(JAVACFLAGS) -classpath ../mmstudio/MMJ_.jar:$(JFREECHARTJARPATH):. $(srcdir)/pgFocus/src/edu/umassmed/pgFocus/*.java -d build/pgFocus
	cp -r $(srcdir)/pgFocus/src/edu/umassmed/pgFocus/umass_logo.png build/pgFocus/edu/umassmed/pgfocus
	cd build/pgFocus && jar cf ../../pgFocus.jar *

autolase.jar: \
	$(srcdir)/autolase/src/ch/epfl/leb/autolase/AutoLase.java \
	$(srcdir)/autolase/src/ch/epfl/leb/autolase/AutoLaseDialog.java \
	$(srcdir)/autolase/src/ch/epfl/leb/autolase/AutoLaseOptionsDialog.java \
	$(srcdir)/autolase/src/ch/epfl/leb/autolase/AutoLasePlugin.java \
	$(srcdir)/autolase/src/ch/epfl/leb/autolase/Camera.java \
	$(srcdir)/autolase/src/ch/epfl/leb/autolase/DensityFileLogger.java \
	$(srcdir)/autolase/src/ch/epfl/leb/autolase/DensityMap.java \
	$(srcdir)/autolase/src/ch/epfl/leb/autolase/DensityMapMonitor.java \
	$(srcdir)/autolase/src/ch/epfl/leb/autolase/DensityMonitor.java \
	$(srcdir)/autolase/src/ch/epfl/leb/autolase/DensityProfiler.java \
	$(srcdir)/autolase/src/ch/epfl/leb/autolase/DensityThread.java \
	$(srcdir)/autolase/src/ch/epfl/leb/autolase/LaseThread.java \
	$(srcdir)/autolase/src/ch/epfl/leb/autolase/LaserConfig.java \
	$(srcdir)/autolase/src/ch/epfl/leb/autolase/LaserPowerMonitor.java \
	$(srcdir)/autolase/src/ch/epfl/leb/autolase/MMCamera.java \
	$(srcdir)/autolase/src/ch/epfl/leb/autolase/RegularIntervalThread.java \
	$(srcdir)/autolase/src/ch/epfl/leb/autolase/TiffCamera.java
	rm -rf build/autolase
	$(MKDIR_P) build/autolase
	$(JAVAC) $(JAVACFLAGS) -classpath ../mmstudio/MMJ_.jar:$(IJJARPATH):. $(srcdir)/autolase/src/ch/epfl/leb/autolase/*.java -d build/autolase
	cd build/autolase && jar cf ../../autolase.jar *

AcquireMultipleRegions.jar: \
	$(srcdir)/AcquireMultipleRegions/src/org/micromanager/acquiremultipleregions/AcquireMultipleRegions.java \
	$(srcdir)/AcquireMultipleRegions/src/org/micromanager/acquiremultipleregions/AcquireMultipleRegionsForm.java \
	$(srcdir)/AcquireMultipleRegions/src/org/micromanager/acquiremultipleregions/Region.java \
	$(srcdir)/AcquireMultipleRegions/src/org/micromanager/acquiremultipleregions/RegionList.java
	rm -rf build/AcquireMultipleRegions
	$(MKDIR_P) build/AcquireMultipleRegions
	$(JAVAC) $(JAVACFLAGS) -classpath ../mmstudio/MMJ_.jar:$(IJJARPATH):$(SWINGLAYOUTJARPATH):. $(srcdir)/AcquireMultipleRegions/src/org/micromanager/acquiremultipleregions/*.java -d build/AcquireMultipleRegions
	cd build/AcquireMultipleRegions && jar cf ../../AcquireMultipleRegions.jar *


mmplugin_DATA = $(TARGETS)


CLEANFILES = $(TARGETS)

clean-local:
	-rm -rf build


EXTRA_DIST = src
