package org.micromanager.internal.utils.imageanalysis;

import boofcv.struct.image.ImageGray;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FHT;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;
import java.awt.Color;
import java.awt.Point;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.MMException;
import org.micromanager.internal.utils.ReportingUtils;

public final class ImageUtils {

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
               // Micro-Manager RGB32 images are generally composed of byte
               // arrays, but ImageJ only takes int arrays.
               if (imgArray instanceof byte[]) {
                  imgArray = convertRGB32BytesToInt((byte[]) imgArray);
               }
               return new ColorProcessor(w, h, (int[]) imgArray);
            default:
               return null;
         }
      }
   }
   
   public static ImageProcessor makeProcessor(TaggedImage taggedImage) {
      final JSONObject tags = taggedImage.tags;
      try {
      return makeProcessor(MDUtils.getIJType(tags), MDUtils.getWidth(tags),
              MDUtils.getHeight(tags), taggedImage.pix);
      } catch (Exception e) {
          ReportingUtils.logError(e);
          return null;
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
   
   public static ImageProcessor makeMonochromeProcessor(TaggedImage taggedImage) {
        try {
            ImageProcessor processor;
            if (MDUtils.isRGB32(taggedImage)) {
                ColorProcessor colorProcessor = new ColorProcessor(
                        MDUtils.getWidth(taggedImage.tags), 
                        MDUtils.getHeight(taggedImage.tags), 
                        convertRGB32UBytesToInt((byte []) taggedImage.pix));
                processor = colorProcessor.convertToByteProcessor();
            } else {
                processor = makeProcessor(taggedImage);
            }
            return processor;
        } catch (Exception e) {
            ReportingUtils.logError(e);
            return null;
        }
   }


   public static ImageProcessor subtractImageProcessors(ImageProcessor proc1, ImageProcessor proc2)
           throws MMException {
      if ((proc1.getWidth() != proc2.getWidth())
              || (proc1.getHeight() != proc2.getHeight())) {
         throw new MMException("Error: Images are of unequal size");
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
             throw new MMException("Types of images to be subtracted were not compatible");
         }
      } catch (ClassCastException ex) {
         throw new MMException("Types of images to be subtracted were not compatible");
      }
   }
   
      public static ImageProcessor subtractFloatProcessors(FloatProcessor proc1, 
           ByteProcessor proc2) {
       return new FloatProcessor(proc1.getWidth(), proc1.getHeight(),
               subtractPixelArrays((float[]) proc1.getPixels(), 
               (byte[]) proc2.getPixels() ),
               null);
   }
   
   public static ImageProcessor subtractFloatProcessors(FloatProcessor proc1, 
           ShortProcessor proc2) {
       return new FloatProcessor(proc1.getWidth(), proc1.getHeight(),
               subtractPixelArrays((float[]) proc1.getPixels(), 
               (short[]) proc2.getPixels() ),
               null);
   }
   
   public static ImageProcessor subtractFloatProcessors(FloatProcessor proc1, FloatProcessor proc2) {
       return new FloatProcessor(proc1.getWidth(), proc1.getHeight(),
               subtractPixelArrays((float[]) proc1.getPixels(), (float[]) proc2.getPixels()),
               null);
   }
   
   private static ByteProcessor subtractByteProcessors(ByteProcessor proc1, ByteProcessor proc2) {
      return new ByteProcessor(proc1.getWidth(), proc1.getHeight(),
              subtractPixelArrays((byte []) proc1.getPixels(), (byte []) proc2.getPixels()),
              null);
   }
   
   private static ShortProcessor subtractShortByteProcessors(ShortProcessor proc1, ByteProcessor proc2) {
      return new ShortProcessor(proc1.getWidth(), proc1.getHeight(),
              subtractPixelArrays((short []) proc1.getPixels(), (byte []) proc2.getPixels()),
              null);
   }
   
   private static ShortProcessor subtractShortProcessors(ShortProcessor proc1, ShortProcessor proc2) {
      return new ShortProcessor(proc1.getWidth(), proc1.getHeight(),
              subtractPixelArrays((short []) proc1.getPixels(), (short []) proc2.getPixels()),
              null);
   }
   
   private static ShortProcessor subtractShortFloatProcessors(ShortProcessor proc1, FloatProcessor proc2) {
       return new ShortProcessor(proc1.getWidth(), proc1.getHeight(),
               subtractPixelArrays( (short[]) proc1.getPixels(), (float []) proc2.getPixels() ),
                null);
       
   };
   
   public static byte[] subtractPixelArrays(byte[] array1, byte[] array2) {
      int l = array1.length;
      byte[] result = new byte[l];
      for (int i=0;i<l;++i) {
         result[i] = (byte) Math.max(0, unsignedValue(array1[i]) - 
                 unsignedValue(array2[i]) );
      }
      return result;
   }
   
   public static short[] subtractPixelArrays(short[] array1, short[] array2) {
      int l = array1.length;
      short[] result = new short[l];
      for (int i=0;i<l;++i) {
         result[i] = (short) Math.max(0, unsignedValue(array1[i]) - unsignedValue(array2[i]));
      }
      return result;
   }
   
   public static short[] subtractPixelArrays(short[] array1, byte[] array2) {
      int l = array1.length;
      short[] result = new short[l];
      for (int i=0;i<l;++i) {
         result[i] = (short) Math.max(0, unsignedValue(array1[i]) - unsignedValue(array2[i]));
      }
      return result;
   }
   
   public static short[] subtractPixelArrays(short[] array1, float[] array2) {
      int l = array1.length;
      short[] result = new short[l];
      for (int i=0; i < l; i++) {
         result[i] = (short) Math.max (0, unsignedValue(array1[i]) - unsignedValue( (short) array2[i]));
      }
      return result;
   }
   public static float[] subtractPixelArrays(float[] array1, byte[] array2) {
      int l = array1.length;
      float[] result = new float[l];
      for (int i=0;i<l;++i) {
         result[i] = array1[i] - unsignedValue(array2[i]);
      }
      return result;
   }
   
   public static float[] subtractPixelArrays(float[] array1, short[] array2) {
      int l = array1.length;
      float[] result = new float[l];
      for (int i=0;i<l;++i) {
         result[i] = array1[i] - unsignedValue(array2[i]);
      }
      return result;
   }
   
   public static float[] subtractPixelArrays(float[] array1, float[] array2) {
      int l = array1.length;
      float[] result = new float[l];
      for (int i=0;i<l;++i) {
         result[i] = array1[i] - array2[i];
      }
      return result;
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

   private static int findArrayMax(Object pix) {
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


   private static int findArrayMax(float[] pix) {
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

   private static int findArrayMax(short[] pix) {
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

   private static int findArrayMax(byte[] pix) {
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

   private static int findArrayMax(int[] pix) {
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
   
   /**
    * Converts a sequence of 4 bytes into an int.
    * Note that this function assumes bytes to be signed.  Most data
    * coming from MMCore will have unsigned bytes, so used the appropriate 
    * function for that conversion.
    * @param pixels
    * @return 
    */
   public static int[] convertRGB32BytesToInt(byte[] pixels) {
      int[] ints = new int[pixels.length/4];
      for (int i=0; i<ints.length; ++i) {
         ints[i] =  (pixels[4*i])
                 + ( (pixels[4*i + 1]) << 8) 
                 + ( (pixels[4*i + 2]) << 16);
      }
      return ints;
   }

   /**
    * Used to convert unsigned bytes coming from the C++ later into
    * ints that can be used by ImageJ. 
    * @param pixels
    * @return 
    */
   public static int[] convertRGB32UBytesToInt(byte[] pixels) {
      int[] ints = new int[pixels.length/4];
      for (int i=0; i<ints.length; ++i) {
         ints[i] =  (pixels[4*i] & 0xff)
                 + ( (pixels[4*i + 1] & 0xff) << 8) 
                 + ( (pixels[4*i + 2] & 0xff) << 16);
      }
      return ints;
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

      double xn;
      double yn;
      for (int x=0;x<size;++x) {
         xn = x / (double) (size-1);
         yn = Math.pow(xn, gamma);
         rs[x] = (byte) (yn * r);
         gs[x] = (byte) (yn * g);
         bs[x] = (byte) (yn * b);
      }
      return new LUT(8,size,rs,gs,bs);
   }

   public static int unsignedValue(byte b) {
      // Sign-extend, then mask
      return ((int) b) & 0x000000ff;
   }

   public static int unsignedValue(short s) {
      // Sign-extend, then mask
      return ((int) s) & 0x0000ffff;
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

   /**
    * Generate a new TaggedImage off of the provided one, with copied metadata
    * so that changes elsewhere in the program won't affect this one.
    */
   public static TaggedImage copyMetadata(TaggedImage image) {
      JSONArray names = image.tags.names();
      String[] keys = new String[names.length()];
      try {
         for (int j = 0; j < names.length(); ++j) {
            keys[j] = names.getString(j);
         }
         return new TaggedImage(image.pix, new JSONObject(image.tags, keys));
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Unable to duplicate image metadata");
         return null;
      }
   }
   
   /**
    * Executes ImageJ-based Fourier space cross-correlation
    * This should have the exact same result as the ImageJ FFT - FD Math - Correlate
    * command
    * @param proc1 input pixels of first image
    * @param proc2 input pixels of second image
    * @return imageProcessor containing a real space image of the cross-correlation landscape
    */
   
   public static ImageProcessor crossCorrelate(ImageProcessor proc1, ImageProcessor proc2) {
      /*
      ImageGray<? extends ImageGray<?>> bc1 = BoofCVImageConverter.convert(proc1, false);
      ImageGray<? extends ImageGray<?>> bc2 = BoofCVImageConverter.convert(proc2, false);
      bc1 = ImagePadder.padPreibisch(bc1);
      bc2 = ImagePadder.padPreibisch(bc2);
      proc1 = BoofCVImageConverter.convert(bc1, false);
      proc2 = BoofCVImageConverter.convert(bc2, false);
      */
      FHT h1 = new FHT(proc1);
      FHT h2 = new FHT(proc2);
      h1.transform();
      h2.transform();
      // Conjugate multiplication in the frequency domain is equivalent to 
      // correlation in the space domain
      FHT result = h1.conjugateMultiply(h2);   
      result.inverseTransform(); //Transform back to space domain.
      result.swapQuadrants(); //This needs to be done after transforming. to get back to the original.
      result.resetMinAndMax(); //This is just to scale the contrast when displayed.
      return result;
   }


}


