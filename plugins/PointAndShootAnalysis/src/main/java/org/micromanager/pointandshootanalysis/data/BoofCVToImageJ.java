
package org.micromanager.pointandshootanalysis.data;

import boofcv.struct.image.GrayU16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageGray;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

/**
 *
 * @author nico
 */
public class BoofCVToImageJ {
   
   public static ImageProcessor convert(ImageGray imgG) {
      if (imgG instanceof GrayU8) {
         return new ByteProcessor(imgG.width, imgG.height);
      } else if (imgG instanceof GrayU16) {
         return new ShortProcessor(imgG.width, imgG.height);
      }
      return null;
   }
   
}
