package acq;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import ij.IJ;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import main.Util;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONObject;
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
            acquireTiles(1, 1, 1, 1);
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
            int numPositions = exploreAcquisition_.getPositionList().length();
            //create metadata for new positions
            JSONObject[] newPositions = new JSONObject[(row2 - row1 + 1) * (col2 - col1 + 1)];
            for (int r = row1; r <= row2; r++) {
               for (int c = col1; c <= col2; c++) {
                  int i = (r - row1) + (1 + row2 - row1) * (c - col1);
                  newPositions[i] = createPositionMetadata(r, c,
                          exploreAcquisition_.getPositionList().getJSONObject(0));
               }
            }
            posIndices = exploreAcquisition_.addPositionsIfNeeded(newPositions);
            newPositionsAdded = numPositions != exploreAcquisition_.getPositionList().length();
         } catch (Exception e) {
            e.printStackTrace();
            ReportingUtils.showError("Problem with position metadata: couldn't add tile");
            return;
         }

         //create set of hardware instructions for an acquisition event
         for (int i = 0; i < posIndices.length; i++) {
            //get x and y coordinates of current position
            double x = exploreAcquisition_.getXPosition(posIndices[i]);
            double y = exploreAcquisition_.getYPosition(posIndices[i]);
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
                  img.tags.put("PositionList", exploreAcquisition_.getPositionList());
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
//         try {
//            core_.setConfig("Channel", event.channelIndex_ == 0 ? "DAPI" : "FITC");
//         } catch (Exception ex) {
//            ReportingUtils.showError("Couldn't change channel group");
//         }
      }
   }

   private JSONObject createPositionMetadata(int row, int col, JSONObject exisitingPosition) {
      try {
         JSONArray xy = new JSONArray();
         //TODO: change if overlap added
         int xOverlap = 0;
         int yOverlap = 0;         
         Point2D.Double stageCoords = Util.getStageCoordinatesBasedOnExistingPosition(row, col, exisitingPosition, xOverlap, yOverlap);
         
         JSONObject coords = new JSONObject();
         xy.put(stageCoords.x);
         xy.put(stageCoords.y);
         coords.put(core_.getXYStageDevice(), xy);
         JSONObject pos = new JSONObject();
         pos.put("DeviceCoordinatesUm", coords);
         pos.put("GridColumnIndex", col);
         pos.put("GridRowIndex", row);
         return pos;
      } catch (Exception e) {
         ReportingUtils.showError("Couldn't create XY position");
         return null;
      }
   }


}
