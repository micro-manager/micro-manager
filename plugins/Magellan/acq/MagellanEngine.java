package acq;

/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
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
import bidc.JavaLayerImageConstructor;
import bidc.FrameIntegrationMethod;
import coordinates.AffineUtils;
import java.awt.geom.AffineTransform;
import main.Magellan;
import misc.GlobalSettings;
import misc.Log;
import misc.MD;
import mmcorej.CMMCore;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import propsandcovariants.CovariantPairing;

/**
 * Engine has a single thread executor, which sits idly waiting for new
 * acquisitions when not in use
 */
public class MagellanEngine {

    private static final int HARDWARE_ERROR_RETRIES = 6;
    private static final int DELWAY_BETWEEN_RETRIES_MS = 5;
    private static CMMCore core_;
    private AcquisitionEvent lastEvent_ = null;
    private ExploreAcquisition currentExploreAcq_;
    private ParallelAcquisitionGroup currentFixedAcqs_;
    private MultipleAcquisitionManager multiAcqManager_;
    private ExecutorService acqExecutor_;

    public MagellanEngine(CMMCore core) {
        core_ = core;
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
      if (settings.spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK && 
              !settings.fixedSurface_.getXYDevice().equals(core_.getXYStageDevice())) {
         Log.log("Error: XY device for surface does match XY device in MM core in " + settings.name_, true);
         throw new Exception();
      }
      if (settings.spaceMode_ == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK
              && (!settings.topSurface_.getXYDevice().equals(core_.getXYStageDevice()) || 
              !settings.bottomSurface_.getXYDevice().equals(core_.getXYStageDevice()))) {
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
           ex.printStackTrace();
           Log.log("Couldn't initialize explore acquisiton", true);
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
            //signal to TaggedImageSink to finish saving thread and mark acquisition as finished
           JavaLayerImageConstructor.getInstance().addSignalTaggedImage(event, new SignalTaggedImage(SignalTaggedImage.AcqSingal.AcqusitionFinsihed));
        } else if (event.isTimepointFinishedEvent()) {
            //signal to TaggedImageSink to let acqusition know that saving for the current time point has completed  
            JavaLayerImageConstructor.getInstance().addSignalTaggedImage(event, new SignalTaggedImage(SignalTaggedImage.AcqSingal.TimepointFinished));
        } else if (event.isAutofocusAdjustmentEvent()) {
            setAutofocusPosition(event.autofocusZName_, event.autofocusPosition_);
        } else {
            updateHardware(event);
            acquireImage(event);
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
                JavaLayerImageConstructor.getInstance().getTaggedImagesAndAddToAcq(event, currentTime);
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

    //from MM website, a potential way to speed up acq:
    //To further streamline synchronization tasks you can define all devices which must be non-busy before the image is acquired.
// The following devices must stop moving before the image is acquired
//core.assignImageSynchro("X");
//core.assignImageSynchro("Y");
//core.assignImageSynchro("Z");
//core.assignImageSynchro("Emission");
//
//// Set all the positions. For some of the devices it will take a while
//// to stop moving
//core.SetPosition("X", 1230);
//core.setPosition("Y", 330);
//core.SetPosition("Z", 8000);
//core.setState("Emission", 3);
//
//// Just go ahead and snap an image. The system will automatically wait
//// for all of the above devices to stop moving before the
//// image is acquired
//core.snapImage();
    
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
                    if (GlobalSettings.getInstance().isBIDCTwoPhoton()) {
                        //hysteresis correction: move to to the left (screen) when starting
                        //column 0 or every time on explore
                        //left on the screen is a higher y coordinate on the gen3 stage
                        //up on the screen is lower x coordinate
                        //always approach from the top left
                        double hystersisDistance = 70;
                        if (lastEvent_ == null || 
                                event.xyPosition_.getCenter().x  - lastEvent_.xyPosition_.getCenter().x < -hystersisDistance || //move to the left
                                event.xyPosition_.getCenter().y - lastEvent_.xyPosition_.getCenter().y > hystersisDistance ) { //move up
                            //70 is half a hi res field
                            core_.setXYPosition(xyStage, event.xyPosition_.getCenter().x - hystersisDistance, 
                                    event.xyPosition_.getCenter().y + hystersisDistance);
                        }
                    }
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
//            Log.log("Couldn't change channel group");
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

    public static void addImageMetadata(JSONObject tags, AcquisitionEvent event,
            int numCamChannels, int camChannel, long elapsed_ms,int exposure) {
        try {
            //add tags
            long gridRow = event.acquisition_.getStorage().getGridRow(event.positionIndex_, 0);
            long gridCol = event.acquisition_.getStorage().getGridCol(event.positionIndex_, 0);
            tags.put(MD.POS_NAME, "Grid_" + gridRow + "_" + gridCol);
            tags.put(MD.POS_INDEX, event.positionIndex_);
            tags.put(MD.SLICE_INDEX, event.sliceIndex_);
            tags.put(MD.SLICE, event.sliceIndex_);
            tags.put(MD.FRAME_INDEX, event.timeIndex_);
            tags.put(MD.FRAME, event.timeIndex_);
            tags.put(MD.CHANNEL_INDEX, event.channelIndex_ * numCamChannels + camChannel);
            tags.put(MD.ZUM, event.zPosition_);
            tags.put(MD.ELAPSED_TIME_MS, elapsed_ms);
            tags.put(MD.TIME, (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss -")).format(Calendar.getInstance().getTime()));
            //Magellan specific tags
            if (GlobalSettings.getInstance().isBIDCTwoPhoton()) {
              tags.put("ExposureRawFrames", exposure);
           }
            tags.put("GridColumnIndex", gridCol);
            tags.put("GridRowIndex", gridRow);
            tags.put("StagePositionX", event.xyPosition_.getCenter().x);
            tags.put("StagePositionY", event.xyPosition_.getCenter().y);
        } catch (JSONException e) {
            Log.log("Problem adding image tags", true);
            Log.log(e);
            e.printStackTrace();
        }
    }

    public static JSONObject makeSummaryMD(Acquisition acq, String prefix) {
        try {

            //num channels is camera channels * acquisitionChannels
            int numChannels = GlobalSettings.getInstance().getDemoMode() ? 6 : acq.getNumChannels();

            CMMCore core = Magellan.getCore();
            JSONObject summary = new JSONObject();
            summary.put(MD.NUM_CHANNELS, numChannels);
            summary.put(MD.ZC_ORDER, false);
            summary.put(MD.PIX_TYPE, GlobalSettings.getInstance().isBIDCTwoPhoton() ? 
                    (acq.getFilterType() == FrameIntegrationMethod.FRAME_SUMMATION ? MD.PIX_TYPE_GRAY16 : MD.PIX_TYPE_GRAY8) :
            core_.getImageBitDepth() > 8 ? MD.PIX_TYPE_GRAY16 : MD.PIX_TYPE_GRAY8);
            summary.put(MD.BIT_DEPTH, GlobalSettings.getInstance().isBIDCTwoPhoton() ? 
                    (acq.getFilterType() == FrameIntegrationMethod.FRAME_SUMMATION ? 16 : 8) :
                    core_.getImageBitDepth() );
            summary.put(MD.WIDTH, JavaLayerImageConstructor.getInstance().getImageWidth());
            summary.put(MD.HEIGHT, JavaLayerImageConstructor.getInstance().getImageHeight());
            summary.put(MD.SAVING_PREFIX, prefix);
            JSONArray initialPosList = acq.createInitialPositionList();
            summary.put(MD.INITIAL_POS_LIST, initialPosList);
            summary.put(MD.PIX_SIZE, core.getPixelSizeUm());
            summary.put(MD.Z_STEP_UM, acq.getZStep());
            summary.put(MD.TIMELAPSE_INTERVAL, acq instanceof FixedAreaAcquisition ? ((FixedAreaAcquisition) acq).getTimeInterval_ms() : 0);
            //write pixel overlap into metadata
            summary.put(MD.OVERLAP_X, acq.getOverlapX());
            summary.put(MD.OVERLAP_Y, acq.getOverlapY());
            summary.put(MD.EXPLORE_ACQ, acq instanceof ExploreAcquisition);

            //affine transform
            AffineTransform at = AffineUtils.getAffineTransform(core.getCurrentPixelSizeConfig(), 0, 0);
            summary.put(MD.AFFINE_TRANSFORM, AffineUtils.transformToString(at));
            
            JSONArray chNames = new JSONArray();
            JSONArray chColors = new JSONArray();
            String[] names = acq.getChannelNames();   
            Color[] colors = acq.getChannelColors();
            for (int i = 0; i < numChannels; i++) {  
               chNames.put(names[i]);
               chColors.put(colors[i].getRGB());
            }
            summary.put(MD.CHANNEL_NAMES, chNames);
            summary.put(MD.CHANNEL_COLORS, chColors);

            return summary;
        } catch (Exception ex) {
            Log.log("couldnt make summary metadata");
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
