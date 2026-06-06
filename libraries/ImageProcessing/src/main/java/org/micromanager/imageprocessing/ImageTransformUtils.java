package org.micromanager.imageprocessing;

import java.awt.geom.AffineTransform;

/**
 * Utility methods for correcting camera orientation in tiled datasets.
 *
 * <p>The {@code pixelSizeAffine} stored in per-image metadata maps
 * <em>camera-pixel displacement → stage displacement (µm)</em>, consistent with
 * how the rest of the codebase uses it (e.g. {@code XYNavigator} and
 * {@code TileCreator} both call {@code affine.transform(pixelDelta, stageDelta)}).</p>
 */
public class ImageTransformUtils {

   private ImageTransformUtils() {}

   /**
    * Derive the correction (mirror + rotation) needed to map camera-space pixels
    * back to stage-space orientation, from the pixelSizeAffine stored in image metadata.
    *
    * <p><b>Affine convention:</b> {@code pixelSizeAffine} maps camera-pixel displacement
    * → stage displacement (µm).  A rotation angle {@code rot} extracted from the affine
    * means the camera's pixel axes are rotated {@code rot} degrees relative to stage
    * space.  Applying that same rotation to the pixel data restores stage orientation:
    * correctionRotation = rot, correctionMirror = mirror.</p>
    *
    * <p><b>Rotation direction:</b> {@link #transformPixels} applies rotations
    * <em>clockwise</em> — a 90° argument rotates the image 90° CW in screen
    * coordinates (origin top-left).</p>
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

      // Mirror = det < 0 (det = m00*m11 - m01*m10): a reflection is present.
      double det = m00 * m11 - m01 * m10;
      boolean mirror = det < 0;

      // Extract rotation from the first column AFTER factoring out the mirror.
      // transformPixels applies "mirror (horizontal flip) then rotate CW"; a horizontal
      // mirror negates the camera x-axis (m00, m10). Un-mirroring before reading atan2
      // yields the canonical (mirror, rotation) pair that transformPixels expects, instead
      // of a rotation that double-counts the reflection.
      double rm00 = mirror ? -m00 : m00;
      double rm10 = mirror ? -m10 : m10;
      double rotRad = Math.atan2(rm10, rm00);
      double rotDeg = Math.toDegrees(rotRad);
      // Normalise to [0, 360)
      rotDeg = ((rotDeg % 360) + 360) % 360;

      // Round to nearest 90°
      int rot = (int) (Math.round(rotDeg / 90.0) * 90) % 360;

      // Correction applies the same rotation to map camera pixels back to stage orientation
      int correctionRot = rot;
      int correctionMirror = mirror ? 1 : 0;

      return new int[]{correctionRot, correctionMirror};
   }

   // -------------------------------------------------------------------------
   // Orientation-operator algebra
   //
   // An orientation operator is "mirror (horizontal flip) first, then rotate
   // clockwise by {@code rotation} degrees", matching {@link #transformPixels}
   // and the Image Flipper (mirror, then rotateRight). It is represented as
   // {@code int[]{rotation (0/90/180/270), mirror (0 or 1)}}, the same encoding
   // returned by {@link #correctionFromAffine}.
   // -------------------------------------------------------------------------

   /**
    * Returns the inverse of a mirror-then-rotate-CW orientation operator.
    *
    * <p>Closed form: {@code mirror} is unchanged; the inverse rotation is
    * {@code mirror ? rotation : (360 - rotation) % 360}.</p>
    *
    * @param rotation rotation in degrees (0/90/180/270)
    * @param mirror   1 if the operator mirrors, 0 otherwise
    * @return int[]{rotation, mirror} of the inverse operator
    */
   public static int[] invertCorrection(int rotation, int mirror) {
      int r = ((rotation % 360) + 360) % 360;
      int m = mirror != 0 ? 1 : 0;
      int invRot = m == 1 ? r : (360 - r) % 360;
      return new int[]{invRot, m};
   }

   /**
    * Composes two mirror-then-rotate-CW orientation operators: the result applies
    * operator B first, then operator A (A after B).
    *
    * <p>Closed form: {@code mirror = mA xor mB}; {@code rotation =
    * mA ? (rA - rB) : (rA + rB)} (mod 360). Verified against the full 8-element
    * operator group by exhaustive 2x2 matrix multiplication.</p>
    *
    * @param rotationA outer operator rotation (applied second)
    * @param mirrorA   outer operator mirror (0/1)
    * @param rotationB inner operator rotation (applied first)
    * @param mirrorB   inner operator mirror (0/1)
    * @return int[]{rotation, mirror} of the composed operator
    */
   public static int[] composeCorrection(int rotationA, int mirrorA,
                                         int rotationB, int mirrorB) {
      int rA = ((rotationA % 360) + 360) % 360;
      int rB = ((rotationB % 360) + 360) % 360;
      int mA = mirrorA != 0 ? 1 : 0;
      int mB = mirrorB != 0 ? 1 : 0;
      int rot = mA == 1 ? (((rA - rB) % 360) + 360) % 360 : (rA + rB) % 360;
      int mirror = mA ^ mB;
      return new int[]{rot, mirror};
   }

