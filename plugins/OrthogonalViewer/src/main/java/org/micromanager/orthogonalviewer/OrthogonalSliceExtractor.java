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
    * result[z * imageWidth + x] = intensity at (x, crosshairY) in z-slice z.</p>
    *
    * @param zStack     list of XY images sorted by z (index 0 = z=0)
    * @param crosshairY Y row to sample from each XY image
    * @param imageWidth pixel width of each XY image
    * @return flat pixel array; 0 for missing slices
    */
   public static int[] extractXZ(List<Image> zStack, int crosshairY, int imageWidth) {
      int numZ = zStack.size();
      int[] result = new int[imageWidth * numZ];
      for (int z = 0; z < numZ; z++) {
         Image img = zStack.get(z);
         if (img == null) {
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
    * result[y * numZ + z] = intensity at (crosshairX, y) in z-slice z.</p>
    *
    * @param zStack      list of XY images sorted by z (index 0 = z=0)
    * @param crosshairX  X column to sample from each XY image
    * @param imageHeight pixel height of each XY image
    * @return flat pixel array; 0 for missing slices
    */
   public static int[] extractYZ(List<Image> zStack, int crosshairX, int imageHeight) {
      int numZ = zStack.size();
      int[] result = new int[numZ * imageHeight];
      for (int z = 0; z < numZ; z++) {
         Image img = zStack.get(z);
         if (img == null) {
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
    * Read a single pixel's intensity from an Image, returning an unsigned int.
    *
    * <p>Handles 8-bit (byte[]), 16-bit (short[]), and 32-bit RGB (int[]) images.
    * For RGB images returns the first component (red channel) as the intensity.</p>
    */
   public static int getPixelValue(Image img, int x, int y) {
      int w = img.getWidth();
      int offset = y * w + x;
      Object pixels = img.getRawPixels();
      if (pixels instanceof short[]) {
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
