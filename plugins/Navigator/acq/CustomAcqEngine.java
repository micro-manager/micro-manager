package acq;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import ij.IJ;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.micromanager.api.MMTags;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author henrypinkard
 */
public class CustomAcqEngine {
   
   private CMMCore core_;
   private AcquisitionEvent lastEvent_;
   private Acquisition exploreAcquisition_;
   private String xyStageName_, zName_;

   public CustomAcqEngine(CMMCore core) {
      core_ = core;
      updateDevicesForAcquisition();
   }
   
   public Acquisition getCurrentExploreAcquisition() {
      return exploreAcquisition_;
   }
   
   public void updateExploreSettings(double zTop, double zBottom) {
      exploreAcquisition_.setZLimits(zTop, zBottom);
   }
   
   private void updateDevicesForAcquisition() {
      xyStageName_ = core_.getXYStageDevice();
      zName_ = core_.getFocusDevice();
   }
   
   public void newExploreWindow(final double zTop, final double zBottom, final double zStep, 
           final int xOverlap, final int yOverlap, final String dir, final String name) {
      new Thread(new Runnable() {
         @Override
         public void run() {
            //TODO: check if existing open explore that should be finished?
            lastEvent_ = null;

            exploreAcquisition_ = new Acquisition(true, CustomAcqEngine.this, zTop, zBottom, zStep);   
            exploreAcquisition_.initialize(xOverlap, yOverlap, dir, name);

            //acquire first tile
            acquireTiles(0,0,0,0);

         }
      }).start();
   }

    public void runEvent(AcquisitionEvent event) {
        updateHardware(event);
        acquireImage(event);
    }

   public void acquireTiles(int row1, int col1, int row2, int col2) {
      try {
         //update positionList and get index
         int[] posIndices = null;
         boolean newPositionsAdded = false;
         try {
            int numPositions = exploreAcquisition_.getPositionManager().getNumPositions();
            //order tile indices properly
            if (row1 > row2) {
               int temp = row1;
               row1 = row2;
               row2 = temp;
            }
            if (col1 > col2) {
               int temp = col1;
               col1 = col2;
               col2 = temp;
            }
            
            //Get position Indices from manager based on row and column
            //it will create new metadata as needed
            int[] newPositionRows = new int[(row2 - row1 + 1) * (col2 - col1 + 1)];
            int[] newPositionCols = new int[(row2 - row1 + 1) * (col2 - col1 + 1)];
            for (int r = row1; r <= row2; r++) {
               for (int c = col1; c <= col2; c++) {
                  int i = (r - row1) + (1 + row2 - row1) * (c - col1);
                  newPositionRows[i] = r;
                  newPositionCols[i] = c;
               }
            }
            
            posIndices = exploreAcquisition_.getPositionManager().getPositionIndices(newPositionRows, newPositionCols);
            
            newPositionsAdded = numPositions != exploreAcquisition_.getPositionManager().getNumPositions();
         } catch (Exception e) {
            e.printStackTrace();
            ReportingUtils.showError("Problem with position metadata: couldn't add tile");
            return;
         }

         //create set of hardware instructions for an acquisition event
         for (int i = 0; i < posIndices.length; i++) {
            //get x and y coordinates of current position
            double x = exploreAcquisition_.getPositionManager().getXCoordinate(posIndices[i]);
            double y = exploreAcquisition_.getPositionManager().getYCoordinate(posIndices[i]);
            //update lowest slice for the benefit of the zScrollbar in the viewer
            exploreAcquisition_.updateLowestAndHighestSlices();
            //Add events for each channel, slice            
            for (int sliceIndex = exploreAcquisition_.getMinSliceIndex(); sliceIndex <= exploreAcquisition_.getMaxSliceIndex(); sliceIndex++) {
               exploreAcquisition_.addEvent(new AcquisitionEvent(0, 0, sliceIndex, posIndices[i], exploreAcquisition_.getZCoordinate(sliceIndex), x, y,
                       newPositionsAdded));
            }
         }
      } catch (Exception e) {
         e.printStackTrace();
         ReportingUtils.showError("Couldn't create acquistion events");
      }
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
              long gridRow = exploreAcquisition_.getPositionManager().getGridRow(event.positionIndex_, 0);
              long gridCol = exploreAcquisition_.getPositionManager().getGridCol(event.positionIndex_, 0);
              img.tags.put(MMTags.Image.POS_NAME, "Grid_" + gridRow + "_" + gridCol);
              img.tags.put(MMTags.Image.POS_INDEX, event.positionIndex_);
              img.tags.put(MMTags.Image.SLICE_INDEX, event.sliceIndex_);
              img.tags.put(MMTags.Image.FRAME_INDEX, event.frameIndex_);
              img.tags.put(MMTags.Image.CHANNEL_INDEX, event.channelIndex_ * numCamChannels + c);
              img.tags.put(MMTags.Image.ZUM, event.zPosition_);
              if (event.posListToMD_) {
                  //write position list to metadata
                  img.tags.put("PositionList", exploreAcquisition_.getPositionManager().getSerializedPositionList());
              }
                           
              //TODO: add elapsed time tag
              exploreAcquisition_.addImage(img);
          }
           
      } catch (Exception ex) {
         ReportingUtils.showError("Couldn't acquire Z stack");
      }
   }
   
   private void updateHardware(AcquisitionEvent event) {
      //compare to last event to see what needs to change
      //TODO: handling of errors when deviecs don't respond as expected
      
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
