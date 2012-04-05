package org.micromanager.utils;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;
import java.awt.Color;

import java.awt.Point;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.acquisition.TaggedImageStorageDiskDefault;
import org.micromanager.api.TaggedImageStorage;

public class ImageUtils {
   private static Class storageClass_ = TaggedImageStorageDiskDefault.class;

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
            case ImagePlus.GRAY32:
               return new FloatProcessor(w,h, (float[]) imgArray, null);
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
      } else if (type == ImagePlus.GRAY32) {
         return new FloatProcessor(w,h);
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

   public static byte[] convertRGB32IntToBytes(int [] pixels) {
      byte[] bytes = new byte[pixels.length*4];
      int j = 0;
      for (int i = 0; i<pixels.length;++i) {
         bytes[j++] = (byte) (pixels[i] & 0xff);
         bytes[j++] = (byte) ((pixels[i] >> 8) & 0xff);
         bytes[j++] = (byte) ((pixels[i] >> 16) & 0xff);
         bytes[j++] = 0;
      }
      return bytes;
   }

   public static byte[] getRGB32PixelsFromColorPanes(byte[][] planes) {
      int j=0;
      byte[] pixels = new byte[planes.length * 4];
      for (int i=0;i<planes.length;++i) {
         pixels[j++] = planes[2][i]; //B
         pixels[j++] = planes[1][i]; //G
         pixels[j++] = planes[0][i]; //R
         pixels[j++] = 0; // Empty A byte.
      }
      return pixels;
   }

   public static short[] getRGB64PixelsFromColorPlanes(short[][] planes) {
      int j=-1;
      short[] pixels = new short[planes[0].length * 4];
      for (int i=0;i<planes[0].length;++i) {
         pixels[++j] = planes[2][i]; //B
         pixels[++j] = planes[1][i]; //G
         pixels[++j] = planes[0][i]; //R
         pixels[++j] = 0; // Empty A (two bytes).
      }
      return pixels;
   }

   public static byte[][] getColorPlanesFromRGB32(byte[] pixels) {
       byte [] r = new byte[pixels.length/4];
       byte [] g = new byte[pixels.length/4];
       byte [] b = new byte[pixels.length/4];

       int j=0;
       for (int i=0;i<pixels.length/4;++i) {
          b[i] = pixels[j++];
          g[i] = pixels[j++];
          r[i] = pixels[j++];
          j++; // skip "A" byte.
       }
       
       byte[][] planes = {r,g,b};
       return planes;
   }

   public static short[][] getColorPlanesFromRGB64(short[] pixels) {
       short [] r = new short[pixels.length/4];
       short [] g = new short[pixels.length/4];
       short [] b = new short[pixels.length/4];

       int j=0;
       for (int i=0;i<pixels.length/4;++i) {
          b[i] = pixels[j++];
          g[i] = pixels[j++];
          r[i] = pixels[j++];
          j++; // skip "A" (two bytes).
       }

       short[][] planes = {r,g,b};
       return planes;
   }


   /*
    * channel should be 0, 1 or 2.
    */
   public static byte[] singleChannelFromRGB32(byte[] pixels, int channel) {
      if (channel != 0 && channel != 1 && channel != 2)
         return null;

      byte [] p = new byte[pixels.length/4];

      for (int i=0;i<p.length;++i) {
         p[i] = pixels[(2-channel) + 4*i]; //B,G,R
      }
      return p;
   }

   /*
    * channel should be 0, 1 or 2.
    */
   public static short[] singleChannelFromRGB64(short[] pixels, int channel) {
      if (channel != 0 && channel != 1 && channel != 2)
         return null;

      short [] p = new short[pixels.length/4];

      for (int i=0;i<p.length;++i) {
         p[i] = pixels[(2-channel) + 4*i]; // B,G,R
      }
      return p;
   }

   
   public static LUT makeLUT(Color color, double gamma) {
      int r = color.getRed();
      int g = color.getGreen();
      int b = color.getBlue();

      int size = 256;
      byte [] rs = new byte[size];
      byte [] gs = new byte[size];
      byte [] bs = new byte[size];

      int x;
      double xn;
      double yn;
      for (int p=0;p<size;++p) {
         x = MathFunctions.clip(0, p, size);
         xn = x / (double) size;
         yn = Math.pow(xn, gamma);
         rs[p] = (byte) (yn * r);
         gs[p] = (byte) (yn * g);
         bs[p] = (byte) (yn * b);
      }
      return new LUT(8,size,rs,gs,bs);
   }

   public static void setPreferredTaggedImageStorage(Class storageClass) {
      storageClass_ = storageClass;
   }

   public static TaggedImageStorage newImageStorageInstance
           (String acqPath, boolean newDataSet, JSONObject summaryMetadata) {
      try {
        // return new TaggedImageStorageDiskDefault(acqPath, b, summaryMetadata);
        return (TaggedImageStorage) storageClass_
                 .getConstructor(String.class, Boolean.class, JSONObject.class)
                 .newInstance(acqPath, newDataSet, summaryMetadata);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
      return null;
   }

   public class MinAndMax {
      int min;
      int max;
   }

   private static int unsignedValue(byte b) {
      return ((0x100 + b) % 0x100);
   }

   private static int unsignedValue(short s) {
      return ((0x10000 + s) % 0x10000);
   }

   public static int getMin(final Object pixels) {
      if (pixels instanceof byte[]) {
         byte[] bytes = (byte []) pixels;
         int min = Integer.MAX_VALUE;
         for (int i=0;i<bytes.length;++i) {
            min = Math.min(min, unsignedValue(bytes[i]));
         }
         return min;
      }
      if (pixels instanceof short[]) {
         short[] shorts = (short []) pixels;
         int min = Integer.MAX_VALUE;
         for (int i=0;i<shorts.length;++i) {
            min = Math.min(min, unsignedValue(shorts[i]));
         }
         return min;
      }
      return -1;
   }

   public static int getMax(final Object pixels) {
      if (pixels instanceof byte[]) {
         byte[] bytes = (byte []) pixels;
         int max = Integer.MIN_VALUE;
         for (int i=0;i<bytes.length;++i) {
            max = Math.max(max, unsignedValue(bytes[i]));
         }
         return max;
      }
      if (pixels instanceof short[]) {
         short[] shorts = (short []) pixels;
         int min = Integer.MIN_VALUE;
         for (int i=0;i<shorts.length;++i) {
            min = Math.max(min, unsignedValue(shorts[i]));
         }
         return min;
      }
      return -1;
   }

   public static int[] getMinMax(final Object pixels) {
      int[] result = new int[2];
      int max = Integer.MIN_VALUE;
      int min = Integer.MAX_VALUE;

      if (pixels instanceof byte[]) {
         byte[] bytes = (byte []) pixels;
         for (int i=0;i<bytes.length;++i) {
            max = Math.max(max, unsignedValue(bytes[i]));
            min = Math.min(min, unsignedValue(bytes[i]));
         }
         result[0] = min;
         result[1] = max;
         return result;
      }
      if (pixels instanceof short[]) {
         short[] shorts = (short []) pixels;
         for (int i=0;i<shorts.length;++i) {
            min = Math.min(min, unsignedValue(shorts[i]));
            max = Math.max(max, unsignedValue(shorts[i]));
         }
         result[0] = min;
         result[1] = max;
         return result;
      }
      return null;
   }

   public static TaggedImage makeTaggedImage(ImageProcessor proc) {
      JSONObject tags = new JSONObject();
      try {
         MDUtils.setChannelIndex(tags, 0);
         MDUtils.setSliceIndex(tags, 0);
         MDUtils.setPositionIndex(tags, 0);
         MDUtils.setFrameIndex(tags, 0);
         MDUtils.setWidth(tags, proc.getWidth());
         MDUtils.setHeight(tags, proc.getHeight());
         MDUtils.setPixelType(tags, getImageProcessorType(proc));
      } catch (Exception e) {
         return null;
      }
      return new TaggedImage(proc.getPixels(), tags);
   }


   public static TaggedImage makeTaggedImage(Object pixels, int channelIndex,
           int sliceIndex, int positionIndex, int frameIndex, int width,
           int height, int numberOfBytesPerPixel) {
      JSONObject tags = new JSONObject();
      try {
         MDUtils.setChannelIndex(tags, channelIndex);
         MDUtils.setSliceIndex(tags, sliceIndex);
         MDUtils.setPositionIndex(tags, positionIndex);
         MDUtils.setFrameIndex(tags, frameIndex);
         MDUtils.setWidth(tags, width);
         MDUtils.setHeight(tags, height);
         MDUtils.setPixelTypeFromByteDepth(tags, numberOfBytesPerPixel);
      } catch (Exception e) {
         return null;
      }
      return new TaggedImage(pixels, tags);
   }
}
