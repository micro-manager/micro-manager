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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JOptionPane;
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
import propsandcovariants.CovariantValue;

/**
 *
 * @author henrypinkard
 */
public class CustomAcqEngine {

   private static final int HARDWARE_ERROR_RETRIES = 4;
   private static final int DELWAY_BETWEEN_RETRIES_MS = 50;
   
   private CMMCore core_;
   private AcquisitionEvent lastEvent_ = null;
   private Thread acquisitionThread_;
   private ExploreAcquisition currentExploreAcq_;
   private ParallelAcquisitionGroup currentFixedAcqs_;
   private MultipleAcquisitionManager multiAcqManager_;
   private CustomAcqEngine singleton_;
   private ExecutorService acqExecutor_ = Executors.newSingleThreadExecutor();

   public CustomAcqEngine(CMMCore core) {
      singleton_ = this;
      core_ = core;
      createAndStartAcquisitionThread();
   }
   
   public CustomAcqEngine getInstance() {
      return singleton_;
   }
   
   public void runEvent(AcquisitionEvent event) {
      BlockingQueue<AcquisitionEvent> events;
      //Fixed acq take priority
      //TODO: might be an alternative way of doing this since the explore event queue is cleared when starting a 
      //fixed acqusiition anyway
      if (currentFixedAcqs_ != null && !currentFixedAcqs_.isPaused() && !currentFixedAcqs_.isFinished()) {
         events = currentFixedAcqs_.getEventQueue();
      } else if (currentExploreAcq_ != null) {
         events = currentExploreAcq_.getEventQueue();
      } else {
         events = null;
      }
      try {
         if (events != null && events.size() > 0) {
            runEvent(events.poll());
         } else {
            //wait for more events to acquire
            Thread.sleep(2);
         }
      } catch (InterruptedException ex) {
         break;
      }
   }
   
   private void createAndStartAcquisitionThread() {
      //create and start acquisition thread
      acquisitionThread_ = new Thread(new Runnable() {

         @Override
         public void run() {
            while (!Thread.interrupted()) {
               
            }
         }
      }, "Custom acquisition engine thread");
      acquisitionThread_.start();
   }

   public void setMultiAcqManager(MultipleAcquisitionManager multiAcqManager) {
      multiAcqManager_ = multiAcqManager;
   }
   
   public ParallelAcquisitionGroup runInterleavedAcquisitions(List<FixedAreaAcquisitionSettings> acqs) {
           //check if current fixed acquisition is running
      if (currentFixedAcqs_ != null && !currentFixedAcqs_.isFinished()) {
         //TODO: is there a check if user tries to start acq when acq in progress
         currentFixedAcqs_.abort();
      }
      //clear explore acquisition events so that they dont unexpectedly restart after acquisition
      if (currentExploreAcq_ != null && !currentExploreAcq_.isFinished()) {
         currentExploreAcq_.getEventQueue().clear();
      }

      currentFixedAcqs_ = new ParallelAcquisitionGroup(acqs, multiAcqManager_, this);
      return currentFixedAcqs_;
   }
   
   public void runFixedAreaAcquisition(final FixedAreaAcquisitionSettings settings) {
      ArrayList<FixedAreaAcquisitionSettings> list = new ArrayList<FixedAreaAcquisitionSettings>();
      list.add(settings);
      runInterleavedAcquisitions(list);
   }

   public void newExploreAcquisition(final ExploreAcqSettings settings) {
      new Thread(new Runnable() {

         @Override
         public void run() {
            if (currentExploreAcq_ != null && !currentExploreAcq_.isFinished()) {
               int result = JOptionPane.showConfirmDialog(null, "Finish exisiting explore acquisition?", "Finish Current Explore Acquisition", JOptionPane.OK_CANCEL_OPTION);
               if (result == JOptionPane.OK_OPTION) {
                  currentExploreAcq_.abort();
               } else {
                  return;
               }
            }
            currentExploreAcq_ = new ExploreAcquisition(settings);
         }
      }).start();
   }

   private void runEvent(AcquisitionEvent event) throws InterruptedException {
      if (event.isFinishingEvent()) {
         //event will be null when fixed acquisitions run to compeletion in normal operation
         //signal to TaggedImageSink to finish saving thread and mark acquisition as finished
         event.acquisition_.engineOutputQueue_.add(new TaggedImage(null, null));
      } else {
         updateHardware(event);
         acquireImage(event);
      }
   }

