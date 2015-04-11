package acq;

/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
import demo.DemoModeImageData;
import gui.SettingsDialog;
import ij.IJ;
import java.awt.Color;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import imageconstruction.CoreCommunicator;
import misc.GlobalSettings;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.api.MMTags;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;
import propsandcovariants.CovariantPairing;

/**
 * Engine has a single thread executor, which sits idly waiting for new
 * acquisitions when not in use
 */
public class CustomAcqEngine {

    private static final int HARDWARE_ERROR_RETRIES = 6;
    private static final int DELWAY_BETWEEN_RETRIES_MS = 50;
    private CMMCore core_;
    private AcquisitionEvent lastEvent_ = null;
    private ExploreAcquisition currentExploreAcq_;
    private ParallelAcquisitionGroup currentFixedAcqs_;
    private MultipleAcquisitionManager multiAcqManager_;
    private ExecutorService acqExecutor_;

    public CustomAcqEngine(CMMCore core) {
        core_ = core;
        acqExecutor_ = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "Custom Acquisition Engine Thread");
            }
        });
    }

    /**
     * Called by run acquisition button
     */
    public void runFixedAreaAcquisition(final FixedAreaAcquisitionSettings settings) {
        runInterleavedAcquisitions(Arrays.asList(new FixedAreaAcquisitionSettings[]{settings}), false);
    }

    /**
     * Called by run all button
     */
    public synchronized ParallelAcquisitionGroup runInterleavedAcquisitions(List<FixedAreaAcquisitionSettings> acqs, boolean multiAcq) {
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
                ReportingUtils.showError("Unexpected interrupt when trying to switch acquisition tasks");
            }
        }

        currentFixedAcqs_ = new ParallelAcquisitionGroup(acqs, multiAcq ? multiAcqManager_ : null);
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
            ReportingUtils.showError("Couldn't initialize explore acquisiton");
            return;
        }
        runAcq(currentExploreAcq_);
    }

    private void runAcq(final AcquisitionEventSource acq) {
        acqExecutor_.submit(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        if (Thread.interrupted()) {
                            return;
                        }
                        AcquisitionEvent event = acq.getNextEvent();
                        if (event.isEngineTaskFinishedEvent()) {
                            break; //this parallel group or explore acqusition is done
                        }
                        executeAcquisitionEvent(event);
                    } catch (InterruptedException ex) {
                        ReportingUtils.showError("Unexpected interrupt to acquisiton engine thread");
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
            //signal to TaggedImageSink to finish saving thread and mark acquisition as finished
           CoreCommunicator.addSignalTaggedImage(event.acquisition_, new SignalTaggedImage(SignalTaggedImage.AcqSingal.AcqusitionFinsihed));
        } else if (event.isTimepointFinishedEvent()) {
            //signal to TaggedImageSink to let acqusition know that saving for the current time point has completed  
            CoreCommunicator.addSignalTaggedImage(event.acquisition_, new SignalTaggedImage(SignalTaggedImage.AcqSingal.TimepointFinished));
        } else if (event.isAutofocusAdjustmentEvent()) {
            setAutofocusPosition(event.autofocusZName_, event.autofocusPosition_);
        } else {
            updateHardware(event);
            acquireImage(event);
        }
    }

    private void acquireImage(final AcquisitionEvent event) throws InterruptedException {
        loopHardwareCommandRetries(new HardwareCommand() {
            @Override
            public void run() throws Exception {
                CoreCommunicator.snapImage();
            }
        }, "snapping image");

        //get elapsed time
        final long currentTime = System.currentTimeMillis();
        if (event.acquisition_.getStartTime_ms() == -1) {
            //first image, initialize
            event.acquisition_.setStartTime_ms(currentTime);
        }

        final int numCamChannels = (int) core_.getNumberOfCameraChannels();
        for (int c = 0; c < numCamChannels; c++) {
            //send to storage
            final int channelIndex = c;
            loopHardwareCommandRetries(new HardwareCommand() {
                @Override
                public void run() throws Exception {
                    CoreCommunicator.getTaggedImageAndAddToAcq(channelIndex, event, currentTime, 
                            GlobalSettings.getDemoMode()? GlobalSettings.getDemoNumChannels() : numCamChannels); 
                }
            }, "getting tagged image");          
        }
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
        if (lastEvent_ == null || event.sliceIndex_ != lastEvent_.sliceIndex_) {
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
        }
        
        /////////////////////////////XY Stage/////////////////////////////
        if (lastEvent_ == null || event.positionIndex_ != lastEvent_.positionIndex_) {
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
        }

        /////////////////////////////Channels/////////////////////////////
//      if (lastEvent_ == null || event.channelIndex_ != lastEvent_.channelIndex_) {
//         try {
//            core_.setConfig("Channel", event.channelIndex_ == 0 ? "DAPI" : "FITC");
//         } catch (Exception ex) {
//            ReportingUtils.showError("Couldn't change channel group");
//         }
//      }

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
                IJ.log(getCurrentDateAndTime() + ": Problem " + commandName + "\n Retry #" + i + " in " + DELWAY_BETWEEN_RETRIES_MS + " ms");
                Thread.sleep(DELWAY_BETWEEN_RETRIES_MS);
            }
        }
        IJ.log("Couldn't successfully " + commandName);
    }

    private String getCurrentDateAndTime() {
        DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
        Calendar calobj = Calendar.getInstance();
        return df.format(calobj.getTime());
    }

    public static void addImageMetadata(TaggedImage img, AcquisitionEvent event,
            int numCamChannels, int camChannel, long elapsed_ms) {
        try {
            //add tags
            long gridRow = event.acquisition_.getPositionManager().getGridRow(event.positionIndex_, 0);
            long gridCol = event.acquisition_.getPositionManager().getGridCol(event.positionIndex_, 0);
            img.tags.put(MMTags.Image.POS_NAME, "Grid_" + gridRow + "_" + gridCol);
            img.tags.put(MMTags.Image.POS_INDEX, event.positionIndex_);
            img.tags.put(MMTags.Image.SLICE_INDEX, event.sliceIndex_);
            img.tags.put(MMTags.Image.SLICE, event.sliceIndex_);
            img.tags.put(MMTags.Image.FRAME_INDEX, event.timeIndex_);
            img.tags.put(MMTags.Image.FRAME, event.timeIndex_);
            img.tags.put(MMTags.Image.CHANNEL_INDEX, event.channelIndex_ * numCamChannels + camChannel);
            img.tags.put(MMTags.Image.ZUM, event.zPosition_);
            img.tags.put(MMTags.Image.ELAPSED_TIME_MS, elapsed_ms);
            img.tags.put(MMTags.Image.TIME, (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss -")).format(Calendar.getInstance().getTime()));
            //Magellan specific tags
            img.tags.put("GridColumnIndex", gridCol);
            img.tags.put("GridRowIndex", gridRow);
        } catch (JSONException e) {
            IJ.log("Problem adding image tags");
            IJ.log(e.toString());
            e.printStackTrace();
        }
    }

    public static JSONObject makeSummaryMD(Acquisition acq, String prefix) {
        try {

            //num channels is camera channels * acquisitionChannels
            int numChannels = GlobalSettings.getDemoMode() ? GlobalSettings.getDemoNumChannels() : acq.getNumChannels();

            /////////////////////////////////////////////////////////////////
            ////Summary metadata equivalent to normal MM metadata////////////
            /////////////////////////////////////////////////////////////////
            CMMCore core = MMStudio.getInstance().getCore();
            JSONObject summary = new JSONObject();
            summary.put("Channels", numChannels);
            summary.put("Frames", 1);
            summary.put("SlicesFirst", true);
            summary.put("TimeFirst", false);
            summary.put("PixelType", core.getBytesPerPixel() == 1 ? "GRAY8" : "GRAY16");
            summary.put("BitDepth", core.getImageBitDepth());
            summary.put("Width", CoreCommunicator.getImageWidth());
            summary.put("Height", CoreCommunicator.getImageHeight());
            summary.put("Prefix", prefix);
            JSONArray initialPosList = acq.createInitialPositionList();
            summary.put("InitialPositionList", initialPosList);
            summary.put("Positions", initialPosList.length());
            summary.put(MMTags.Summary.PIXSIZE, core.getPixelSizeUm());
            summary.put("z-step_um", acq.getZStep());
            summary.put("Interval_ms", acq instanceof FixedAreaAcquisition ? ((FixedAreaAcquisition) acq).getTimeInterval_ms() : 0);

            /////////////////////////////////////////////////////////////////
            ////Summary metadata specific to Magellan///////////////////////
            /////////////////////////////////////////////////////////////////

            //write pixel overlap into metadata
            summary.put("GridPixelOverlapX", acq.getOverlapX());
            summary.put("GridPixelOverlapY", acq.getOverlapY());
            summary.put("MagellanExploreAcquisition", acq instanceof ExploreAcquisition);


            //TODO: make this legit
            JSONArray chNames = new JSONArray();
            JSONArray chColors = new JSONArray();
            for (int i = 0; i < numChannels; i++) {
                if (GlobalSettings.getDemoMode()) {
                    String[] names = {"Violet", "Blue", "Green", "Yellow", "Red", "Far red"};
                    int[] colors = {new Color(127, 0, 255).getRGB(), Color.blue.getRGB(), Color.green.getRGB(),
                        Color.yellow.getRGB(), Color.red.getRGB(), Color.pink.getRGB()};
                    chNames.put(names[i]);
                    chColors.put(colors[i]);
                } else {
                    chNames.put(core.getCameraChannelName(i));
                    chColors.put(MMAcquisition.getMultiCamDefaultChannelColor(i, core.getCameraChannelName(i)));
                }
            }
            summary.put("ChNames", chNames);
            summary.put("ChColors", chColors);

            return summary;
        } catch (Exception ex) {
            ReportingUtils.showError("couldnt make summary metadata");
            ex.printStackTrace();
        }
        return null;
    }

    private interface HardwareCommand {

        void run() throws Exception;
    }

    public void setMultiAcqManager(MultipleAcquisitionManager multiAcqManager) {
        multiAcqManager_ = multiAcqManager;
    }
}
