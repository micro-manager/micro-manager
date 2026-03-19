package org.micromanager.imageprocessing;

import java.awt.geom.AffineTransform;

/**
 * Utility methods for correcting camera orientation in tiled datasets.
 *
 * <p>The affine transform stored in per-image metadata encodes the relationship
 * between stage movement (µm) and camera pixels. This class provides methods to
 * derive the correction needed and apply it to pixel arrays.</p>
 */
public class ImageTransformUtils {

   private ImageTransformUtils() {}

   /**
    * Derive the correction (mirror + rotation) needed to map camera-space pixels
    * back to stage-space orientation, from the pixelSizeAffine stored in image metadata.
    *
    * <p>The affine maps stage displacement (µm) to camera-pixel displacement.
    * To convert camera pixels back to stage orientation we apply the inverse linear
    * transform: correctionRotation = (360 - rot) % 360, correctionMirror = mirror.</p>
    *
    * @param affine the AffineTransform from {@code Metadata.getPixelSizeAffine()},
    *               or null
    * @return int[]{rotation (0/90/180/270), mirror (0 or 1)}, or null if affine is null
    */
   public static int[] correctionFromAffine(AffineTransform affine) {
      if (affine == null) {
         return null;
      }

      // Decompose: A = [[m00, m01], [m10, m11]]
      // xScale = sqrt(m00^2 + m10^2), with sign from m00
      // rotationDeg = atan2(m10, m00) in degrees
      double m00 = affine.getScaleX();   // cos(theta) * xScale
      double m10 = affine.getShearY();   // sin(theta) * xScale
      double m01 = affine.getShearX();   // -sin(theta) * yScale  (or shear)
      double m11 = affine.getScaleY();   // cos(theta) * yScale

      double xScale = Math.sqrt(m00 * m00 + m10 * m10);
      // Preserve sign: if m00 < 0 after factoring out rotation, the x-axis is flipped
      // The signed x-scale is the length with sign = sign(m00) when theta near 0
      // More precisely: xScaleSigned = xScale * sign(m00*cos + m10*sin)
      // For our purposes: mirror = det < 0 (det = m00*m11 - m01*m10)
      double det = m00 * m11 - m01 * m10;
      boolean mirror = det < 0;

      // Rotation from the first column (or after un-mirroring)
      double rotRad = Math.atan2(m10, m00);
      double rotDeg = Math.toDegrees(rotRad);
      // Normalise to [0, 360)
      rotDeg = ((rotDeg % 360) + 360) % 360;

      // Round to nearest 90°
      int rot = (int) (Math.round(rotDeg / 90.0) * 90) % 360;

      // Correction is the inverse: rotation = (360 - rot) % 360
      int correctionRot = (360 - rot) % 360;
      int correctionMirror = mirror ? 1 : 0;

      return new int[]{correctionRot, correctionMirror};
   }

   /**
    * Apply mirror-then-rotate correction to a raw pixel array.
    *
    * <p>Supports {@code short[]} (16-bit gray), {@code byte[]} with
    * {@code bytesPerPixel=1} (8-bit gray), and {@code byte[]} with
    * {@code bytesPerPixel=4} (RGB32 in BGRA order, as stored by Micro-Manager).
    * Mirror is applied first (horizontal flip), then rotation.</p>
    *
    * @param pixels        the raw pixel array ({@code short[]} or {@code byte[]})
    * @param width         image width in pixels
    * @param height        image height in pixels
    * @param bytesPerPixel 1 for 8-bit gray, 2 for 16-bit, 4 for RGB32/BGRA
    * @param mirror        true to apply horizontal mirror before rotation
    * @param rotation      rotation in degrees: 0, 90, 180, or 270
    * @return Object[]{transformedPixels, newWidth (Integer), newHeight (Integer)}
    */
   public static Object[] transformPixels(Object pixels, int width, int height,
                                          int bytesPerPixel, boolean mirror, int rotation) {
      if (pixels instanceof short[]) {
         short[] src = (short[]) pixels;
         if (mirror) {
            src = mirrorHorizontal16(src, width, height);
         }
         return rotate16(src, width, height, rotation);
      } else if (pixels instanceof byte[]) {
         byte[] src = (byte[]) pixels;
         if (bytesPerPixel == 4) {
            // RGB32: 4 bytes per pixel (BGRA order)
            if (mirror) {
               src = mirrorHorizontalBgra(src, width, height);
            }
            return rotateBgra(src, width, height, rotation);
         } else {
            // 8-bit gray: 1 byte per pixel
            if (mirror) {
               src = mirrorHorizontal8(src, width, height);
            }
            return rotate8(src, width, height, rotation);
         }
      } else {
         return new Object[]{pixels, width, height};
      }
   }

   /**
    * Convenience overload for grayscale images (bytesPerPixel derived from array type).
    *
    * <p>For {@code short[]}: bytesPerPixel=2. For {@code byte[]}: bytesPerPixel=1.
    * Use the full overload for RGB32 ({@code byte[]}, bytesPerPixel=4).</p>
    */
   public static Object[] transformPixels(Object pixels, int width, int height,
                                          boolean mirror, int rotation) {
      int bpp = (pixels instanceof short[]) ? 2 : 1;
      return transformPixels(pixels, width, height, bpp, mirror, rotation);
   }

   // -------------------------------------------------------------------------
   // 16-bit helpers
   // -------------------------------------------------------------------------

