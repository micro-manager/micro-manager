/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package bidc;

/**
 *
 * @author bidc
 */
public class FrameSummationWrapper extends FrameIntegrationMethod {
    
   public FrameSummationWrapper(int offset, int doubleWidth, int numFrames) {
      super(doubleWidth, offset, numFrames);
   }

   @Override
   public Object constructImage() {
      //sort all lists and construct final image
      short[] summedPixels = new short[width_ * height_];
      for (int i = 0; i < summedPixels.length; i++) {
         int sum = 0;
         for (int f = 0; f < numFrames_; f++) {
            summedPixels[i] += rawBuffers_.get(f).getUnwarpedImageValue(i % width_, i / width_);
         }
      }
      return summedPixels;
   }
    
}
