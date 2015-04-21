/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package imageconstruction;

/**
 * Alternative to rank filter wrapper that wraps frame averaging
 */
public class FrameAverageWrapper extends FrameIntegrationMethod {

   
   public FrameAverageWrapper(int offset, int doubleWidth, int numFrames) {
      super(doubleWidth, offset, numFrames);
   }

   @Override
   public byte[] constructImage() {
      //sort all lists and construct final image
      byte[] averagedPixels = new byte[width_ * height_];
      for (int i = 0; i < averagedPixels.length; i++) {
         int sum = 0;
         for (int f = 0; f < numFrames_; f++) {
            sum += rawBuffers_.get(f).getUnwarpedImageValue(i % width_, i / width_);
         }
         averagedPixels[i] = (byte) (sum / numFrames_);
      }
      return averagedPixels;
   }
   
}
