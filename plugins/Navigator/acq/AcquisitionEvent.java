/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import java.awt.geom.Point2D;

/**
 * 
 * @author Henry
 */
public class AcquisitionEvent {
   
   final public int frameIndex_, sliceIndex_, channelIndex_, positionIndex_;
   final public double zPosition_, xPosition_, yPosition_;
   final public boolean posListToMD_;
   
   public AcquisitionEvent(int frameIndex, int channelIndex, int sliceIndex, int positionIndex, 
           double zPos, double xPos, double yPos, boolean writePosListToMetadata) {
      frameIndex_ = frameIndex;
      sliceIndex_ = sliceIndex;
      channelIndex_ = channelIndex;
      positionIndex_ = positionIndex;    
      zPosition_ = zPos;
      xPosition_ = xPos;
      yPosition_ = yPos;
      posListToMD_ = writePosListToMetadata;

   }
   
}
