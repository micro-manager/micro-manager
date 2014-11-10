
package org.micromanager.multichannelshading;

import ij.ImagePlus;
import ij.process.ImageStatistics;

/**
 *
 * @author nico
 */
public class SimpleFloatImage {
   private final Float[] normalizedPixels_;
   private final int height_;
   private final int width_;
   
   public SimpleFloatImage (ImagePlus flatField) {
      if (flatField != null) {       
         ImageStatistics flatFieldStats = ImageStatistics.getStatistics(flatField.getProcessor(),
                 ImageStatistics.MEAN + ImageStatistics.MIN_MAX, null);
         width_ = flatField.getWidth();
         height_ = flatField.getHeight();
         normalizedPixels_ = new Float[width_ * height_];
         float mean = (float) flatFieldStats.mean;         
        
         /* store images as reciprocals to speed up flat fielding later
            normalized image is normalized so mean = 1
          */
         int size = width_ * height_;
         for (int i = 0; i < size; i++) {
            normalizedPixels_[i]
                    = mean / flatField.getProcessor().getf(i);
         }
      } else {
         // keep compiler happy, better to throw exception?
         normalizedPixels_ = null;
         height_ = 0;
         width_ = 0;
      }
   }

   public Float[] getNormalizedPixels() {
      return normalizedPixels_;
   }
   
   public int getWidth() {
      return width_;
   }
   
   public int getHeight() {
      return height_;
   }
}