   private static short[] mirrorHorizontal16(short[] src, int width, int height) {
      short[] dst = new short[src.length];
      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            dst[y * width + x] = src[y * width + (width - 1 - x)];
         }
      }
      return dst;
   }

   private static Object[] rotate16(short[] src, int width, int height, int rotation) {
      switch (((rotation % 360) + 360) % 360) {
         case 0:
            return new Object[]{src.clone(), width, height};
         case 90: {
            // 90° CW: dst(x, y) = src(y, width-1-x), new dims = (height, width)
            int newW = height;
            int newH = width;
            short[] dst = new short[newW * newH];
            for (int y = 0; y < newH; y++) {
               for (int x = 0; x < newW; x++) {
                  dst[y * newW + x] = src[y + (newW - 1 - x) * width];
               }
            }
            return new Object[]{dst, newW, newH};
         }
         case 180: {
            short[] dst = new short[src.length];
            int n = src.length;
            for (int i = 0; i < n; i++) {
               dst[i] = src[n - 1 - i];
            }
            return new Object[]{dst, width, height};
         }
         case 270: {
            // 270° CW: dst(x, y) = src(height-1-y, x), new dims = (height, width)
            int newW = height;
            int newH = width;
            short[] dst = new short[newW * newH];
            for (int y = 0; y < newH; y++) {
               for (int x = 0; x < newW; x++) {
                  dst[y * newW + x] = src[(newH - 1 - y) + x * width];
               }
            }
            return new Object[]{dst, newW, newH};
         }
         default:
            return new Object[]{src.clone(), width, height};
      }
   }

   // -------------------------------------------------------------------------
   // 8-bit gray helpers
   // -------------------------------------------------------------------------

   private static byte[] mirrorHorizontal8(byte[] src, int width, int height) {
      byte[] dst = new byte[src.length];
      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            dst[y * width + x] = src[y * width + (width - 1 - x)];
         }
      }
      return dst;
   }

   private static Object[] rotate8(byte[] src, int width, int height, int rotation) {
      switch (((rotation % 360) + 360) % 360) {
         case 0:
            return new Object[]{src.clone(), width, height};
         case 90: {
            int newW = height;
            int newH = width;
            byte[] dst = new byte[newW * newH];
            for (int y = 0; y < newH; y++) {
               for (int x = 0; x < newW; x++) {
                  dst[y * newW + x] = src[y + (newW - 1 - x) * width];
               }
            }
            return new Object[]{dst, newW, newH};
         }
         case 180: {
            byte[] dst = new byte[src.length];
            int n = src.length;
            for (int i = 0; i < n; i++) {
               dst[i] = src[n - 1 - i];
            }
            return new Object[]{dst, width, height};
         }
         case 270: {
            int newW = height;
            int newH = width;
            byte[] dst = new byte[newW * newH];
            for (int y = 0; y < newH; y++) {
               for (int x = 0; x < newW; x++) {
                  dst[y * newW + x] = src[(newH - 1 - y) + x * width];
               }
            }
            return new Object[]{dst, newW, newH};
         }
         default:
            return new Object[]{src.clone(), width, height};
      }
   }

   // -------------------------------------------------------------------------
   // RGB32 / BGRA helpers (4 bytes per pixel, byte[] storage)
   // -------------------------------------------------------------------------

   private static byte[] mirrorHorizontalBgra(byte[] src, int width, int height) {
      byte[] dst = new byte[src.length];
      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            int srcOff = (y * width + x) * 4;
            int dstOff = (y * width + (width - 1 - x)) * 4;
            dst[dstOff]     = src[srcOff];
            dst[dstOff + 1] = src[srcOff + 1];
            dst[dstOff + 2] = src[srcOff + 2];
            dst[dstOff + 3] = src[srcOff + 3];
         }
      }
      return dst;
   }

   private static Object[] rotateBgra(byte[] src, int width, int height, int rotation) {
      switch (((rotation % 360) + 360) % 360) {
         case 0:
            return new Object[]{src.clone(), width, height};
         case 90: {
            // 90° CW: dst pixel (x,y) comes from src pixel (y, width-1-x)
            int newW = height;
            int newH = width;
            byte[] dst = new byte[src.length];
            for (int y = 0; y < newH; y++) {
               for (int x = 0; x < newW; x++) {
                  int srcPixel = y + (newW - 1 - x) * width;
                  int dstPixel = y * newW + x;
                  System.arraycopy(src, srcPixel * 4, dst, dstPixel * 4, 4);
               }
            }
            return new Object[]{dst, newW, newH};
         }
         case 180: {
            // 180°: reverse pixel order
            byte[] dst = new byte[src.length];
            int nPix = width * height;
            for (int i = 0; i < nPix; i++) {
               System.arraycopy(src, i * 4, dst, (nPix - 1 - i) * 4, 4);
            }
            return new Object[]{dst, width, height};
         }
         case 270: {
            // 270° CW: dst pixel (x,y) comes from src pixel (height-1-y, x)
            int newW = height;
            int newH = width;
            byte[] dst = new byte[src.length];
            for (int y = 0; y < newH; y++) {
               for (int x = 0; x < newW; x++) {
                  int srcPixel = (newH - 1 - y) + x * width;
                  int dstPixel = y * newW + x;
                  System.arraycopy(src, srcPixel * 4, dst, dstPixel * 4, 4);
               }
            }
            return new Object[]{dst, newW, newH};
         }
         default:
            return new Object[]{src.clone(), width, height};
      }
   }
}
