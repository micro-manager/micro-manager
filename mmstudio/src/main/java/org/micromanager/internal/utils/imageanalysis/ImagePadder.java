
package org.micromanager.internal.utils.imageanalysis;

import boofcv.alg.misc.GImageMiscOps;
import boofcv.alg.misc.GPixelMath;
import boofcv.core.image.GConvertImage;
import boofcv.struct.image.GrayF32;
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
      
      //TODO: make implementation more memory and CPU efficient
      ImageGray output = null;
      ImageGray lrSide = null;
      ImageGray tbSide = null;
      final GrayF32 sideMask = new GrayF32(halfWidth, height);
      final GrayF32 sideTmp = new GrayF32(halfWidth, height);
      final GrayF32 tbMask = new GrayF32(2 * width, halfHeight);
      final GrayF32 tbTmp = new GrayF32(2 * width, halfHeight);
      if (null != input.getImageType().getDataType()) {
         switch (input.getImageType().getDataType()) {
            case U16:
               output =  new GrayU16(2 * width, 2 * height);
               lrSide = new GrayU16(halfWidth, height);
               tbSide = new GrayU16(2 * width, halfHeight);
               break;
            case U8:
               output = new GrayU8(2 * width, 2 * height);
               lrSide = new GrayU8(halfWidth, height);
               tbSide = new GrayU8(2 * width, halfHeight);
               break;
               // TODO: throw unsupportedtype exception
            default:
               break;
         }
      }
      
      // copy the source to the center of destination, then add mirrored sides,
      // then add mirrored tops and bottom
      GImageMiscOps.copy(0, 0, halfWidth, halfHeight, width, height, input, output);
      // Left side
      GImageMiscOps.copy(0, 0, 0, 0, halfWidth, height, input, lrSide);
      GImageMiscOps.flipHorizontal(lrSide);
      sideMask.setData(leftHanWindow1DA(halfWidth, height));
      GConvertImage.convert(lrSide, sideTmp);
      GPixelMath.multiply(sideTmp, sideMask, sideTmp);
      GConvertImage.convert(sideTmp, lrSide);
      GImageMiscOps.copy(0, 0, 0, halfHeight, halfWidth, height, lrSide, output);
      // Right side
      GImageMiscOps.copy(halfWidth, 0, 0, 0, halfWidth, height, input, lrSide);
      GImageMiscOps.flipHorizontal(lrSide);
      sideMask.setData(rightHanWindow1DA(halfWidth, height));
      GConvertImage.convert(lrSide, sideTmp);
      GPixelMath.multiply(sideTmp, sideMask, sideTmp);
      GConvertImage.convert(sideTmp, lrSide);
      GImageMiscOps.copy(0, 0, width + halfWidth, halfHeight, 
              halfWidth, height, lrSide, output);
      
      // Now top/bottom copy
      GImageMiscOps.copy(0, halfHeight, 0, 0, 2 * width, halfHeight, output, tbSide);
      GImageMiscOps.flipVertical(tbSide);
      tbMask.setData(topHanWindow1DA(2 * width, halfHeight));
      GConvertImage.convert(tbSide, tbTmp);
      GPixelMath.multiply(tbTmp, tbMask, tbTmp);
      GConvertImage.convert(tbTmp, tbSide);
      GImageMiscOps.copy(0, 0, 0, 0, 2 * width, halfHeight, tbSide, output);
      
      // Bottom
      GImageMiscOps.copy(0, height, 0, 0, 2 * width, halfHeight, output, tbSide);
      GImageMiscOps.flipVertical(tbSide);
      tbMask.setData(bottomHanWindow1DA(2 * width, halfHeight));
      GConvertImage.convert(tbSide, tbTmp);
      GPixelMath.multiply(tbTmp, tbMask, tbTmp);
      GConvertImage.convert(tbTmp, tbSide);
      GImageMiscOps.copy(0, 0, 0, height + halfHeight, 2 * width, halfHeight, tbSide, output);
      
      
      // image i
      
      return output;
   }
   
   
   /**
    * Creates a HanWindow specific for the padPreibisch function
    * Left half is 0, followed by a "half" HanWindow ending at 1.0.
    *
    * @param width
    * @param height
    * @return 
    */
   public static float[] leftHanWindow1DA(int width, int height) {
      float[] han1DArray = new float[width];
      int halfEdgeSize = (int) (width / 2);

      for (int i = 0; i < halfEdgeSize; i++) {
         han1DArray[i + halfEdgeSize] = (float) (0.5 * (1 - Math.cos(2 * Math.PI * i / width)));
      }

      float[] han2DArray = new float[width * height];
      // non-isotropic, separable way to compute the 2D version
      for (int x = 0; x < width; x++) {
         for (int y = 0; y < height; y++) {
            han2DArray[x + y * width] = han1DArray[x];
         }
      }
      return han2DArray;
   }
   
   /**
    * Creates a HanWindow specific for the padPreibisch function
    * Right half is 0, followed by a "half" HanWindow ending at 1.0.
    *
    * @param width
    * @param height
    * @return 
    */
   public static float[] rightHanWindow1DA(int width, int height) {
      float[] han1DArray = new float[width];
      int halfEdgeSize = width / 2;

      for (int i = 0; i < halfEdgeSize; i++) {
         han1DArray[halfEdgeSize - i - 1] = (float) (0.5 * (1 - Math.cos(2 * Math.PI * i / width)));
      }

      float[] han2DArray = new float[width * height];
      // non-isotropic, separable way to compute the 2D version
      for (int x = 0; x < width; x++) {
         for (int y = 0; y < height; y++) {
            han2DArray[x + y * width] = han1DArray[x];
         }
      }
      return han2DArray;
      
   }
   
   /**
    * Creates a HanWindow specific for the padPreibisch function
    * Top half is 0, followed by a "half" HanWindow ending at 1.0.
    *
    * @param width
    * @param height
    * @return 
    */
   public static float[] topHanWindow1DA(int width, int height) {
      float[] han1DArray = new float[height];
      int halfHeight = height / 2;

      for (int i = 0; i < halfHeight; i++) {
         han1DArray[halfHeight + i] = (float) (0.5 * (1 - Math.cos(2 * Math.PI * i / height)));
      }

      float[] han2DArray = new float[width * height];
      // non-isotropic, separable way to compute the 2D version
      for (int x = 0; x < width; x++) {
         for (int y = 0; y < height; y++) {
            han2DArray[x + y * width] = han1DArray[y];
         }
      }
      return han2DArray;
   }
   
   /**
    * Creates a HanWindow specific for the padPreibisch function
    * Top half is 0, followed by a "half" HanWindow ending at 1.0.
    *
    * @param width
    * @param height
    * @return 
    */
   public static float[] bottomHanWindow1DA(int width, int height) {
      float[] han1DArray = new float[height];
      int halfHeight = height / 2;

      for (int i = 0; i < halfHeight; i++) {
         han1DArray[halfHeight + i] = (float) (0.5 * (1 - Math.cos(2 * Math.PI * i / height)));
      }

      float[] han2DArray = new float[width * height];
      // non-isotropic, separable way to compute the 2D version
      for (int x = 0; x < width; x++) {
         for (int y = 0; y < height; y++) {
            han2DArray[x + y * width] = han1DArray[height - y - 1];
         }
      }
      return han2DArray;
   }
   
}
