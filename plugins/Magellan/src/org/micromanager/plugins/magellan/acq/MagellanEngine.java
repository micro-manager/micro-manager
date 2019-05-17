///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
package org.micromanager.plugins.magellan.acq;

/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
import com.google.common.eventbus.EventBus;
import java.awt.Color;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.swing.JOptionPane;
import org.micromanager.plugins.magellan.bidc.JavaLayerImageConstructor;
import org.micromanager.plugins.magellan.bidc.FrameIntegrationMethod;
import org.micromanager.plugins.magellan.channels.ChannelSetting;
import org.micromanager.plugins.magellan.coordinates.AffineUtils;
import java.awt.geom.AffineTransform;
import org.micromanager.plugins.magellan.json.JSONArray;
import org.micromanager.plugins.magellan.json.JSONObject;
import org.micromanager.plugins.magellan.main.Magellan;
import org.micromanager.plugins.magellan.misc.GlobalSettings;
import org.micromanager.plugins.magellan.misc.Log;
import org.micromanager.plugins.magellan.misc.MD;
import mmcorej.CMMCore;
import org.micromanager.plugins.magellan.demo.DemoModeImageData;
import org.micromanager.plugins.magellan.gui.GUI;
import org.micromanager.plugins.magellan.propsandcovariants.CovariantPairing;

/**
 * Engine has a single thread executor, which sits idly waiting for new
 * acquisitions when not in use
 */
public class MagellanEngine {

    private static final int DEMO_DELAY_Z = 0;
    private static final int DEMO_DELAY_XY = 0;
    private static final int DEMO_DELAY_IMAGE_CAPTURE = 0;
    private static final int HARDWARE_ERROR_RETRIES = 6;
    private static final int DELWAY_BETWEEN_RETRIES_MS = 5;
    private static CMMCore core_;
    private AcquisitionEvent lastEvent_ = null;
    private ExploreAcquisition currentExploreAcq_;
    private ParallelAcquisitionGroup currentFixedAcqs_;
    private MultipleAcquisitionManager multiAcqManager_;
    private ExecutorService acqExecutor_;
    private EventBus bus_;
    private AcqDurationEstimator acqDurationEstiamtor_;

