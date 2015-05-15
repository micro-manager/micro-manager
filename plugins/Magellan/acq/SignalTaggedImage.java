/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;


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
