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
package org.micromanager.magellan.acq;

import org.micromanager.magellan.coordinates.XYStagePosition;

/**
 * Information about the acquisition of a single image
 */
public class AcquisitionEvent {

   enum SpecialFlag {
      AcqusitionFinished
   };

   public Acquisition acquisition_;
   public int timeIndex_, sliceIndex_, channelIndex_, positionIndex_;
   public long miniumumStartTime_; //For pausing between time points
   public double zPosition_;
   public XYStagePosition xyPosition_;
   private SpecialFlag specialFlag_;

   public AcquisitionEvent(Acquisition acq) {
      acquisition_ = acq;
      miniumumStartTime_ = System.currentTimeMillis();
   }

   public AcquisitionEvent copy() {
      AcquisitionEvent e = new AcquisitionEvent(this.acquisition_);
      e.timeIndex_ = timeIndex_;
      e.channelIndex_ = channelIndex_;
      e.sliceIndex_ = sliceIndex_;
      e.positionIndex_ = positionIndex_;
      e.zPosition_ = zPosition_;
      e.xyPosition_ = xyPosition_;   
      e.miniumumStartTime_ = miniumumStartTime_;
      return e;
   }
   
   public static AcquisitionEvent createAcquisitionFinishedEvent(Acquisition acq) {
      AcquisitionEvent evt = new AcquisitionEvent(acq);
      evt.specialFlag_ = SpecialFlag.AcqusitionFinished;
      return evt;
   }

   public boolean isAcquisitionFinishedEvent() {
      return specialFlag_ == SpecialFlag.AcqusitionFinished;
   }

   @Override
   public String toString() {
      if (specialFlag_ == SpecialFlag.AcqusitionFinished) {
         return "Acq finished event";
      }

      return "P: " + positionIndex_ + "\tT: " + timeIndex_ + "\tZ: " + 
              sliceIndex_ + "\tC: " + channelIndex_ + "\tPos: " + xyPosition_;
   }
}
