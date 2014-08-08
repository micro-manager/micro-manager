/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import org.micromanager.utils.ReportingUtils;

/**
 * A single time point acquisition that can dynamically expand in X,Y, and Z
 * @author Henry
 */
public class ExploreAcquisition extends Acquisition {

   private volatile double zTop_, zBottom_;
   private final double zOrigin_;   
   private int lowestSliceIndex_ = 0, highestSliceIndex_ = 0;

   public ExploreAcquisition(CustomAcqEngine eng, double zTop, double zBottom, double zStep) {
      super(eng, zStep);
      zTop_ = zTop;
      zBottom_ = zBottom;
      zOrigin_ = zTop;
   }
   
   @Override
   public void initialize(int xOverlap, int yOverlap, String dir, String name) {
      super.initialize(xOverlap, yOverlap, dir, name);
      //acquire first tile
      acquireTiles(0,0,0,0);
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
               eng_.addEvent(new AcquisitionEvent(this, 0, 0, sliceIndex, posIndices[i], getZCoordinate(sliceIndex), x, y));
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

 
}
