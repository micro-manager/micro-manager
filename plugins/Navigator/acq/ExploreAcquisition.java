/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import coordinates.XYStagePosition;
import java.awt.geom.Point2D;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.utils.ReportingUtils;

/**
 * A single time point acquisition that can dynamically expand in X,Y, and Z
 * @author Henry
 */
public class ExploreAcquisition extends Acquisition {

   private volatile double zTop_, zBottom_;
   private final double zOrigin_;   
   private int lowestSliceIndex_ = 0, highestSliceIndex_ = 0;

   public ExploreAcquisition(ExploreAcqSettings settings) {
      super(settings.zStep_);
      zTop_ = settings.zTop_;
      zBottom_ = settings.zBottom_;
      zOrigin_ = zTop_;
      initialize(settings.dir_, settings.name_);
   }
   
   public void abort() {
      //abort all pending events
      events_.clear();
      engineOutputQueue_.clear();
      engineOutputQueue_.add(new TaggedImage(null, null));
      imageSink_.waitToDie();
      //image sink will call finish when it completes
   }

   public void finish() {
      finished_ = true;
   }

   public void acquireTiles(int row1, int col1, int row2, int col2) {
      try {
         //update positionList and get index
         int[] posIndices = null;
         try {
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
            posIndices = getPositionManager().getPositionIndices(newPositionRows, newPositionCols);
         } catch (Exception e) {
            e.printStackTrace();
            ReportingUtils.showError("Problem with position metadata: couldn't add tile");
            return;
         }

         //create set of hardware instructions for an acquisition event
         for (int i = 0; i < posIndices.length; i++) {
            //get x and y coordinates of current position
            double x = getPositionManager().getXCoordinate(posIndices[i]);
            double y = getPositionManager().getYCoordinate(posIndices[i]);
            //update lowest slice for the benefit of the zScrollbar in the viewer
            updateLowestAndHighestSlices();
            //Add events for each channel, slice            
            for (int sliceIndex = getMinSliceIndex(); sliceIndex <= getMaxSliceIndex(); sliceIndex++) {
               addEvent(new AcquisitionEvent(this, 0, 0, sliceIndex, posIndices[i], getZCoordinate(sliceIndex), x, y));
            }
         }
      } catch (Exception e) {
         e.printStackTrace();
         ReportingUtils.showError("Couldn't create acquistion events");
      }
   }
   
   
   @Override
   public double getZCoordinateOfSlice(int displaySliceIndex, int displayFrameIndex) {
      //No frames in explorer acquisition
      int sliceIndex = (displaySliceIndex - 1 + lowestSliceIndex_);
      return zOrigin_ + zStep_ * sliceIndex;
   }

   @Override
   public int getDisplaySliceIndexFromZCoordinate(double z, int displayFrameIndex) {
      return (int) Math.round((z - zOrigin_) / zStep_) - lowestSliceIndex_ + 1;
   }



   /**
    * return the slice index of the lowest slice seen in this acquisition
    *
    * @return
    */
   public int getLowestSliceIndex() {
      return lowestSliceIndex_;
   }

   public int getHighestSliceIndex() {
      return highestSliceIndex_;
   }

   public void updateLowestAndHighestSlices() {
      //keep track of this for the purposes of the viewer
      lowestSliceIndex_ = Math.min(lowestSliceIndex_, getMinSliceIndex());
      highestSliceIndex_ = Math.max(highestSliceIndex_, getMaxSliceIndex());
   }

   /**
    * get min slice index for current settings in explore acquisition
    *
    * @return
    */
   public int getMinSliceIndex() {
      return (int) ((zTop_ - zOrigin_) / zStep_);
   }

   /**
    * get max slice index for current settings in explore acquisition
    *
    * @return
    */
   public int getMaxSliceIndex() {
      return (int) ((zBottom_ - zOrigin_) / zStep_);
   }

   /**
    * get z coordinate for slice position
    * @return 
    */
   public double getZCoordinate(int sliceIndex) {
      return zOrigin_ + zStep_ * sliceIndex;
   }

   public void setZLimits(double zTop, double zBottom) {
      //Convention: z top should always be lower than zBottom
      zBottom_ = Math.max(zTop, zBottom);
      zTop_ = Math.min(zTop, zBottom);
   }
   
   public double getZTop() {
      return zTop_;
   }
   
   public double getZBottom() {
      return zBottom_;
   }
   
   @Override
   protected JSONArray createInitialPositionList() {
      try {
         //make intitial position list, with current position and 0,0 as coordinates
         CMMCore core = MMStudio.getInstance().getCore();
         JSONArray pList = new JSONArray();
         //create first position based on current XYStage position
         pList.put(new XYStagePosition("Grid_0_0", new Point2D.Double(core.getXPosition(core.getXYStageDevice()), core.getYPosition(core.getXYStageDevice())),
                 0, 0, 0, 0, pixelSizeConfig_).getMMPosition());
         return pList;
      } catch (Exception e) {
         ReportingUtils.showError("Couldn't create initial position list");
         return null;
      }
   }

 
}