    public MagellanEngine(CMMCore core, AcqDurationEstimator acqDurationEstiamtor) {
        core_ = core;
        acqDurationEstiamtor_ = acqDurationEstiamtor;
        acqExecutor_ = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "Custom Acquisition Engine Thread");
            }
        });
    }

    private void validateSettings(FixedAreaAcquisitionSettings settings) throws Exception {
        //space
        //non null surface
        if ((settings.spaceMode_ == FixedAreaAcquisitionSettings.REGION_2D || settings.spaceMode_ == FixedAreaAcquisitionSettings.SIMPLE_Z_STACK)
                && settings.footprint_ == null) {
            Log.log("Error: No surface or region selected for " + settings.name_, true);
            throw new Exception();
        }
        if (settings.spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK && settings.fixedSurface_ == null) {
            Log.log("Error: No surface selected for " + settings.name_, true);
            throw new Exception();
        }
        if (settings.spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK && settings.footprint_ == null) {
            Log.log("Error: No xy footprint selected for " + settings.name_, true);
            throw new Exception();
        }
        if (settings.spaceMode_ == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK
                && (settings.topSurface_ == null || settings.bottomSurface_ == null)) {
            Log.log("Error: No surface selected for " + settings.name_, true);
            throw new Exception();
        }
        //correct coordinate devices--XY
        if ((settings.spaceMode_ == FixedAreaAcquisitionSettings.REGION_2D || settings.spaceMode_ == FixedAreaAcquisitionSettings.SIMPLE_Z_STACK)
                && !settings.footprint_.getXYDevice().equals(core_.getXYStageDevice())) {
            Log.log("Error: XY device for surface/grid does match XY device in MM core in " + settings.name_, true);
            throw new Exception();
        }
        if (settings.spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK
                && !settings.fixedSurface_.getXYDevice().equals(core_.getXYStageDevice())) {
            Log.log("Error: XY device for surface does match XY device in MM core in " + settings.name_, true);
            throw new Exception();
        }
        if (settings.spaceMode_ == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK
                && (!settings.topSurface_.getXYDevice().equals(core_.getXYStageDevice())
                || !settings.bottomSurface_.getXYDevice().equals(core_.getXYStageDevice()))) {
            Log.log("Error: XY device for surface does match XY device in MM core in " + settings.name_, true);
            throw new Exception();
        }
        //correct coordinate device--Z
        if (settings.spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK
                && !settings.fixedSurface_.getZDevice().equals(core_.getFocusDevice())) {
            Log.log("Error: Z device for surface does match Z device in MM core in " + settings.name_, true);
            throw new Exception();
        }
        if (settings.spaceMode_ == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK
                && (!settings.topSurface_.getZDevice().equals(core_.getFocusDevice())
                || !settings.bottomSurface_.getZDevice().equals(core_.getFocusDevice()))) {
            Log.log("Error: Z device for surface does match Z device in MM core in " + settings.name_, true);
            throw new Exception();
        }

        //channels
//       if (settings.channels_.isEmpty()) {
//           Log.log("Error: no channels selected for " + settings.name_);
//           throw new Exception();
//       }
        //covariants
    }

    /**
     * Called by run acquisition button
     */
    public void runFixedAreaAcquisition(final FixedAreaAcquisitionSettings settings) {
        try {
            runInterleavedAcquisitions(Arrays.asList(new FixedAreaAcquisitionSettings[]{settings}), false);
        } catch (Exception ex) {
            Log.log(ex);
        }
    }

    /**
     * Called by run all button
     */
    public synchronized ParallelAcquisitionGroup runInterleavedAcquisitions(List<FixedAreaAcquisitionSettings> acqs, boolean multiAcq) throws Exception {
        //validate proposed settings
        for (FixedAreaAcquisitionSettings settings : acqs) {
            validateSettings(settings);
        }

        //check if current fixed acquisition is running
        //abort existing fixed acq if needed
        if (currentFixedAcqs_ != null && !currentFixedAcqs_.isFinished()) {
            int result = JOptionPane.showConfirmDialog(null, "Finish exisiting acquisition?", "Finish Current Acquisition", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                currentFixedAcqs_.abort();
            } else {
                return null;
            }
        }
        if (currentExploreAcq_ != null && !currentExploreAcq_.isFinished()) {
            //clear events so exploring doesn't surprisingly restart after acquisition
            currentExploreAcq_.clearEventQueue();
            try {
                //finish task as if explore had been aborted, but don't actually abort explore, so
                //that it can be returned to after
                currentExploreAcq_.events_.put(AcquisitionEvent.createEngineTaskFinishedEvent());
            } catch (InterruptedException ex) {
                Log.log("Unexpected interrupt when trying to switch acquisition tasks");
            }
        }

        currentFixedAcqs_ = new ParallelAcquisitionGroup(acqs, multiAcq ? multiAcqManager_ : null, bus_);
        runAcq(currentFixedAcqs_);
        //return to exploring once this acquisition finished
        if (currentExploreAcq_ != null && !currentExploreAcq_.isFinished()) {
            runAcq(currentExploreAcq_);
        }
        return currentFixedAcqs_;
    }

    public synchronized void runExploreAcquisition(final ExploreAcqSettings settings) {
        //abort existing explore acq if needed
        if (currentExploreAcq_ != null && !currentExploreAcq_.isFinished()) {
            int result = JOptionPane.showConfirmDialog(null, "Finish exisiting explore acquisition?", "Finish Current Explore Acquisition", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                currentExploreAcq_.abort();
            } else {
                return;
            }
        }
        //abort existing fixed acq if needed
        if (currentFixedAcqs_ != null && !currentFixedAcqs_.isFinished()) {
            int result = JOptionPane.showConfirmDialog(null, "Finish exisiting acquisition?", "Finish Current Acquisition", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                currentFixedAcqs_.abort();
            } else {
                return;
            }
        }
        try {
            currentExploreAcq_ = new ExploreAcquisition(settings);
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.log("Couldn't initialize explore acquisiton", true);
            Log.log(ex);
            return;
        }
        runAcq(currentExploreAcq_);
    }

    private void runAcq(final AcquisitionEventSource acq) {
        acqExecutor_.submit(new Runnable() {
            @Override
            public void run() {
                if (!(acq instanceof ExploreAcquisition)) {
                    GUI.getInstance().acquisitionRunning(true);
                }
                while (true) {
                    try {
                        if (Thread.interrupted()) {
                            if (!(acq instanceof ExploreAcquisition)) {
                                GUI.getInstance().acquisitionRunning(false);
                            }
                            return;
                        }
                        AcquisitionEvent event = acq.getNextEvent();
                        if (event.isEngineTaskFinishedEvent()) {
                            if (!(acq instanceof ExploreAcquisition)) {
                                GUI.getInstance().acquisitionRunning(false);
                            }
                            break; //this parallel group or explore acqusition is done
                        }
                        executeAcquisitionEvent(event);
                    } catch (InterruptedException ex) {
                        Log.log("Unexpected interrupt to acquisiton engine thread");
                        return;
                    }
                }
            }
        });
    }

    private void executeAcquisitionEvent(AcquisitionEvent event) throws InterruptedException {
        if (event.isReQueryEvent()) {
            //nothing to do, just a dummy event to get of blocking call when switching between parallel acquisitions
        } else if (event.isAcquisitionFinishedEvent()) {
            //signal to MagellanTaggedImageSink to finish saving thread and mark acquisition as finished
            JavaLayerImageConstructor.getInstance().addSignalMagellanTaggedImage(event, new SignalTaggedImage(SignalTaggedImage.AcqSingal.AcqusitionFinsihed));
        } else if (event.isTimepointFinishedEvent()) {
            //signal to MagellanTaggedImageSink to let acqusition know that saving for the current time point has completed  
            JavaLayerImageConstructor.getInstance().addSignalMagellanTaggedImage(event, new SignalTaggedImage(SignalTaggedImage.AcqSingal.TimepointFinished));
        } else if (event.isAutofocusAdjustmentEvent()) {
            setAutofocusPosition(event.autofocusZName_, event.autofocusPosition_);
        } else {
            updateHardware(event);
            double startTime = System.currentTimeMillis();
            acquireImage(event);
            if (GlobalSettings.getInstance().getDemoMode()) {
                Thread.sleep(DEMO_DELAY_IMAGE_CAPTURE);
            }
            try {
                acqDurationEstiamtor_.storeImageAcquisitionTime(core_.getExposure(), System.currentTimeMillis() - startTime);
            } catch (Exception ex) {
                Log.log(ex);
            }
            if (event.acquisition_ instanceof ExploreAcquisition) {
                ((ExploreAcquisition) event.acquisition_).eventAcquired(event);
            }
        }
    }

    private void acquireImage(final AcquisitionEvent event) throws InterruptedException {
        loopHardwareCommandRetries(new HardwareCommand() {
            @Override
            public void run() throws Exception {
                JavaLayerImageConstructor.getInstance().snapImage();
            }
        }, "snapping image");

        //get elapsed time
        final long currentTime = System.currentTimeMillis();
        if (event.acquisition_.getStartTime_ms() == -1) {
            //first image, initialize
            event.acquisition_.setStartTime_ms(currentTime);
        }

        //send to storage
        loopHardwareCommandRetries(new HardwareCommand() {
            @Override
            public void run() throws Exception {
                JavaLayerImageConstructor.getInstance().getMagellanTaggedImagesAndAddToAcq(event, currentTime, event.acquisition_.channels_.get(event.channelIndex_).exposure_);
            }
        }, "getting tagged image");

        //now that Core Communicator has added images into construction pipeline,
        //free to snap again which will add more to circular buffer
    }

    private void setAutofocusPosition(final String zName, final double pos) throws InterruptedException {

        loopHardwareCommandRetries(new HardwareCommand() {
            @Override
            public void run() throws Exception {
                core_.setPosition(zName, pos);
            }
        }, "Setting autofocus position");
    }

    private void updateHardware(final AcquisitionEvent event) throws InterruptedException {
        //compare to last event to see what needs to change
        if (lastEvent_ != null && lastEvent_.acquisition_ != event.acquisition_) {
            lastEvent_ = null; //update all hardware if switching to a new acquisition
        }
        //Get the hardware specific to this acquisition
        final String xyStage = event.acquisition_.getXYStageName();
        final String zStage = event.acquisition_.getZStageName();

        //move Z before XY 
        /////////////////////////////Z stage/////////////////////////////
        if (lastEvent_ == null || event.sliceIndex_ != lastEvent_.sliceIndex_ || event.positionIndex_ != lastEvent_.positionIndex_ ) {
            double startTime = System.currentTimeMillis();
            //wait for it to not be busy (is this even needed?)
            loopHardwareCommandRetries(new HardwareCommand() {
                @Override
                public void run() throws Exception {
                    while (core_.deviceBusy(zStage)) {
                        Thread.sleep(2);
                    }
                }
            }, "waiting for Z stage to not be busy");
            //move Z stage
            loopHardwareCommandRetries(new HardwareCommand() {
                @Override
                public void run() throws Exception {
                    core_.setPosition(zStage, event.zPosition_);
                    //delay in demo mode to simulate movement
                    if (GlobalSettings.getInstance().getDemoMode()) {
                        Thread.sleep(DEMO_DELAY_Z);
                    }
                }
            }, "move Z device");
            //wait for it to not be busy (is this even needed?)
            loopHardwareCommandRetries(new HardwareCommand() {
                @Override
                public void run() throws Exception {
                    while (core_.deviceBusy(zStage)) {
                        Thread.sleep(2);
                    }
                }
            }, "waiting for Z stage to not be busy");
            try {
                acqDurationEstiamtor_.storeZMoveTime(System.currentTimeMillis() - startTime);
            } catch (Exception ex) {
                Log.log(ex);
            }
        }


        /////////////////////////////XY Stage/////////////////////////////
        if (lastEvent_ == null || event.positionIndex_ != lastEvent_.positionIndex_) {
            double startTime = System.currentTimeMillis();
            //wait for it to not be busy (is this even needed??)
            loopHardwareCommandRetries(new HardwareCommand() {
                @Override
                public void run() throws Exception {
                    while (core_.deviceBusy(xyStage)) {
                        Thread.sleep(2);
                    }
                }
            }, "waiting for XY stage to not be busy");
            //move to new position
            loopHardwareCommandRetries(new HardwareCommand() {
                @Override
                public void run() throws Exception {
                    core_.setXYPosition(xyStage, event.xyPosition_.getCenter().x, event.xyPosition_.getCenter().y);
                    //delay in demo mode to simulate movement
                    if (GlobalSettings.getInstance().getDemoMode()) {
                        Thread.sleep(DEMO_DELAY_XY);
                    }
                }
            }, "moving XY stage");
            //wait for it to not be busy (is this even needed??)
            loopHardwareCommandRetries(new HardwareCommand() {
                @Override
                public void run() throws Exception {
                    while (core_.deviceBusy(xyStage)) {
                        Thread.sleep(2);
                    }
                }
            }, "waiting for XY stage to not be busy");
            try {
                acqDurationEstiamtor_.storeXYMoveTime(System.currentTimeMillis() - startTime);
            } catch (Exception ex) {
                Log.log(ex);
            }
        }

        /////////////////////////////Channels/////////////////////////////
        if (lastEvent_ == null || event.channelIndex_ != lastEvent_.channelIndex_
                && event.acquisition_.channels_ != null && !event.acquisition_.channels_.isEmpty()) {
            double startTime = System.currentTimeMillis();
            try {
                final ChannelSetting setting = event.acquisition_.channels_.get(event.channelIndex_);
                if (setting.use_ && setting.config_ != null) {
                    loopHardwareCommandRetries(new HardwareCommand() {
                        @Override
                        public void run() throws Exception {
                            //set exposure
                            core_.setExposure(setting.exposure_);
                            //set other channel props
                            core_.setConfig(setting.group_, setting.config_);
                            core_.waitForConfig(setting.group_, setting.config_);
                        }
                    }, "Set channel group");
                }

            } catch (Exception ex) {
                Log.log("Couldn't change channel group");
            }
            try {
                acqDurationEstiamtor_.storeChannelSwitchTime(System.currentTimeMillis() - startTime);
            } catch (Exception ex) {
                Log.log(ex);
            }
        }

        /////////////////////////////Covariants/////////////////////////////
        if (event.covariants_ != null) {
            outerloop:
            for (final CovariantPairing cp : event.covariants_) {
                //get the value of dependent covariant based on state of independent, and
                //change hardware settings as appropriate
                loopHardwareCommandRetries(new HardwareCommand() {
                    @Override
                    public void run() throws Exception {
                        cp.updateHardwareBasedOnPairing(event);
                    }
                }, "settng Covariant value pair " + cp.toString());
            }
        }
        lastEvent_ = event;
    }

    private void loopHardwareCommandRetries(HardwareCommand r, String commandName) throws InterruptedException {
        for (int i = 0; i < HARDWARE_ERROR_RETRIES; i++) {
            try {
                r.run();
                return;
            } catch (Exception e) {
                e.printStackTrace();
                Log.log(getCurrentDateAndTime() + ": Problem " + commandName + "\n Retry #" + i + " in " + DELWAY_BETWEEN_RETRIES_MS + " ms", true);
                Thread.sleep(DELWAY_BETWEEN_RETRIES_MS);
            }
        }
        Log.log(commandName + "unsuccessful", true);
    }

    private String getCurrentDateAndTime() {
        DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
        Calendar calobj = Calendar.getInstance();
        return df.format(calobj.getTime());
    }

    public static void addImageMetadata(JSONObject tags, AcquisitionEvent event, int timeIndex,
            int camChannelIndex, long elapsed_ms, double exposure) {
        //add tags
        try {
            long gridRow = event.acquisition_.getStorage().getGridRow(event.positionIndex_, 0);
            long gridCol = event.acquisition_.getStorage().getGridCol(event.positionIndex_, 0);
            MD.setPositionName(tags, "Grid_" + gridRow + "_" + gridCol);
            MD.setPositionIndex(tags, event.positionIndex_);
            MD.setSliceIndex(tags, event.sliceIndex_);
            MD.setFrameIndex(tags, timeIndex);
            MD.setChannelIndex(tags, event.channelIndex_ + camChannelIndex);
            MD.setZPositionUm(tags, event.zPosition_);
            MD.setElapsedTimeMs(tags, elapsed_ms);
            MD.setImageTime(tags, (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss -")).format(Calendar.getInstance().getTime()));
            MD.setExposure(tags, exposure);
            MD.setGridRow(tags, gridRow);
            MD.setGridCol(tags, gridCol);
            MD.setStageX(tags, event.xyPosition_.getCenter().x);
            MD.setStageY(tags, event.xyPosition_.getCenter().y);
            //add data about surface
            //right now this only works for fixed distance from the surface
            if ((event.acquisition_ instanceof FixedAreaAcquisition)
                    && ((FixedAreaAcquisition) event.acquisition_).getSpaceMode() == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
                //add metadata about surface
                MD.setSurfacePoints(tags, ((FixedAreaAcquisition) event.acquisition_).getSurfacePoints());
            }
        } catch (Exception e) {
            Log.log("Problem adding image metadata");
            throw new RuntimeException();
        }
    }

    public static JSONObject makeSummaryMD(Acquisition acq, String prefix) {
        //num channels is camera channels * acquisitionChannels
        int numChannels = GlobalSettings.getInstance().getDemoMode() ? DemoModeImageData.getNumChannels() : acq.getNumChannels();

        CMMCore core = Magellan.getCore();
        JSONObject summary = new JSONObject();
        //Actual number of channels is equal or less than this field
        MD.setNumChannels(summary, numChannels);
        //neither numchannels or numnumslices is guaranteeed to be accurate. Just initial estimates
        MD.setNumFrames(summary, acq.getInitialNumFrames());
        MD.setNumSlices(summary, acq.getInitialNumSlicesEstimate());
        MD.setZCTOrder(summary, false);
        MD.setPixelTypeFromByteDepth(summary, GlobalSettings.getInstance().isBIDCTwoPhoton()
                ? (acq.getFilterType() == FrameIntegrationMethod.FRAME_SUMMATION ? 2 : 1)
                : (int) core_.getBytesPerPixel());
        MD.setBitDepth(summary, GlobalSettings.getInstance().isBIDCTwoPhoton()
                ? (acq.getFilterType() == FrameIntegrationMethod.FRAME_SUMMATION ? 16 : 8)
                : (int) core_.getImageBitDepth());
        MD.setWidth(summary, JavaLayerImageConstructor.getInstance().getImageWidth());
        MD.setHeight(summary, JavaLayerImageConstructor.getInstance().getImageHeight());
        MD.setSavingPrefix(summary, prefix);
        JSONArray initialPosList = acq.createInitialPositionList();
        MD.setInitialPositionList(summary, initialPosList);
        MD.setPixelSizeUm(summary, core.getPixelSizeUm());
        MD.setZStepUm(summary, acq.getZStep());
        MD.setIntervalMs(summary, acq instanceof FixedAreaAcquisition ? ((FixedAreaAcquisition) acq).getTimeInterval_ms() : 0);
        MD.setPixelOverlapX(summary, acq.getOverlapX());
        MD.setPixelOverlapY(summary, acq.getOverlapY());
        MD.setExploreAcq(summary, acq instanceof ExploreAcquisition);
        if (GlobalSettings.getInstance().isBIDCTwoPhoton()) {
            MD.setImageConstructionFilter(summary, acq.getFilterType() == FrameIntegrationMethod.RANK_FILTER ? "RankFilter" :
                    (acq.getFilterType() == FrameIntegrationMethod.FRAME_AVERAGE ? "FrameAverage" : "FrameSummation" ));
            if (acq.getFilterType() == FrameIntegrationMethod.RANK_FILTER ) {
                MD.setRankFilterRank(summary, acq.getRank());
            }
        }
        //affine transform
        String pixelSizeConfig;
        try {
            pixelSizeConfig = core.getCurrentPixelSizeConfig();
        } catch (Exception ex) {
            Log.log("couldn't get affine transform");
            throw new RuntimeException();
        }
        AffineTransform at = AffineUtils.getAffineTransform(pixelSizeConfig, 0, 0);
        if (at == null) {
            Log.log("No affine transform found for pixel size config: " + pixelSizeConfig
                    + "\nUse \"Calibrate\" button on main Magellan window to configure\n\n");
            throw new RuntimeException();
        }
        MD.setAffineTransformString(summary, AffineUtils.transformToString(at));
        JSONArray chNames = new JSONArray();
        JSONArray chColors = new JSONArray();
        String[] names = acq.getChannelNames();
        Color[] colors = acq.getChannelColors();
        for (int i = 0; i < numChannels; i++) {
            chNames.put(names[i]);
            chColors.put(colors[i].getRGB());
        }
        MD.setChannelNames(summary, chNames);
        MD.setChannelColors(summary, chColors);
        try {
            MD.setCoreXY(summary, Magellan.getCore().getXYStageDevice());
            MD.setCoreFocus(summary, Magellan.getCore().getFocusDevice());
        } catch (Exception e) {
            Log.log("couldn't get XY or Z stage from core");
        }
        return summary;
    }

    private interface HardwareCommand {

        void run() throws Exception;
    }

    public void setMultiAcqManager(MultipleAcquisitionManager multiAcqManager) {
        multiAcqManager_ = multiAcqManager;
    }
}
