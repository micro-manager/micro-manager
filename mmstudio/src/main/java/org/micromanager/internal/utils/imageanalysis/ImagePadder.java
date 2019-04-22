
package org.micromanager.internal.utils.imageanalysis;

import boofcv.alg.misc.GImageMiscOps;
import boofcv.struct.image.GrayU16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageGray;

/**
 *
 * @author NicoLocal
 */
public class ImagePadder {
   
   /**
    * Prepares the image for FFT to reduce Gibbs "cross"
    * Uses method described by Preibisch et al. (TODO: ref)
    * Fast Stitching of Large 3D Biological Datasets 
    * Stephan Preibisch, Stephan Saalfeld and Pavel Tomancak
    * Max Planck Institute of Molecular Cell Biology and Genetics, 
    * Dresden, Germany
    * Doubles the image in size, fills in the edges by mirroring,
    * and using a Han window (only over the edges)
    * @param input
    * @return 
    */
   public static ImageGray padPreibisch(ImageGray input) {
      //TODO: define input size requirements and enforce them
      int width = input.getWidth();
      int height = input.getHeight();
      int halfWidth = (int) (0.5 * width);
      int halfHeight = (int) (0.5 * height);
      
      ImageGray output = null;
      ImageGray lrSide = null;
      ImageGray tbSide = null;
      if (null != input.getImageType().getDataType()) {
         switch (input.getImageType().getDataType()) {
            case U16:
               output =  new GrayU16(2*width, 2*height);
               lrSide = new GrayU16(halfWidth, height);
               tbSide = new GrayU16(2*width, halfHeight);
               break;
            case U8:
               output = new GrayU8(2*width, 2*height);
               lrSide = new GrayU8(halfWidth, height);
               tbSide = new GrayU8(2*width, halfHeight);
               break;
               // TODO: throw unsupportedtype exception
            default:
               break;
         }
      }
      // copy the source to the center of destination, then add mirrored sides,
      // then add mirrored tops and bottom
      GImageMiscOps.copy(0, 0, halfWidth, halfHeight, width, height, input, output);
      GImageMiscOps.copy(0, 0, 0, 0, halfWidth, height, input, lrSide);
      GImageMiscOps.flipHorizontal(lrSide);
      GImageMiscOps.copy(0, 0, 0, halfHeight, halfWidth, height, lrSide, output);
      GImageMiscOps.copy(halfWidth, 0, 0, 0, halfWidth, height, input, lrSide);
      GImageMiscOps.flipHorizontal(lrSide);
      GImageMiscOps.copy(0, 0, width + halfWidth, halfHeight, 
              halfWidth, height, lrSide, output);
      
      // Now top/bottom copy
      GImageMiscOps.copy(0, halfHeight, 0, 0, 2 * width, halfHeight, output, tbSide);
      GImageMiscOps.flipVertical(tbSide);
      GImageMiscOps.copy(0, 0, 0, 0, 2*width, halfHeight, tbSide, output);
      GImageMiscOps.copy(0, height, 0, 0, 2*width, halfHeight, output, tbSide);
      GImageMiscOps.flipVertical(tbSide);
      GImageMiscOps.copy(0, 0, 0, height + halfHeight, 2*width, halfHeight, tbSide, output);
      
      
      return output;
   }
}
