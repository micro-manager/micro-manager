package acq;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import ij.IJ;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import main.Util;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.api.MMTags;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MDUtils;
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
   
   public void updateExploreSettings(double zTop, double zBottom) {
      exploreAcquisition_.setZLimits(zTop, zBottom);
   }
   
   private void updateDevicesForAcquisition() {
      xyStageName_ = core_.getXYStageDevice();
      zName_ = core_.getFocusDevice();
   }
   
   public void newExploreWindow(final double zTop, final double zBottom) {
      new Thread(new Runnable() {
         @Override
         public void run() {
            //TODO: check if existing open explore that should be finished?
            lastEvent_ = null;

            exploreAcquisition_ = new Acquisition(true, CustomAcqEngine.this, zTop, zBottom, 1);           

            //acquire first tile
            acquireTiles(0,0,0,0);


//            MultiResMultipageTiffStorage storage = new MultiResMultipageTiffStorage(
//                    "C:/Users/Henry/Desktop/MultiRes/", true, Acquisition.makeSummaryMD(2, 2), 0, 0);
//
//            ScriptInterface app = MMStudioMainFrame.getInstance();
//
//            for (int position = 0; position < 5; position++) {
//               for (int channel = 0; channel < 1; channel++) {
//                  for (int slice = 0; slice < 1; slice++) {
//                     try {
//                        core_.snapImage();
//                        TaggedImage img = core_.getTaggedImage();
//                        //set tags
//                        MDUtils.setFrameIndex(img.tags, 0);
//                        MDUtils.setChannelIndex(img.tags, channel);
//                        MDUtils.setSliceIndex(img.tags, slice);
//                        MDUtils.setPositionIndex(img.tags, position);
//                        storage.putImage(img);
//                     } catch (Exception ex) {
//                        ReportingUtils.showError("couldnt snap");
//                     }
//                  }
//               }
//            }
            

         }
      }).start();
   }

    public void runEvent(AcquisitionEvent event) {
        updateHardware(event);
        acquireImage(event);
    }

   public void acquireTiles(final int row1, final int col1, final int row2, final int col2) {
      try {
         //update positionList and get index
         int[] posIndices = null;
         boolean newPositionsAdded = false;
         try {
            int numPositions = exploreAcquisition_.getPositionManager().getNumPositions();
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
            //Add events for each channel, slice
            double zTop = exploreAcquisition_.getZTop(), zBottom = exploreAcquisition_.getZBottom(),
                    zStep = exploreAcquisition_.getZStep(), zAbsoluteTop = exploreAcquisition_.getZAbsoluteTop(),
                    zAbsoluteBottom = exploreAcquisition_.getZAbsoluteBottom();
            for (double z = zTop; z <= zBottom; z += zStep) {
               //move focus
               int sliceIndex = (int) ((z - zAbsoluteTop) / zStep);
               exploreAcquisition_.addEvent(new AcquisitionEvent(0, 0, sliceIndex, posIndices[i], z, x, y,
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
