///////////////////////////////////////////////////////////////////////////////
//FILE:          ImageUtils.java
//PROJECT:       Micro-Manager  
//SUBSYSTEM:     MultiChannelShading plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Kurt Thorn, Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2016
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.multichannelshading;

import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

/**
 * Utility code copied from Micro-Manager internal code
 *
 * @author nico
 */
public class ImageUtils {

   public static ImageProcessor subtractImageProcessors(ImageProcessor proc1, ImageProcessor proc2)
         throws ShadingException {
      if ((proc1.getWidth() != proc2.getWidth())
            || (proc1.getHeight() != proc2.getHeight())) {
         throw new ShadingException("Error: Images are of unequal size");
      }
      try {
         if (proc1 instanceof ByteProcessor && proc2 instanceof ByteProcessor) {
            return subtractByteProcessors((ByteProcessor) proc1, (ByteProcessor) proc2);
         } else if (proc1 instanceof ShortProcessor && proc2 instanceof ShortProcessor) {
            return subtractShortProcessors((ShortProcessor) proc1, (ShortProcessor) proc2);
         } else if (proc1 instanceof ShortProcessor && proc2 instanceof ByteProcessor) {
            return subtractShortByteProcessors((ShortProcessor) proc1, (ByteProcessor) proc2);
         } else if (proc1 instanceof ShortProcessor && proc2 instanceof FloatProcessor) {
            return subtractShortFloatProcessors((ShortProcessor) proc1, (FloatProcessor) proc2);
         } else if (proc1 instanceof FloatProcessor && proc2 instanceof ByteProcessor) {
            return subtractFloatProcessors((FloatProcessor) proc1, (ByteProcessor) proc2);
         } else if (proc1 instanceof FloatProcessor && proc2 instanceof ShortProcessor) {
            return subtractFloatProcessors((FloatProcessor) proc1, (ShortProcessor) proc2);
         } else if (proc1 instanceof FloatProcessor) {
            return subtractFloatProcessors((FloatProcessor) proc1, (FloatProcessor) proc2);
         } else {
            throw new ShadingException("Types of images to be subtracted were not compatible");
         }
      } catch (ClassCastException ex) {
         throw new ShadingException("Types of images to be subtracted were not compatible");
      }
   }

   private static ByteProcessor subtractByteProcessors(ByteProcessor proc1, ByteProcessor proc2) {
      return new ByteProcessor(proc1.getWidth(), proc1.getHeight(),
            subtractPixelArrays((byte[]) proc1.getPixels(), (byte[]) proc2.getPixels()),
            null);
   }

   private static ShortProcessor subtractShortByteProcessors(ShortProcessor proc1,
                                                             ByteProcessor proc2) {
      return new ShortProcessor(proc1.getWidth(), proc1.getHeight(),
            subtractPixelArrays((short[]) proc1.getPixels(), (byte[]) proc2.getPixels()),
            null);
   }

   private static ShortProcessor subtractShortProcessors(ShortProcessor proc1,
                                                         ShortProcessor proc2) {
      return new ShortProcessor(proc1.getWidth(), proc1.getHeight(),
            subtractPixelArrays((short[]) proc1.getPixels(), (short[]) proc2.getPixels()),
            null);
   }

   private static ShortProcessor subtractShortFloatProcessors(ShortProcessor proc1,
                                                              FloatProcessor proc2) {
      return new ShortProcessor(proc1.getWidth(), proc1.getHeight(),
            subtractPixelArrays((short[]) proc1.getPixels(), (float[]) proc2.getPixels()),
            null);
   }

   public static ImageProcessor subtractFloatProcessors(FloatProcessor proc1,
                                                        ByteProcessor proc2) {
      return new FloatProcessor(proc1.getWidth(), proc1.getHeight(),
            subtractPixelArrays((float[]) proc1.getPixels(),
                  (byte[]) proc2.getPixels()),
            null);
   }

   public static ImageProcessor subtractFloatProcessors(FloatProcessor proc1,
                                                        ShortProcessor proc2) {
      return new FloatProcessor(proc1.getWidth(), proc1.getHeight(),
            subtractPixelArrays((float[]) proc1.getPixels(),
                  (short[]) proc2.getPixels()),
            null);
   }

   public static ImageProcessor subtractFloatProcessors(FloatProcessor proc1,
                                                        FloatProcessor proc2) {
      return new FloatProcessor(proc1.getWidth(), proc1.getHeight(),
            subtractPixelArrays((float[]) proc1.getPixels(), (float[]) proc2.getPixels()),
            null);
   }

   public static byte[] subtractPixelArrays(byte[] array1, byte[] array2) {
      int l = array1.length;
      byte[] result = new byte[l];
      for (int i = 0; i < l; ++i) {
         result[i] = (byte) Math.max(0, unsignedValue(array1[i]) - unsignedValue(array2[i]));
      }
      return result;
   }

   public static short[] subtractPixelArrays(short[] array1, short[] array2) {
      int l = array1.length;
      short[] result = new short[l];
      for (int i = 0; i < l; ++i) {
         result[i] = (short) Math.max(0, unsignedValue(array1[i]) - unsignedValue(array2[i]));
      }
      return result;
   }

   public static short[] subtractPixelArrays(short[] array1, byte[] array2) {
      int l = array1.length;
      short[] result = new short[l];
      for (int i = 0; i < l; ++i) {
         result[i] = (short) Math.max(0, unsignedValue(array1[i]) - unsignedValue(array2[i]));
      }
      return result;
   }

   public static short[] subtractPixelArrays(short[] array1, float[] array2) {
      int l = array1.length;
      short[] result = new short[l];
      for (int i = 0; i < l; i++) {
         result[i] =
               (short) Math.max(0, unsignedValue(array1[i]) - unsignedValue((short) array2[i]));
      }
      return result;
   }

   public static float[] subtractPixelArrays(float[] array1, byte[] array2) {
      int l = array1.length;
      float[] result = new float[l];
      for (int i = 0; i < l; ++i) {
         result[i] = array1[i] - unsignedValue(array2[i]);
      }
      return result;
   }

   public static float[] subtractPixelArrays(float[] array1, short[] array2) {
      int l = array1.length;
      float[] result = new float[l];
      for (int i = 0; i < l; ++i) {
         result[i] = array1[i] - unsignedValue(array2[i]);
      }
      return result;
   }

   public static float[] subtractPixelArrays(float[] array1, float[] array2) {
      int l = array1.length;
      float[] result = new float[l];
      for (int i = 0; i < l; ++i) {
         result[i] = array1[i] - array2[i];
      }
      return result;
   }


   public static int unsignedValue(byte b) {
      // Sign-extend, then mask
      return ((int) b) & 0x000000ff;
   }

   public static int unsignedValue(short s) {
      // Sign-extend, then mask
      return ((int) s) & 0x0000ffff;
   }
}
