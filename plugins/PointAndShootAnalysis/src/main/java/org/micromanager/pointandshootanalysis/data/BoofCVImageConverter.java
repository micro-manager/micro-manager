
package org.micromanager.pointandshootanalysis.data;

import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageGray;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import org.micromanager.data.Image;

/**
 * Collection of static functions that convert between ImageJ, Micro-Manager,
 * and BoofCV images. Unless otherwise noted, pixels are not copied, but 
 * references are handed over.  Therefore, be careful changing pixels
 * 
 * @author Nico
 */
public final class BoofCVImageConverter {
   
   // TODO: Metadata; may need to make a class that inherits from ImageGray 
   // and adds metadata field 
   
   public static ImageGray mmToBoofCV(Image image) {
      return mmToBoofCV(image, true);
   } 
   
   public static ImageGray mmToBoofCV(Image image, boolean copy) {
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
   
   /**
    * Converts a BoofCV image into an ImageJ ImageProcessor
    * 
    * TODO: support other types besides GrayU8 and GrayU16
    * 
    * @param imgG Input BoofCV image
    * @param copy If true, pixels will be copied, otherwise a reference to 
    *             the BoofCV image's pixels will be handed to the ImageProcerros
    * @return     ImageJ ImageProcessor
    */
   public static ImageProcessor convert(ImageGray imgG, boolean copy) {
      ImageProcessor ip = null;
      if (imgG instanceof GrayU8) {
         ip = new ByteProcessor(imgG.width, imgG.height);
         if (copy) {
            ip.setPixels(((GrayU8) imgG).getData().clone());
         } else {
            ip.setPixels(((GrayU8) imgG).getData());
         }
      } else if (imgG instanceof GrayU16) {
         ip = new ShortProcessor(imgG.width, imgG.height);
         if (copy) {
            ip.setPixels(((GrayU16) imgG).getData().clone());
         } else {
            ip.setPixels(((GrayU16) imgG).getData());
         }
      } else if (imgG instanceof GrayF32) {
         ip = new FloatProcessor(imgG.width, imgG.height);
         if (copy) {
            ip.setPixels(((GrayF32) imgG).getData().clone());
         } else {
            ip.setPixels(((GrayF32) imgG).getData());  
         }
      }
      return ip;
   }
   
   /**
    * Converts an ImageJ ImageProcessor to a BoofCV image
    * 
    * @param ip
    * @param copy
    * @return 
    */
   
   public static ImageGray convert (ImageProcessor ip, boolean copy) {
      ImageGray ig = null;
      if (ip instanceof ByteProcessor) {
         GrayU8 tmp8Image = new GrayU8(ip.getWidth(), ip.getHeight());
         if (copy) {
            tmp8Image.setData(((byte[]) ip.getPixels()).clone());
         } else {
            tmp8Image.setData((byte[]) ip.getPixels());
         }
         ig = tmp8Image;
      } else if (ip instanceof ShortProcessor) {
         GrayU16 tmp16Image = new GrayU16(ip.getWidth(), ip.getHeight());
         if (copy) {
            tmp16Image.setData(((short[]) ip.getPixels()).clone());
         } else {
            tmp16Image.setData((short[]) ip.getPixels());
         }
         ig = tmp16Image;
      } else if (ip instanceof FloatProcessor) {
         GrayF32 tmpF32Image = new GrayF32(ip.getWidth(), ip.getHeight());
         if (copy) {
            tmpF32Image.setData(((float[]) ip.getPixels()).clone());
         } else {
            tmpF32Image.setData((float[]) ip.getPixels());
         }
         ig = tmpF32Image;
      }
      
      return ig;
   }
   
}
