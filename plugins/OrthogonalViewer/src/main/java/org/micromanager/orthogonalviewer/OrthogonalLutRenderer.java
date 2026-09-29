package org.micromanager.orthogonalviewer;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.List;
import org.micromanager.data.Image;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.ComponentDisplaySettings;
import org.micromanager.display.DisplaySettings;

/**
 * Static utilities for applying brightness/contrast LUT to pixel arrays and rendering
 * to {@link BufferedImage}.
 */
public final class OrthogonalLutRenderer {

   private OrthogonalLutRenderer() {}

   /**
    * Render a flat pixel array to a BufferedImage applying per-channel LUT.
    *
    * <p>For grayscale mode, renders with the channel's color tint.
    * For multi-channel composite, this must be called per-channel and composited externally.</p>
    *
    * @param pixels       flat array of raw (unsigned) pixel intensities
    * @param width        image width in pixels
    * @param height       image height in pixels
    * @param minIntensity black point
    * @param maxIntensity white point
    * @param gamma        gamma exponent (1.0 = linear)
    * @param channelColor color tint to apply (use Color.WHITE for grayscale)
    * @return ARGB BufferedImage
    */
   public static BufferedImage render(int[] pixels, int width, int height,
                                      long minIntensity, long maxIntensity, double gamma,
                                      Color channelColor) {
      BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      int[] outPixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();

      float r = channelColor.getRed() / 255f;
      float g = channelColor.getGreen() / 255f;
      float b = channelColor.getBlue() / 255f;

      long range = maxIntensity - minIntensity;
      if (range <= 0) {
         range = 1;
      }

      for (int i = 0; i < pixels.length; i++) {
         double normalized = (pixels[i] - minIntensity) / (double) range;
         if (normalized < 0.0) {
            normalized = 0.0;
         } else if (normalized > 1.0) {
            normalized = 1.0;
         }
         if (gamma != 1.0) {
            normalized = Math.pow(normalized, gamma);
         }
         int level = (int) (normalized * 255.0 + 0.5);
         outPixels[i] = (0xFF << 24)
               | ((int) (level * r) << 16)
               | ((int) (level * g) << 8)
               | (int) (level * b);
      }
      return img;
   }

   /**
    * Render a set of per-channel pixel arrays into a single composite BufferedImage.
    *
    * <p>Each channel is rendered individually and alpha-composited (SRC_OVER) onto
    * a black background. Uses display settings for per-channel LUT parameters.</p>
    *
    * @param channelPixels list of flat pixel arrays, one per channel (may have null gaps)
    * @param width         image width
    * @param height        image height
    * @param settings      current display settings
    * @return composite ARGB BufferedImage
    */
   public static BufferedImage renderComposite(List<int[]> channelPixels,
                                               int width, int height,
                                               DisplaySettings settings) {
      int nPix = width * height;
      int[] accR = new int[nPix];
      int[] accG = new int[nPix];
      int[] accB = new int[nPix];

      int numChannels = channelPixels.size();
      for (int ch = 0; ch < numChannels; ch++) {
         int[] pixels = channelPixels.get(ch);
         if (pixels == null) {
            continue;
         }
         ChannelDisplaySettings cs = settings.getChannelSettings(ch);
         if (!cs.isVisible()) {
            continue;
         }
         ComponentDisplaySettings comp = cs.getComponentSettings(0);
         long min = comp.getScalingMinimum();
         long max = comp.getScalingMaximum();
         double gamma = comp.getScalingGamma();
         Color color = cs.getColor();

         float r = color.getRed() / 255f;
         float g = color.getGreen() / 255f;
         float b = color.getBlue() / 255f;

         long range = max - min;
         if (range <= 0) {
            range = 1;
         }

         for (int i = 0; i < nPix; i++) {
            double normalized = (pixels[i] - min) / (double) range;
            if (normalized < 0.0) {
               normalized = 0.0;
            } else if (normalized > 1.0) {
               normalized = 1.0;
            }
            if (gamma != 1.0) {
               normalized = Math.pow(normalized, gamma);
            }
            int level = (int) (normalized * 255.0 + 0.5);
            // Additive blending: accumulate each channel's contribution
            accR[i] += (int) (level * r + 0.5);
            accG[i] += (int) (level * g + 0.5);
            accB[i] += (int) (level * b + 0.5);
         }
      }

      // Clamp and pack into output image
      BufferedImage composite = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      int[] outPixels = ((DataBufferInt) composite.getRaster().getDataBuffer()).getData();
      for (int i = 0; i < nPix; i++) {
         int rv = Math.min(255, accR[i]);
         int gv = Math.min(255, accG[i]);
         int bv = Math.min(255, accB[i]);
         outPixels[i] = (0xFF << 24) | (rv << 16) | (gv << 8) | bv;
      }
      return composite;
   }

