
package org.micromanager.pointandshootanalysis.data;

import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageGray;
import georegression.struct.point.Point2D_I32;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.io.IOException;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
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
         if (copy) {
            ip = new ByteProcessor(imgG.width, imgG.height, 
                    ((GrayU8) imgG).getData().clone());
         } else {
            ip = new ByteProcessor(imgG.width, imgG.height, 
                    ((GrayU8) imgG).getData());
         }
      } else if (imgG instanceof GrayU16) {
         
         if (copy) {
            ip = new ShortProcessor(imgG.width, imgG.height, 
                    ((GrayU16) imgG).getData().clone(), null );
         } else {
            ip = new ShortProcessor(imgG.width, imgG.height, 
                    ((GrayU16) imgG).getData(), null );
         }
      } else if (imgG instanceof GrayF32) {
         if (copy) {            
            ip = new FloatProcessor(imgG.width, imgG.height, 
                    ((GrayF32) imgG).getData().clone());
         } else {
            ip = new FloatProcessor(imgG.width, imgG.height, 
                    ((GrayF32) imgG).getData());
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
         GrayU8 tmp8Image = new GrayU8(); //ip.getWidth(), ip.getHeight());
         if (copy) {
            tmp8Image.setData(((byte[]) ip.getPixels()).clone());
         } else {
            tmp8Image.setData((byte[]) ip.getPixels());
         }         
         ig = tmp8Image;
      } else if (ip instanceof ShortProcessor) {
         GrayU16 tmp16Image = new GrayU16();
         if (copy) {
            tmp16Image.setData(((short[]) ip.getPixels()).clone());
         } else {
            tmp16Image.setData((short[]) ip.getPixels());
         }
         ig = tmp16Image;
      } else if (ip instanceof FloatProcessor) {
         GrayF32 tmpF32Image = new GrayF32();
         if (copy) {
            tmpF32Image.setData(((float[]) ip.getPixels()).clone());
         } else {
            tmpF32Image.setData((float[]) ip.getPixels());
         }
         ig = tmpF32Image;
      }
      if (ig != null) {
         ig.setWidth(ip.getWidth());
         ig.setStride(ip.getWidth());
         ig.setHeight(ip.getHeight());
      }
      
      return ig;
   }
   
    /**
    * Utility function.  Extracts region from a MM dataset and returns as 
    * a BoofCV ImageGray.  Points to the same pixel data as the original
    * 
    * @param dp Micro-Manager Datasource
    * @param cb Micro-Manager Coords Builder (for efficiency)
    * @param frame Frame number from which we want the image data
    * @param p point around which to build the ROI
    * @param halfBoxSize Half the width and length of the ROI
    * @return ImageGray Note that the pixels are not copied.
    * 
    * @throws IOException 
    */
   public static ImageGray subImage(final DataProvider dp, final Coords.Builder cb,
           final int frame, final Point2D_I32 p, final int halfBoxSize) throws IOException {
      Coords coord = cb.t(frame).build();
      Image img = dp.getImage(coord);
      
      ImageGray ig = BoofCVImageConverter.mmToBoofCV(img, false);
      if (p.getX() - halfBoxSize < 0 ||
              p.getY() - halfBoxSize < 0 ||
              p.getX() + halfBoxSize >= ig.getWidth() ||
              p.getY() + halfBoxSize >= ig.getHeight()) {
         return null; // TODO: we'll get stuck at the edge
      }
      return (ImageGray) ig.subimage((int) p.getX() - halfBoxSize, 
              (int) p.getY() - halfBoxSize, (int) p.getX() + halfBoxSize, 
              (int) p.getY() + halfBoxSize);
   }
   
   
}
