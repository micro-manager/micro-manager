package acq;

/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
import demo.DemoModeImageData;
import gui.SettingsDialog;
import ij.IJ;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.micromanager.api.MMTags;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;
import surfacesandregions.RegionManager;
import surfacesandregions.SurfaceManager;

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


   public CustomAcqEngine(CMMCore core) {
      core_ = core;
      updateDeviceNamesForAcquisition();
      //create and start acquisition thread
      acquisitionThread_ = new Thread(new Runnable() {

         @Override
         public void run() {
            while (!Thread.interrupted()) {
               BlockingQueue<AcquisitionEvent> events;
               //Fixed acq take priority
               if (currentFixedAcq_ != null && !currentFixedAcq_.isPaused() && !currentFixedAcq_.isFinished()) {
                  events = currentFixedAcq_.getEventQueue();
               } else if (currentExploreAcq_ != null  ) {
                  events = currentExploreAcq_.getEventQueue();
               } else {
                  events = null;
               }                       
               if (events != null && events.size() > 0) {
                  runEvent(events.poll());
               } else {
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

   private void updateDeviceNamesForAcquisition() {
      xyStageName_ = core_.getXYStageDevice();
      zName_ = core_.getFocusDevice();
   }

   public void runFixedAreaAcquisition(final FixedAreaAcquisitionSettings settings) {
      new Thread(new Runnable() {
         @Override
         public void run() {
            //check if current fixed acquisition is running
            if (currentFixedAcq_ != null && !currentFixedAcq_.isFinished()) {
               currentFixedAcq_.abort();
            }
            //clear explore acquisition events so that they dont unexpectedly restart after acquisition
            if (currentExploreAcq_ != null && !currentExploreAcq_.isFinished()) {
               currentExploreAcq_.getEventQueue().clear();
            }
            
            currentFixedAcq_ = new FixedAreaAcquisition(settings);
         }
      }).start();
   }

   public void newExploreAcquisition(final ExploreAcqSettings settings) {
      new Thread(new Runnable() {

         @Override
         public void run() {
            currentExploreAcq_ = new ExploreAcquisition(settings);
         }
      }).start();
   }

   private void runEvent(AcquisitionEvent event) {
      if (event == null) {
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
         //TODO: openShutter? or autoshutter
         core_.snapImage();
         //TODO: close shutter?


         int numCamChannels = (int) core_.getNumberOfCameraChannels();
         if (SettingsDialog.getDemoMode()) {
            numCamChannels = SettingsDialog.getDemoNumChannels();
         }
         for (int c = 0; c < numCamChannels; c++) {
            //send to storage
            TaggedImage img = core_.getTaggedImage(c);
            
            //substitute in dummy pixel data for demo mode
            if (SettingsDialog.getDemoMode()) {
               byte[] demoPix = DemoModeImageData.getPixelData(c, (int) event.xPosition_, (int)event.yPosition_,
                       (int) event.zPosition_, MDUtils.getWidth(img.tags), MDUtils.getHeight(img.tags));               
               img = new TaggedImage(demoPix,img.tags);
            }           
            //add tags
            long gridRow = event.acquisition_.getPositionManager().getGridRow(event.positionIndex_, 0);
            long gridCol = event.acquisition_.getPositionManager().getGridCol(event.positionIndex_, 0);
            img.tags.put(MMTags.Image.POS_NAME, "Grid_" + gridRow + "_" + gridCol);
            img.tags.put(MMTags.Image.POS_INDEX, event.positionIndex_);
            img.tags.put(MMTags.Image.SLICE_INDEX, event.sliceIndex_);
            img.tags.put(MMTags.Image.SLICE, event.sliceIndex_);
            img.tags.put(MMTags.Image.FRAME_INDEX, event.timeIndex_);
            img.tags.put(MMTags.Image.FRAME, event.timeIndex_);
            img.tags.put(MMTags.Image.CHANNEL_INDEX, event.channelIndex_ * numCamChannels + c);
            img.tags.put(MMTags.Image.ZUM, event.zPosition_);

            //TODO: add elapsed time tag
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
}