   /**
    * Render a single XY {@link Image} to a BufferedImage using the given display settings.
    *
    * <p>Single-channel images use the first channel's LUT; multi-channel composite
    * images render all visible channels composited together.</p>
    *
    * @param images   list of Images (one per channel) at the current position
    * @param settings current display settings
    * @param width    expected image width
    * @param height   expected image height
    * @return rendered ARGB BufferedImage
    */
   public static BufferedImage renderXY(List<Image> images, DisplaySettings settings,
                                        int width, int height) {
      int numChannels = images.size();
      if (numChannels == 0) {
         return makeBlack(width, height);
      }

      if (numChannels == 1) {
         Image img = images.get(0);
         if (img == null) {
            return makeBlack(width, height);
         }
         ChannelDisplaySettings cs = settings.getChannelSettings(0);
         ComponentDisplaySettings comp = cs.getComponentSettings(0);
         Object raw = img.getRawPixels();
         int[] pixels = toIntArray(raw, width * height);
         return render(pixels, width, height,
               comp.getScalingMinimum(), comp.getScalingMaximum(),
               comp.getScalingGamma(), cs.getColor());
      }

      // Multi-channel composite
      java.util.List<int[]> channelPixels = new java.util.ArrayList<int[]>();
      for (int ch = 0; ch < numChannels; ch++) {
         Image img = images.get(ch);
         if (img == null) {
            channelPixels.add(null);
         } else {
            channelPixels.add(toIntArray(img.getRawPixels(), width * height));
         }
      }
      return renderComposite(channelPixels, width, height, settings);
   }

   /**
    * Render a flat float pixel array to a BufferedImage, applying a linear LUT
    * with actual float min/max values (not bin indices).
    *
    * @param floatPixels  flat array of actual float pixel values
    * @param width        image width
    * @param height       image height
    * @param fMin         black point (actual float pixel value)
    * @param fMax         white point (actual float pixel value)
    * @param gamma        gamma exponent
    * @param channelColor color tint
    * @return ARGB BufferedImage
    */
   public static BufferedImage renderFloat(float[] floatPixels, int width, int height,
                                           double fMin, double fMax, double gamma,
                                           Color channelColor) {
      BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      int[] outPixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();

      float r = channelColor.getRed() / 255f;
      float g = channelColor.getGreen() / 255f;
      float b = channelColor.getBlue() / 255f;

      double range = fMax - fMin;
      if (range <= 0.0) {
         range = 1.0;
      }

      for (int i = 0; i < floatPixels.length; i++) {
         double normalized = (floatPixels[i] - fMin) / range;
         if (normalized < 0.0) {
            normalized = 0.0;
         } else if (normalized > 1.0) {
            normalized = 1.0;
         }
         if (gamma != 1.0) {
            normalized = Math.pow(normalized, gamma);
         }
         int level = (int) (normalized * 255.0 + 0.5);
         outPixels[i] = (0xFF << 24)
               | ((int) (level * r) << 16)
               | ((int) (level * g) << 8)
               | (int) (level * b);
      }
      return img;
   }

   /**
    * Convert a raw pixel array (byte[], short[], or int[]) to an int[] of values
    * suitable for LUT scaling. Float arrays are not supported here; use
    * {@link #renderFloat} instead for GRAY32 images.
    */
   public static int[] toIntArray(Object raw, int expectedSize) {
      if (raw instanceof float[]) {
         // Float images should be rendered via renderFloat(), not this path.
         // As a fallback, clamp to [0, 65535] by treating each float as-is.
         float[] arr = (float[]) raw;
         int[] result = new int[arr.length];
         for (int i = 0; i < arr.length; i++) {
            result[i] = Float.floatToRawIntBits(arr[i]);
         }
         return result;
      } else if (raw instanceof int[]) {
         int[] arr = (int[]) raw;
         // RGB: extract red as intensity
         int[] result = new int[arr.length];
         for (int i = 0; i < arr.length; i++) {
            result[i] = (arr[i] >> 16) & 0xFF;
         }
         return result;
      } else if (raw instanceof short[]) {
         short[] arr = (short[]) raw;
         int[] result = new int[arr.length];
         for (int i = 0; i < arr.length; i++) {
            result[i] = arr[i] & 0xFFFF;
         }
         return result;
      } else if (raw instanceof byte[]) {
         byte[] arr = (byte[]) raw;
         int[] result = new int[arr.length];
         for (int i = 0; i < arr.length; i++) {
            result[i] = arr[i] & 0xFF;
         }
         return result;
      }
      return new int[expectedSize];
   }

   private static BufferedImage makeBlack(int width, int height) {
      return new BufferedImage(Math.max(1, width), Math.max(1, height),
            BufferedImage.TYPE_INT_ARGB);
   }
}
