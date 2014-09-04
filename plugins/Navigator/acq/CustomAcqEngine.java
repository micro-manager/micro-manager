package acq;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import ij.IJ;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.micromanager.api.MMTags;
import org.micromanager.utils.ReportingUtils;
import surfacesandregions.RegionManager;
import surfacesandregions.SurfaceManager;
import surfacesandregions.SurfaceOrRegionManager;

/**
 *
 * @author henrypinkard
 */
public class CustomAcqEngine {
   
   private CMMCore core_;
   private AcquisitionEvent lastEvent_ = null;
   private String xyStageName_, zName_;
   private Thread acquisitionThread_;
   protected BlockingQueue<AcquisitionEvent> events_;
   private SurfaceManager surfaceManager_;
   private RegionManager regionManager_;


   public CustomAcqEngine(CMMCore core, RegionManager rManager, SurfaceManager sManager) {
      core_ = core;
      regionManager_ = rManager;
      surfaceManager_ = sManager;
      updateDeviceNamesForAcquisition();
      events_ = new LinkedBlockingQueue<AcquisitionEvent>();
      //create and start acquisition thread
      acquisitionThread_ = new Thread(new Runnable() {

         @Override
         public void run() {
            while (!Thread.interrupted()) {
               if (events_.size() > 0) {
                  runEvent(events_.poll());
               } else {
                  try {
                     //wait for more events to acquire
                     Thread.sleep(5);
                  } catch (InterruptedException ex) {
                     Thread.currentThread().interrupt();
                  }
               }
            }
         }
      }, "Explorer acquisition thread");
      acquisitionThread_.start();
   }
   
   public SurfaceManager getSurfaceManager() {
      return surfaceManager_;
   }
   
   public RegionManager getRegionManager() {
      return regionManager_;
   }

   private void updateDeviceNamesForAcquisition() {
      xyStageName_ = core_.getXYStageDevice();
      zName_ = core_.getFocusDevice();
   }
   
   public void addEvent(AcquisitionEvent e) {
      events_.add(e);
   }

   public ExploreAcquisition newExploreAcquisition(final double zTop, final double zBottom, final double zStep,
           final int xOverlap, final int yOverlap, final String dir, final String name) {
      ExploreAcquisition exAcq = new ExploreAcquisition(CustomAcqEngine.this, zTop, zBottom, zStep);
      exAcq.initialize(xOverlap, yOverlap, dir, name);
      return exAcq;

   }

    public void runEvent(AcquisitionEvent event) {
        updateHardware(event);
        acquireImage(event);
    }

   private void acquireImage(AcquisitionEvent event) {
      try {
         //TODO: openShutter?
         core_.snapImage();
         //TODO: close shutter?
         
         int numCamChannels = (int) core_.getNumberOfCameraChannels();
          for (int c = 0; c < numCamChannels; c++) {
              //send to storage
              TaggedImage img = core_.getTaggedImage(c) ;
              //add tags
              long gridRow = event.acquisition_.getPositionManager().getGridRow(event.positionIndex_, 0);
              long gridCol = event.acquisition_.getPositionManager().getGridCol(event.positionIndex_, 0);
              img.tags.put(MMTags.Image.POS_NAME, "Grid_" + gridRow + "_" + gridCol);
              img.tags.put(MMTags.Image.POS_INDEX, event.positionIndex_);
              img.tags.put(MMTags.Image.SLICE_INDEX, event.sliceIndex_);
              img.tags.put(MMTags.Image.FRAME_INDEX, event.frameIndex_);
              img.tags.put(MMTags.Image.CHANNEL_INDEX, event.channelIndex_ * numCamChannels + c);
              img.tags.put(MMTags.Image.ZUM, event.zPosition_);
                           
              //TODO: add elapsed time tag
              event.acquisition_.addImage(img);
          }
           
      } catch (Exception ex) {
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
            core_.setPosition(zName_,event.zPosition_);
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