   private void acquireImage(AcquisitionEvent event) {
      try {
         core_.snapImage();
         //get elapsed time
         long currentTime = System.currentTimeMillis();
         if (event.acquisition_.getStartTime_ms() == -1) {
            //first image, initialize
            event.acquisition_.setStartTime_ms(currentTime);
         }

         int numCamChannels = (int) core_.getNumberOfCameraChannels();
         if (SettingsDialog.getDemoMode()) {
            numCamChannels = SettingsDialog.getDemoNumChannels();
         }
         for (int c = 0; c < numCamChannels; c++) {
            //send to storage
            TaggedImage img = core_.getTaggedImage(c);

            //substitute in dummy pixel data for demo mode
            if (SettingsDialog.getDemoMode()) {
               Object demoPix;
               if (core_.getBytesPerPixel() == 1) {
                  demoPix = DemoModeImageData.getBytePixelData(c, (int) event.xPosition_, (int) event.yPosition_,
                          (int) event.zPosition_, MDUtils.getWidth(img.tags), MDUtils.getHeight(img.tags));
               } else {
                  demoPix = DemoModeImageData.getShortPixelData(c, (int) event.xPosition_, (int) event.yPosition_,
                          (int) event.zPosition_, MDUtils.getWidth(img.tags), MDUtils.getHeight(img.tags));
               
               }img = new TaggedImage(demoPix, img.tags);
            }
            //add metadata
            addImageMetadata(img, event, numCamChannels, c, currentTime - event.acquisition_.getStartTime_ms());

            event.acquisition_.addImage(img);
         }

      } catch (Exception ex) {
         ex.printStackTrace();
         ReportingUtils.showError("Couldn't acquire Z stack");
      }
   }

   private void updateHardware(final AcquisitionEvent event) throws InterruptedException {
      //compare to last event to see what needs to change
      if (lastEvent_ != null && lastEvent_.acquisition_ != event.acquisition_) {
         lastEvent_ = null; //update all hardware if switching to a new acquisition
      }
      //Get the hardware specific to this acquisition
      final String xyStage = event.acquisition_.getXYStageName();
      final String zStage = event.acquisition_.getZStageName();
      
      /////////////////////////////XY Stage/////////////////////////////
      if (lastEvent_ == null || event.positionIndex_ != lastEvent_.positionIndex_) {
         //wait for it to not be busy (is this even needed??)
         loopHardwareCommandRetries(new HardwareCommand() {
            @Override
            public void run() throws Exception {
               while (core_.deviceBusy(xyStage)) {
                  Thread.sleep(2);
                  IJ.log(getCurrentDateAndTime() + ": waiting for XY stage to not be busy...");
               }
            }
         }, "waiting for XY stage to not be busy");
         //move to new position
         loopHardwareCommandRetries(new HardwareCommand() {
            @Override
            public void run() throws Exception {
               core_.setXYPosition(xyStage, event.xPosition_, event.yPosition_);
            }
         }, "moving XY stage");
         //wait for it to not be busy (is this even needed??)
         loopHardwareCommandRetries(new HardwareCommand() {
            @Override
            public void run() throws Exception {
               while (core_.deviceBusy(xyStage)) {
                  Thread.sleep(2);
                  IJ.log(getCurrentDateAndTime() + ": waiting for XY stage to not be busy...");
               }
            }
         }, "waiting for XY stage to not be busy");
      }

      /////////////////////////////Z stage/////////////////////////////
      if (lastEvent_ == null || event.sliceIndex_ != lastEvent_.sliceIndex_) {
         //wait for it to not be busy (is this even needed?)
         loopHardwareCommandRetries(new HardwareCommand() {
            @Override
            public void run() throws Exception {
               while (core_.deviceBusy(zStage)) {
                  Thread.sleep(2);
                  IJ.log(getCurrentDateAndTime() + ": waiting for Z stage to not be busy...");
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
                  IJ.log(getCurrentDateAndTime() + ": waiting for Z stage to not be busy...");
               }  
            }
         }, "waiting for Z stage to not be busy");
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
            IJ.log(getCurrentDateAndTime() + ": Problem "+commandName+ "\n Retry #" + i + " in " + DELWAY_BETWEEN_RETRIES_MS + " ms");
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

   private void addImageMetadata(TaggedImage img, AcquisitionEvent event,
           int numCamChannels, int camChannel, long elapsed_ms) throws JSONException {

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
      //Navigator specific tags
      img.tags.put("GridColumnIndex", gridCol);
      img.tags.put("GridRowIndex", gridRow);
   }

   public static JSONObject makeSummaryMD(Acquisition acq, String prefix) {
      try {

         //num channels is camera channels * acquisitionChannels
         int numChannels = SettingsDialog.getDemoMode() ? SettingsDialog.getDemoNumChannels() : acq.getNumChannels();

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
         summary.put("Width", core.getImageWidth());
         summary.put("Height", core.getImageHeight());
         summary.put("Prefix", prefix);
         JSONArray initialPosList = acq.createInitialPositionList();
         summary.put("InitialPositionList", initialPosList);
         summary.put("Positions", initialPosList.length());
         summary.put(MMTags.Summary.PIXSIZE, core.getPixelSizeUm());
         summary.put("z-step_um", acq.getZStep());
         summary.put("Interval_ms", acq instanceof FixedAreaAcquisition ? ((FixedAreaAcquisition) acq).getTimeInterval_ms() : 0);

         /////////////////////////////////////////////////////////////////
         ////Summary metadata specific to Navigator///////////////////////
         /////////////////////////////////////////////////////////////////

         //write pixel overlap into metadata
         summary.put("GridPixelOverlapX", SettingsDialog.getOverlapX());
         summary.put("GridPixelOverlapY", SettingsDialog.getOverlapY());
         summary.put("NavigatorExploreAcquisition", acq instanceof ExploreAcquisition);

         
         //TODO: make this legit
         JSONArray chNames = new JSONArray();
         JSONArray chColors = new JSONArray();
         for (int i = 0; i < numChannels; i++) {
            if (SettingsDialog.getDemoMode()) {
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
}
