
package org.micromanager.pointandshootanalysis.data;

import boofcv.struct.image.GrayU16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageGray;
import org.micromanager.data.Image;

/**
 *
 * @author NicoLocal
 */
public final class BoofCVImageConverter {
   
   // TODO: Metadata; may need to inherit from ImageGray and add metadata field 
   
   public static ImageGray createBoofCVImage(Image image) {
      return createBoofCVImage(image, true);
   } 
   
   public static ImageGray createBoofCVImage(Image image, boolean copy) {
      ImageGray outImage;
      switch (image.getBytesPerPixel()){
         case 1 : 
            GrayU8 tmp8Image = new GrayU8(); 
            if (copy) {
               tmp8Image.setData((byte[]) image.getRawPixelsCopy());
            } else {
               tmp8Image.setData((byte[]) image.getRawPixels());
            }
            outImage = tmp8Image; 
            break;
         case 2 : 
            GrayU16 tmp16Image = new GrayU16(); 
            if (copy) {
               tmp16Image.setData((short[]) image.getRawPixelsCopy());
            } else {
               tmp16Image.setData((short[]) image.getRawPixels());
            }
            outImage = tmp16Image;
            break;
         // TODO: RGB?
         default:  // TODO: catch this as exception?
            GrayU8 tmpImage = new GrayU8(); 
            if (copy) {
               tmpImage.setData((byte[]) image.getRawPixelsCopy());
            } else {
               tmpImage.setData((byte[]) image.getRawPixels());
            }
            outImage = tmpImage;
      }
      outImage.setWidth(image.getWidth());
      outImage.setHeight(image.getHeight());
      outImage.setStride(image.getWidth());
      
      return outImage;
   }
}
