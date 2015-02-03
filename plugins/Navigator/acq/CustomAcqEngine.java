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
import java.util.Calendar;
import java.util.concurrent.BlockingQueue;
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

/**
 *
 * @author henrypinkard
 */
public class CustomAcqEngine {

   private CMMCore core_;
   private AcquisitionEvent lastEvent_ = null;
   private String xyStageName_, zName_;
   private Thread acquisitionThread_;
   private ExploreAcquisition currentExploreAcq_;
   private FixedAreaAcquisition currentFixedAcq_;
   private MultipleAcquisitionManager multiAcqManager_;
   private volatile boolean idle_ = true;

   public CustomAcqEngine(CMMCore core) {
      core_ = core;
      updateDeviceNamesForAcquisition();   
      createAndStartAcquisitionThread();
   }
   
   private void createAndStartAcquisitionThread() {
      //create and start acquisition thread
      acquisitionThread_ = new Thread(new Runnable() {

         @Override
         public void run() {
            while (!Thread.interrupted()) {
               BlockingQueue<AcquisitionEvent> events;
               //Fixed acq take priority
               //TODO: might be an alternative way of doing this since the explore event queue is cleared when starting a 
               //fixed acqusiition anyway
               if (currentFixedAcq_ != null && !currentFixedAcq_.isPaused() && !currentFixedAcq_.isFinished()) {
                  events = currentFixedAcq_.getEventQueue();
               } else if (currentExploreAcq_ != null) {
                  events = currentExploreAcq_.getEventQueue();
               } else {
                  events = null;
               }
               if (events != null && events.size() > 0) {
                  idle_ = false;
                  runEvent(events.poll());
               } else {
                  idle_ = true;
                  try {
                     //wait for more events to acquire
                     Thread.sleep(2);
                  } catch (InterruptedException ex) {
                     Thread.currentThread().interrupt();
                  }
               }
            }
         }
      }, "Custom acquisition engine thread");
      acquisitionThread_.start();
   }

   public void setMultiAcqManager(MultipleAcquisitionManager multiAcqManager) {
      multiAcqManager_ = multiAcqManager;
   }
   
   private void updateDeviceNamesForAcquisition() {
      xyStageName_ = core_.getXYStageDevice();
      zName_ = core_.getFocusDevice();
   }

   public boolean isIdle() {
      return idle_;
   }
   
   public FixedAreaAcquisition runFixedAreaAcquisition(final FixedAreaAcquisitionSettings settings) {
      //check if current fixed acquisition is running
      if (currentFixedAcq_ != null && !currentFixedAcq_.isFinished()) {
         currentFixedAcq_.abort();
      }
      //clear explore acquisition events so that they dont unexpectedly restart after acquisition
      if (currentExploreAcq_ != null && !currentExploreAcq_.isFinished()) {
         currentExploreAcq_.getEventQueue().clear();
      }

      updateDeviceNamesForAcquisition();
      currentFixedAcq_ = new FixedAreaAcquisition(settings, multiAcqManager_, this);
      return currentFixedAcq_;
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
            updateDeviceNamesForAcquisition();
            currentExploreAcq_ = new ExploreAcquisition(settings);
         }
      }).start();
   }

   private void runEvent(AcquisitionEvent event) {
      if (event.acquisition_ == null) {
         //event will be null when fixed acquisitions run to compeletion in normal operation
         //signal to TaggedImageSink to finish saving thread and mark acquisition as finished
         currentFixedAcq_.addImage(new TaggedImage(null, null));
         currentFixedAcq_ = null;
         
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
               byte[] demoPix = DemoModeImageData.getPixelData(c, (int) event.xPosition_, (int) event.yPosition_,
                       (int) event.zPosition_, MDUtils.getWidth(img.tags), MDUtils.getHeight(img.tags));
               img = new TaggedImage(demoPix, img.tags);
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

   private void updateHardware(AcquisitionEvent event) {
      //compare to last event to see what needs to change
      //TODO: handling of errors when deviecs don't respond as expected
      if (lastEvent_ != null && lastEvent_.acquisition_ != event.acquisition_) {
         lastEvent_ = null; //update all hardware if switching to a new acquisition
      }

      //XY Stage
      if (lastEvent_ == null || event.positionIndex_ != lastEvent_.positionIndex_) {
         try {
            while (core_.deviceBusy(xyStageName_)) {
               try {
                  Thread.sleep(2);
               } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
               }
            }
            core_.setXYPosition(xyStageName_, event.xPosition_, event.yPosition_);
         } catch (Exception ex) {
            ReportingUtils.showError("Couldn't move XY stage");
            ex.printStackTrace();
         }
      }

      //Z stage
      if (lastEvent_ == null || event.sliceIndex_ != lastEvent_.sliceIndex_) {
         try {
            //TODO: add checks for device business??
            core_.setPosition(zName_, event.zPosition_);
         } catch (Exception e) {
            IJ.showMessage("Setting focus position failed");
            //TODO: try again a couple times?
            e.printStackTrace();
         }
      }

      //Channels
      if (lastEvent_ == null || event.channelIndex_ != lastEvent_.channelIndex_) {
         try {
//            core_.setConfig("Channel", event.channelIndex_ == 0 ? "DAPI" : "FITC");
         } catch (Exception ex) {
            ReportingUtils.showError("Couldn't change channel group");
         }
      }
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
         summary.put("PixelType", "GRAY8");
         summary.put("BitDepth", 8);
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
}
