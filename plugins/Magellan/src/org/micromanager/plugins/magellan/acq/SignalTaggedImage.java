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


/**
 * Class used for signaling to image saving thread
 */
public class SignalTaggedImage extends MagellanTaggedImage {
   
   public enum AcqSingal {AcqusitionFinsihed, TimepointFinished};
   
   private AcqSingal signal_;
   
   public SignalTaggedImage(AcqSingal s) {
      super(null, null);
      signal_ = s;
   }
   
   public static boolean isTimepointFinishedSignal(MagellanTaggedImage img) {
      return (img instanceof SignalTaggedImage && ((SignalTaggedImage) img).signal_ == AcqSingal.TimepointFinished);
   }
   
   public static boolean isAcquisitionFinsihedSignal(MagellanTaggedImage img) {
      return (img instanceof SignalTaggedImage && ((SignalTaggedImage) img).signal_ == AcqSingal.AcqusitionFinsihed);
   }
}
