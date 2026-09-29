package org.micromanager.orthogonalviewer;

import java.util.List;
import org.micromanager.data.Image;

/**
 * Static utilities to extract orthogonal slice pixel arrays from a Z-stack.
 *
 * <p>The z-stack is provided as a List&lt;Image&gt; sorted ascending by z index,
 * where index i in the list corresponds to z-slice i.</p>
 */
public final class OrthogonalSliceExtractor {

   private OrthogonalSliceExtractor() {}

   /**
    * Extract an XZ slice as a flat int[] array of raw pixel intensities.
    *
    * <p>Result dimensions: width=imageWidth, height=numZ.
    * result[zIdx * imageWidth + x] = intensity at (x, crosshairY) in z-slice zIdx.
    * Images are looked up by their z-coordinate, so sparse stacks (where list index
    * does not equal z-index) are handled correctly.</p>
    *
    * @param zStack     list of XY images (may be sparse, sorted ascending by z-coord)
    * @param crosshairY Y row to sample from each XY image
    * @param imageWidth pixel width of each XY image
    * @param numZ       total number of z-slices (output height)
    * @return flat pixel array; 0 for missing slices
    */
   public static int[] extractXZ(List<Image> zStack, int crosshairY, int imageWidth, int numZ) {
      int[] result = new int[imageWidth * numZ];
      for (Image img : zStack) {
         if (img == null) {
            continue;
         }
         int z = img.getCoords().getZ();
         if (z < 0 || z >= numZ) {
            continue;
         }
         int row = Math.max(0, Math.min(crosshairY, img.getHeight() - 1));
         for (int x = 0; x < imageWidth; x++) {
            result[z * imageWidth + x] = getPixelValue(img, x, row);
         }
      }
      return result;
   }

   /**
    * Extract a YZ slice as a flat int[] array of raw pixel intensities.
    *
    * <p>Result dimensions: width=numZ, height=imageHeight.
    * result[y * numZ + zIdx] = intensity at (crosshairX, y) in z-slice zIdx.
    * Images are looked up by their z-coordinate, so sparse stacks (where list index
    * does not equal z-index) are handled correctly.</p>
    *
    * @param zStack      list of XY images (may be sparse, sorted ascending by z-coord)
    * @param crosshairX  X column to sample from each XY image
    * @param imageHeight pixel height of each XY image
    * @param numZ        total number of z-slices (output width)
    * @return flat pixel array; 0 for missing slices
    */
   public static int[] extractYZ(List<Image> zStack, int crosshairX, int imageHeight, int numZ) {
      int[] result = new int[numZ * imageHeight];
      for (Image img : zStack) {
         if (img == null) {
            continue;
         }
         int z = img.getCoords().getZ();
         if (z < 0 || z >= numZ) {
            continue;
         }
         int col = Math.max(0, Math.min(crosshairX, img.getWidth() - 1));
         for (int y = 0; y < imageHeight; y++) {
            result[y * numZ + z] = getPixelValue(img, col, y);
         }
      }
      return result;
   }

   /**
    * Extract an XZ float slice from a z-stack of float (GRAY32) images.
    *
    * <p>Result dimensions: width=imageWidth, height=numZ.
    * result[z * imageWidth + x] = float intensity at (x, crosshairY) in z-slice z.</p>
    */
   public static float[] extractXZFloat(List<Image> zStack, int crosshairY,
                                        int imageWidth, int numZ) {
      float[] result = new float[imageWidth * numZ];
      for (Image img : zStack) {
         if (img == null) {
            continue;
         }
         int z = img.getCoords().getZ();
         if (z < 0 || z >= numZ) {
            continue;
         }
         float[] pixels = (float[]) img.getRawPixels();
         int row = Math.max(0, Math.min(crosshairY, img.getHeight() - 1));
         for (int x = 0; x < imageWidth; x++) {
            int offset = row * img.getWidth() + x;
            if (offset < pixels.length) {
               result[z * imageWidth + x] = pixels[offset];
            }
         }
      }
      return result;
   }

   /**
    * Extract a YZ float slice from a z-stack of float (GRAY32) images.
    *
    * <p>Result dimensions: width=numZ, height=imageHeight.
    * result[y * numZ + z] = float intensity at (crosshairX, y) in z-slice z.</p>
    */
   public static float[] extractYZFloat(List<Image> zStack, int crosshairX,
                                        int imageHeight, int numZ) {
      float[] result = new float[numZ * imageHeight];
      for (Image img : zStack) {
         if (img == null) {
            continue;
         }
         int z = img.getCoords().getZ();
         if (z < 0 || z >= numZ) {
            continue;
         }
         float[] pixels = (float[]) img.getRawPixels();
         int col = Math.max(0, Math.min(crosshairX, img.getWidth() - 1));
         for (int y = 0; y < imageHeight; y++) {
            int offset = y * img.getWidth() + col;
            if (offset < pixels.length) {
               result[y * numZ + z] = pixels[offset];
            }
         }
      }
      return result;
   }

   /**
    * Read a single pixel's intensity from an Image, returning a value suitable for LUT scaling.
    *
    * <p>Handles 8-bit (byte[]), 16-bit (short[]), 32-bit RGB (int[]), and 32-bit float (float[])
    * images. For RGB images returns the red component as intensity. For float images returns
    * the raw IEEE 754 bit pattern (use {@code extractXZFloat}/{@code extractYZFloat} instead
    * for float z-stacks).</p>
    */
   public static int getPixelValue(Image img, int x, int y) {
      int w = img.getWidth();
      int offset = y * w + x;
      Object pixels = img.getRawPixels();
      if (pixels instanceof float[]) {
         float[] arr = (float[]) pixels;
         if (offset < arr.length) {
            return Float.floatToRawIntBits(arr[offset]);
         }
      } else if (pixels instanceof short[]) {
         short[] arr = (short[]) pixels;
         if (offset < arr.length) {
            return arr[offset] & 0xFFFF;
         }
      } else if (pixels instanceof byte[]) {
         byte[] arr = (byte[]) pixels;
         if (offset < arr.length) {
            return arr[offset] & 0xFF;
         }
      } else if (pixels instanceof int[]) {
         int[] arr = (int[]) pixels;
         if (offset < arr.length) {
            // RGB packed: extract red component as representative intensity
            return (arr[offset] >> 16) & 0xFF;
         }
      }
      return 0;
   }
}
