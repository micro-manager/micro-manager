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
    * <p>Supports {@code short[]} (16-bit) and {@code byte[]} (8-bit) pixel arrays.
    * Mirror is applied first (horizontal flip), then rotation.</p>
    *
    * @param pixels   the raw pixel array ({@code short[]} or {@code byte[]})
    * @param width    image width in pixels
    * @param height   image height in pixels
    * @param mirror   true to apply horizontal mirror before rotation
    * @param rotation rotation in degrees: 0, 90, 180, or 270
    * @return Object[]{transformedPixels, newWidth (Integer), newHeight (Integer)}
    */
   public static Object[] transformPixels(Object pixels, int width, int height,
                                          boolean mirror, int rotation) {
      if (pixels instanceof short[]) {
         short[] src = (short[]) pixels;
         if (mirror) {
            src = mirrorHorizontal16(src, width, height);
         }
         Object[] rotResult = rotate16(src, width, height, rotation);
         return rotResult;
      } else if (pixels instanceof byte[]) {
         byte[] src = (byte[]) pixels;
         if (mirror) {
            src = mirrorHorizontal8(src, width, height);
         }
         Object[] rotResult = rotate8(src, width, height, rotation);
         return rotResult;
      } else {
         return new Object[]{pixels, width, height};
      }
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
   // 8-bit helpers
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
}
