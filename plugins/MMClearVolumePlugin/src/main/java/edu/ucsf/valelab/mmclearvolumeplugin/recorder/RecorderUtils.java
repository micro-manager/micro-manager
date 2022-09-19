
package edu.ucsf.valelab.mmclearvolumeplugin.recorder;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

/**
 *
 * @author nico
 */
public class RecorderUtils {
   public static BufferedImage makeBufferedImage(
                                 final int pWidth,
                                 final int pHeight,
                                 final ByteBuffer pByteBuffer) {
      try {
         int[] lPixelInts = new int[pWidth * pHeight];

         // Convert RGB bytes to ARGB ints with no transparency. Flip image
         // vertically by reading the rows of pixels in the byte buffer
         // in reverse - (0,0) is at bottom left in OpenGL.

         int p = pWidth * pHeight * 3; // Points to first byte (red) in each row
         int q; // Index into ByteBuffer
         int i = 0; // Index into target int[]
         final int w3 = pWidth * 3; // Number of bytes in each row

         for (int row = 0; row < pHeight; row++) {
            p -= w3;
            q = p;
            for (int col = 0; col < pWidth; col++) {
               final int iR = pByteBuffer.get(q++);
               final int iG = pByteBuffer.get(q++);
               final int iB = pByteBuffer.get(q++);

               lPixelInts[i++] = 0xFF000000 | ((iR & 0x000000FF) << 16)
                            | ((iG & 0x000000FF) << 8)
                            | (iB & 0x000000FF);
            }
         }
         BufferedImage lBufferedImage =
                           new BufferedImage(pWidth,
                                             pHeight,
                                             BufferedImage.TYPE_INT_ARGB);
         lBufferedImage.setRGB(0,
                                0,
                                pWidth,
                                pHeight,
                                lPixelInts,
                            0,
                            pWidth);
         return lBufferedImage;
      } catch (final Throwable e) {
         e.printStackTrace();
      }
    
      return null;
   }
}