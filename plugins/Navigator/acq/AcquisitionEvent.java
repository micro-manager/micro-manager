/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import java.awt.geom.Point2D;
import java.util.List;
import propsandcovariants.CovariantPairing;

/**
 * Information about the acquisition of a single image
 */
public class AcquisitionEvent implements Runnable{
       
   final public Acquisition acquisition_;
   final public int timeIndex_, sliceIndex_, channelIndex_, positionIndex_;
   final public double zPosition_, xPosition_, yPosition_;
   private boolean finishingEvent_ = false;
   final public List<CovariantPairing> covariants_;
   
   public AcquisitionEvent(Acquisition acq, int frameIndex, int channelIndex, int sliceIndex, int positionIndex, 
           double zPos, double xPos, double yPos, List<CovariantPairing> covariants) {
      timeIndex_ = frameIndex;
      sliceIndex_ = sliceIndex;
      channelIndex_ = channelIndex;
      positionIndex_ = positionIndex;    
      zPosition_ = zPos;
      xPosition_ = xPos;
      yPosition_ = yPos;
      acquisition_ = acq;
      covariants_ = covariants;
   }
   
   public static AcquisitionEvent createAcquisitionFinishedEvent(Acquisition acq) {
      AcquisitionEvent evt = new AcquisitionEvent(acq, 0, 0, 0, 0, 0, 0, 0, null);
      evt.finishingEvent_ = true;
      return evt;
   }
   
   public boolean isFinishingEvent() {
      return finishingEvent_;
   }
   
   @Override
   public String toString() {
      return "P: " + positionIndex_ + "\t\tT: " + timeIndex_ + "\t\tZ: " + sliceIndex_ + "\t\tC: " + channelIndex_; 
   }

   @Override
   public void run() {
      CustomAcqEngine.getInstance().runEvent(this);
   }
   
}