   /**
    * Parses Image Flipper metadata into an orientation operator.
    *
    * <p>The Image Flipper writes {@code ImageFlipper-Rotation} (0/90/180/270) and
    * {@code ImageFlipper-Mirror} ("On"/"Off") into per-image user data and has
    * already physically transformed the pixels (mirror, then rotate CW).</p>
    *
    * @param flipRotation the {@code ImageFlipper-Rotation} value
    * @param flipMirror   the {@code ImageFlipper-Mirror} value ("On"/"Off")
    * @return int[]{rotation, mirror}, or null when the operator is the identity
    *         (no rotation and no mirror), i.e. nothing to fold in
    */
   public static int[] flipperFromUserData(int flipRotation, String flipMirror) {
      int rot = ((flipRotation % 360) + 360) % 360;
      int mirror = "On".equals(flipMirror) ? 1 : 0;
      if (rot == 0 && mirror == 0) {
         return null;
      }
      return new int[]{rot, mirror};
   }

   /**
    * Builds the stage-delta -> canvas-pixel-delta matrix {@code M = O * A^-1}, where
    * {@code A} is the 2x2 of the pixelSizeAffine (camera-pixel-delta -> stage-micron-delta)
    * and {@code O} is the 2x2 of the orientation operator (mirror then rotate CW)
    * applied to the tile pixels.
    *
    * <p>Because {@code O} is the orientation extracted from {@code A} (via
    * {@link #correctionFromAffine}), {@code M} is a pure positive-scaled axis-aligned
    * map: {@code +canvasX (right)} increases with the orientation-aligned stage X and
    * {@code +canvasY (down)} with the orientation-aligned stage Y. This is the single
    * authority for placing tiles consistently with the rotated pixel data, under the
    * Micro-Manager convention that the lowest stage (X,Y) is the canvas top-left.</p>
    *
    * @param affine   the pixelSizeAffine (only its 2x2 linear part is used)
    * @param rotation orientation rotation in degrees (0/90/180/270)
    * @param mirror   1 if the orientation mirrors, 0 otherwise
    * @return the 2x2 matrix M in row-major order {m00, m01, m10, m11},
    *         or null if affine is null or singular
    */
   public static double[] stageToCanvasMatrix(AffineTransform affine,
                                              int rotation, boolean mirror) {
      if (affine == null) {
         return null;
      }
      // Inverse of A's 2x2 linear part (camera-pixel-delta from stage-micron-delta).
      double a00 = affine.getScaleX();
      double a10 = affine.getShearY();
      double a01 = affine.getShearX();
      double a11 = affine.getScaleY();
      double det = a00 * a11 - a01 * a10;
      if (det == 0) {
         return null;
      }
      double i00 = a11 / det;
      double i01 = -a01 / det;
      double i10 = -a10 / det;
      double i11 = a00 / det;

      // Orientation operator O = Rcw(rotation) * Mh^mirror (2x2, screen coords y-down).
      double o00;
      double o01;
      double o10;
      double o11;
      switch (((rotation % 360) + 360) % 360) {
         case 90:
            o00 = 0;
            o01 = -1;
            o10 = 1;
            o11 = 0;
            break;
         case 180:
            o00 = -1;
            o01 = 0;
            o10 = 0;
            o11 = -1;
            break;
         case 270:
            o00 = 0;
            o01 = 1;
            o10 = -1;
            o11 = 0;
            break;
         default:
            o00 = 1;
            o01 = 0;
            o10 = 0;
            o11 = 1;
            break;
      }
      if (mirror) {
         // Horizontal mirror negates the x-axis: O * diag(-1, 1) flips column 0.
         o00 = -o00;
         o10 = -o10;
      }

      // M = O * A^-1
      double m00 = o00 * i00 + o01 * i10;
      double m01 = o00 * i01 + o01 * i11;
      double m10 = o10 * i00 + o11 * i10;
      double m11 = o10 * i01 + o11 * i11;
      return new double[]{m00, m01, m10, m11};
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
