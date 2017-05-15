///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.plugins.magellan.acq;

import org.micromanager.plugins.magellan.coordinates.XYStagePosition;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.micromanager.plugins.magellan.propsandcovariants.CovariantPairing;
import org.micromanager.plugins.magellan.propsandcovariants.SurfaceData;

/**
 * Information about the acquisition of a single image
 */
public class AcquisitionEvent  {

   enum SpecialFlag {TimepointFinished, AcqusitionFinished, SwappingQueues, EngineTaskFinished, AutofocusAdjustment};
   
   final public Acquisition acquisition_;
   final public int timeIndex_, sliceIndex_, channelIndex_, positionIndex_;
   final public double zPosition_;
   final public XYStagePosition xyPosition_;
   private SpecialFlag specialFlag_;
   final public List<CovariantPairing> covariants_;
   public String autofocusZName_;
   public double autofocusPosition_;
   public byte[] nnEOM1Settings_, nnEOM2Settings_;
   
   
   public AcquisitionEvent(Acquisition acq, int frameIndex, int channelIndex, int sliceIndex, int positionIndex, 
            double zPos, XYStagePosition xyPos, List<CovariantPairing> covariants) throws InterruptedException {
      timeIndex_ = frameIndex;
      sliceIndex_ = sliceIndex;
      channelIndex_ = channelIndex;
      positionIndex_ = positionIndex;    
      zPosition_ = zPos;
      acquisition_ = acq;
      xyPosition_ = xyPos;
      covariants_ = covariants;
      //check if Neural net is one of the covariants and if so precompite value
      for (CovariantPairing p : covariants_) {
         if (p.getIndependentCovariant() instanceof SurfaceData
                 && ((SurfaceData) p.getIndependentCovariant()).isNeuralNetControl()) {
            //precompute neural net values
             nnEOM1Settings_ = ((SurfaceData) p.getIndependentCovariant()).getNN(0).getExcitations(xyPosition_,zPosition_, 
                  ((SurfaceData) p.getIndependentCovariant()).getSurface());
             nnEOM2Settings_ = ((SurfaceData) p.getIndependentCovariant()).getNN(1).getExcitations(xyPosition_,zPosition_, 
                  ((SurfaceData) p.getIndependentCovariant()).getSurface());
         }
      }
   }
   
   public static AcquisitionEvent createAutofocusEvent(String zName, double pos )  {   
       try {
           AcquisitionEvent evt = new AcquisitionEvent(null, 0, 0, 0, 0, 0, null, null);
           evt.autofocusZName_ = zName;
           evt.autofocusPosition_ = pos;
           evt.specialFlag_ = SpecialFlag.AutofocusAdjustment;
           return evt;
       } catch (InterruptedException ex) {
          //Shouldn't ever happen
           return null;
       }
   }
   
    public boolean isAutofocusAdjustmentEvent() {
      return specialFlag_ == SpecialFlag.AutofocusAdjustment;
   }
   
   public static AcquisitionEvent createEngineTaskFinishedEvent()  {
       try {
           AcquisitionEvent evt = new AcquisitionEvent(null, 0, 0,  0, 0, 0, null, null);
           evt.specialFlag_ = SpecialFlag.EngineTaskFinished;
           return evt;
       } catch (InterruptedException ex) {
           //Shouldn't ever happen
           return null;
       }
   }
   
   public boolean isEngineTaskFinishedEvent() {
      return specialFlag_ == SpecialFlag.EngineTaskFinished;
   }
   
   public static AcquisitionEvent createTimepointFinishedEvent(Acquisition acq)   {
       try {
           AcquisitionEvent evt = new AcquisitionEvent(acq, 0, 0,  0, 0, 0, null, null);
           evt.specialFlag_ = SpecialFlag.TimepointFinished;
           return evt;
       } catch (InterruptedException ex) {
            //Shouldn't ever happen
           return null;
       }
   }
   
   public boolean isTimepointFinishedEvent() {
      return specialFlag_ == SpecialFlag.TimepointFinished;
   }
   
   public static AcquisitionEvent createReQuerieEventQueueEvent() {
       try {
           AcquisitionEvent evt = new AcquisitionEvent(null, 0, 0, 0, 0, 0, null, null);
           evt.specialFlag_ = SpecialFlag.SwappingQueues;
           return evt;
       } catch (InterruptedException ex) {
           //Shouldn't ever happen
           return null;
       }
   }
   
   public boolean isReQueryEvent() {
      return specialFlag_ == SpecialFlag.SwappingQueues;
   }
   
   public static AcquisitionEvent createAcquisitionFinishedEvent(Acquisition acq)  {
       try {
           AcquisitionEvent evt = new AcquisitionEvent(acq, 0, 0, 0, 0, 0, null, null);
           evt.specialFlag_ = SpecialFlag.AcqusitionFinished;
           return evt;
       } catch (InterruptedException ex) {
           //Shouldn't ever happen
           return null;
       }
   }
   
   public boolean isAcquisitionFinishedEvent() {
      return specialFlag_ == SpecialFlag.AcqusitionFinished;
   }
   
   @Override
   public String toString() {
      if (specialFlag_ == SpecialFlag.AcqusitionFinished) {
          return "Acq finished event";
      } else if (specialFlag_ == SpecialFlag.AutofocusAdjustment) {       
          return "Autofocus event";
      } else if (specialFlag_ == SpecialFlag.EngineTaskFinished) {       
          return "Engine task finished event";
      } else if (specialFlag_ == SpecialFlag.SwappingQueues) {
          return "Swapping queues event";
      } else if (specialFlag_ == SpecialFlag.TimepointFinished) {
          return "Timepoint finished event";
      }
       
       return "P: " + positionIndex_ + "\t\tT: " + timeIndex_ + "\t\tZ: " + sliceIndex_ + "\t\tC: " + channelIndex_; 
   }
}
