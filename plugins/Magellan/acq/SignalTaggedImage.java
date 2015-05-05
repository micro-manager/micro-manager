/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import mmcorej.TaggedImage;

/**
 * Class used for signaling to image saving thread
 */
public class SignalTaggedImage extends TaggedImage {
   
   public enum AcqSingal {AcqusitionFinsihed, TimepointFinished};
   
   private AcqSingal signal_;
   
   public SignalTaggedImage(AcqSingal s) {
      super(null, null);
      signal_ = s;
   }
   
   public static boolean isTimepointFinishedSignal(TaggedImage img) {
      return (img instanceof SignalTaggedImage && ((SignalTaggedImage) img).signal_ == AcqSingal.TimepointFinished);
   }
   
   public static boolean isAcquisitionFinsihedSignal(TaggedImage img) {
      return (img instanceof SignalTaggedImage && ((SignalTaggedImage) img).signal_ == AcqSingal.AcqusitionFinsihed);
   }
}
