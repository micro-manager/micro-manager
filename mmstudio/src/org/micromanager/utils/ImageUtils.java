package org.micromanager.utils;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Point;

import mmcorej.CMMCore;

public class ImageUtils {

   public static int BppToImageType(long Bpp) {
      int BppInt = (int) Bpp;
      switch (BppInt) {
         case 1:
            return ImagePlus.GRAY8;
         case 2:
            return ImagePlus.GRAY16;
         case 4:
            return ImagePlus.COLOR_RGB;
      }
      return 0;
   }

   public static int getImageProcessorType(ImageProcessor proc) {
      if (proc instanceof ByteProcessor) {
         return ImagePlus.GRAY8;
      }

      if (proc instanceof ShortProcessor) {
         return ImagePlus.GRAY16;
      }

      if (proc instanceof ColorProcessor) {
         return ImagePlus.COLOR_RGB;
      }

      return -1;
   }

   public static ImageProcessor makeProcessor(CMMCore core) {
      return makeProcessor(core, null);
   }

   public static ImageProcessor makeProcessor(CMMCore core, Object imgArray) {
      int w = (int) core.getImageWidth();
      int h = (int) core.getImageHeight();
      int Bpp = (int) core.getBytesPerPixel();
      int type;
      switch (Bpp) {
         case 1:
            type = ImagePlus.GRAY8;
            break;
         case 2:
            type = ImagePlus.GRAY16;
            break;
         case 4:
            type = ImagePlus.COLOR_RGB;
            break;
         default:
            type = 0;
      }
      return makeProcessor(type, w, h, imgArray);
   }

   public static ImageProcessor makeProcessor(int type, int w, int h, Object imgArray) {
      if (imgArray == null) {
         return makeProcessor(type, w, h);
      } else {
         switch (type) {
            case ImagePlus.GRAY8:
               return new ByteProcessor(w, h, (byte[]) imgArray, null);
            case ImagePlus.GRAY16:
               return new ShortProcessor(w, h, (short[]) imgArray, null);
            case ImagePlus.COLOR_RGB:
               return new ColorProcessor(w, h, (int[]) imgArray);
            default:
               return null;
         }
      }
   }

   public static ImageProcessor makeProcessor(int type, int w, int h) {
      if (type == ImagePlus.GRAY8) {
         return new ByteProcessor(w, h);
      } else if (type == ImagePlus.GRAY16) {
         return new ShortProcessor(w, h);
      } else if (type == ImagePlus.COLOR_RGB) {
         return new ColorProcessor(w, h);
      } else {
         return null;
      }
   }

   /*
    * Finds the position of the maximum pixel value.
    */
   public static Point findMaxPixel(ImagePlus img) {
      ImageProcessor proc = img.getProcessor();
      float[] pix = (float[]) proc.getPixels();
      int width = img.getWidth();
      double max = 0;
      int imax = -1;
      for (int i = 0; i < pix.length; i++) {
         if (pix[i] > max) {
            max = pix[i];
            imax = i;
         }
      }
      int y = imax / width;
      int x = imax % width;
      return new Point(x, y);
   }

   public static Point findMaxPixel(ImageProcessor proc) {
      int width = proc.getWidth();
      int imax = findArrayMax(proc.getPixels());

      int y = imax / width;
      int x = imax % width;
      return new Point(x, y);
   }

   public static byte[] get8BitData(Object bytesAsObject) {
      return (byte[]) bytesAsObject;
   }

   public static short[] get16BitData(Object shortsAsObject) {
      return (short[]) shortsAsObject;
   }

   public static int[] get32BitData(Object intsAsObject) {
      return (int[]) intsAsObject;
   }

   public static int findArrayMax(Object pix) {
      if (pix instanceof byte [])
         return findArrayMax((byte []) pix);
      if (pix instanceof int [])
         return findArrayMax((int []) pix);
      if (pix instanceof short [])
         return findArrayMax((short []) pix);
      if (pix instanceof float [])
         return findArrayMax((float []) pix);
      else
         return -1;
   }


   public static int findArrayMax(float[] pix) {
      float pixel;
      int imax = -1;
      float max = Float.MIN_VALUE;
      for (int i = 0; i < pix.length; ++i) {
         pixel = pix[i];
         if (pixel > max) {
            max = pixel;
            imax = i;
         }
      }
      return imax;
   }

   public static int findArrayMax(short[] pix) {
      short pixel;
      int imax = -1;
      short max = Short.MIN_VALUE;
      for (int i = 0; i < pix.length; ++i) {
         pixel = pix[i];
         if (pixel > max) {
            max = pixel;
            imax = i;
         }
      }
      return imax;
   }

   public static int findArrayMax(byte[] pix) {
      byte pixel;
      int imax = -1;
      byte max = Byte.MIN_VALUE;
      for (int i = 0; i < pix.length; ++i) {
         pixel = pix[i];
         if (pixel > max) {
            max = pixel;
            imax = i;
         }
      }
      return imax;
   }

   public static int findArrayMax(int[] pix) {
      int pixel;
      int imax = -1;
      int max = Integer.MIN_VALUE;
      for (int i = 0; i < pix.length; ++i) {
         pixel = pix[i];
         if (pixel > max) {
            max = pixel;
            imax = i;
         }
      }
      return imax;
   }

   /*
    * channel should be 0, 1 or 2.
    */
   public static byte[] singleChannelFromRGB32(int[] pixels, int channel) {
      if (channel != 0 || channel != 1 || channel != 2)
         return null;
      
      byte[] newPixels = new byte[pixels.length];
      int bitShift = 8*channel;
      for (int i=0;i<pixels.length;++i) {
         newPixels[i] = (byte) (0xff & (pixels[i] >> bitShift));
      }
      return newPixels;
   }

   /*
    * channel should be 0, 1 or 2.
    */
   public static short[] singleChannelFromRGB64(int[] pixels, int channel) {
       short [] newPixels = new short[pixels.length/2];
       int i=0;
       if (channel == 0) { // even pixels, first half
          for (int j=0; j<newPixels.length; j+=2) {
             newPixels[i++] = (short) (pixels[j] & 0xffff);
          }
       } else if (channel == 1) { // even pixels, second half
          for (int j=0; j<newPixels.length; j+=2) {
             newPixels[i++] = (short) (pixels[j] >> 16);
          }
       } else if (channel == 2) { // odd pixels, first half
          for (int j=1; j<newPixels.length; j+=2) {
             newPixels[i++] = (short) (pixels[j] & 0xffff);
          }
       } else {
          newPixels = null;
       }
       return newPixels;
   }

}
