package org.micromanager.imageprocessing;

import java.awt.geom.AffineTransform;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the orientation math in {@link ImageTransformUtils}: affine decomposition,
 * the orientation-operator algebra (compose/invert), Image Flipper folding, and the
 * stage-to-canvas placement matrix.
 */
public class ImageTransformUtilsTest {

   private static final double EPS = 1e-9;

   // ----------------------------------------------------------------------
   // correctionFromAffine
   // ----------------------------------------------------------------------

   /** The real-world example: A = diag(-0.324, -0.324) => 180 rotation, no mirror. */
   @Test
   public void diagNegativeIs180NoMirror() {
      AffineTransform a = new AffineTransform(
            -0.3239979811076061, -7.330948792043812e-4,   // m00, m10
            6.187140617613031e-4, -0.32369706791816655,    // m01, m11
            0, 0);
      int[] c = ImageTransformUtils.correctionFromAffine(a);
      Assert.assertNotNull(c);
      Assert.assertEquals(180, c[0]);
      Assert.assertEquals(0, c[1]);
   }

   /** Identity-scale affine => no rotation, no mirror. */
   @Test
   public void positiveDiagIsZeroNoMirror() {
      AffineTransform a = new AffineTransform(0.5, 0, 0, 0.5, 0, 0);
      int[] c = ImageTransformUtils.correctionFromAffine(a);
      Assert.assertEquals(0, c[0]);
      Assert.assertEquals(0, c[1]);
   }

   /** A pure horizontal mirror (negative x-scale only) => mirror, no rotation. */
   @Test
   public void negativeXScaleIsMirrorNoRotation() {
      AffineTransform a = new AffineTransform(-0.5, 0, 0, 0.5, 0, 0);
      int[] c = ImageTransformUtils.correctionFromAffine(a);
      // De-mirroring the first column before atan2 must yield rotation 0, not 180.
      Assert.assertEquals(0, c[0]);
      Assert.assertEquals(1, c[1]);
   }

   /** A 90 deg rotation (no mirror): A = [[0,-s],[s,0]] in (m00,m10,m01,m11) order. */
   @Test
   public void rotation90NoMirror() {
      double s = 0.5;
      AffineTransform a = new AffineTransform(0, s, -s, 0, 0, 0);
      int[] c = ImageTransformUtils.correctionFromAffine(a);
      Assert.assertEquals(90, c[0]);
      Assert.assertEquals(0, c[1]);
   }

   @Test
   public void nullAffineReturnsNull() {
      Assert.assertNull(ImageTransformUtils.correctionFromAffine(null));
   }

   // ----------------------------------------------------------------------
   // invertCorrection / composeCorrection
   // ----------------------------------------------------------------------

   /** Composing an operator with its inverse yields the identity, for all 8 operators. */
   @Test
   public void invertThenComposeIsIdentity() {
      int[] rots = {0, 90, 180, 270};
      for (int m = 0; m <= 1; m++) {
         for (int r : rots) {
            int[] inv = ImageTransformUtils.invertCorrection(r, m);
            // result = inv applied after op  => compose(inv, op)
            int[] id = ImageTransformUtils.composeCorrection(inv[0], inv[1], r, m);
            Assert.assertEquals("rot for r=" + r + " m=" + m, 0, id[0]);
            Assert.assertEquals("mirror for r=" + r + " m=" + m, 0, id[1]);
            // and op after inv as well
            int[] id2 = ImageTransformUtils.composeCorrection(r, m, inv[0], inv[1]);
            Assert.assertEquals(0, id2[0]);
            Assert.assertEquals(0, id2[1]);
         }
      }
   }

   @Test
   public void invertKnownValues() {
      Assert.assertArrayEquals(new int[]{270, 0},
            ImageTransformUtils.invertCorrection(90, 0));
      Assert.assertArrayEquals(new int[]{90, 0},
            ImageTransformUtils.invertCorrection(270, 0));
      Assert.assertArrayEquals(new int[]{180, 0},
            ImageTransformUtils.invertCorrection(180, 0));
      // Mirror operators are their own rotation under inversion.
      Assert.assertArrayEquals(new int[]{90, 1},
            ImageTransformUtils.invertCorrection(90, 1));
   }

   @Test
   public void composeNonMirrorAddsRotations() {
      Assert.assertArrayEquals(new int[]{270, 0},
            ImageTransformUtils.composeCorrection(180, 0, 90, 0));
      Assert.assertArrayEquals(new int[]{0, 0},
            ImageTransformUtils.composeCorrection(270, 0, 90, 0));
   }

   // ----------------------------------------------------------------------
   // flipperFromUserData
   // ----------------------------------------------------------------------

   @Test
   public void flipperIdentityIsNull() {
      Assert.assertNull(ImageTransformUtils.flipperFromUserData(0, "Off"));
      Assert.assertNull(ImageTransformUtils.flipperFromUserData(0, null));
   }

   @Test
   public void flipperParsesRotationAndMirror() {
      Assert.assertArrayEquals(new int[]{180, 0},
            ImageTransformUtils.flipperFromUserData(180, "Off"));
      Assert.assertArrayEquals(new int[]{90, 1},
            ImageTransformUtils.flipperFromUserData(90, "On"));
      Assert.assertArrayEquals(new int[]{0, 1},
            ImageTransformUtils.flipperFromUserData(0, "On"));
   }

   /**
    * Folding rule: when the Image Flipper already applied operator O in-acquisition,
    * pixelOp = O after flipper^-1 must collapse to identity (no double correction).
    */
   @Test
   public void flipperFoldingCollapsesToIdentity() {
      int[] o = {180, 0};                       // affine says rotate 180
      int[] flip = {180, 0};                    // flipper already rotated 180
      int[] flipInv = ImageTransformUtils.invertCorrection(flip[0], flip[1]);
      int[] pixelOp = ImageTransformUtils.composeCorrection(
            o[0], o[1], flipInv[0], flipInv[1]);
      Assert.assertEquals(0, pixelOp[0]);
      Assert.assertEquals(0, pixelOp[1]);
   }

   // ----------------------------------------------------------------------
   // stageToCanvasMatrix
   // ----------------------------------------------------------------------

   /** For the example affine + its derived 180 operator, M is a positive diagonal. */
   @Test
   public void stageToCanvasIsPositiveDiagonalForExample() {
      AffineTransform a = new AffineTransform(
            -0.3239979811076061, -7.330948792043812e-4,
            6.187140617613031e-4, -0.32369706791816655,
            0, 0);
      int[] c = ImageTransformUtils.correctionFromAffine(a);
      double[] m = ImageTransformUtils.stageToCanvasMatrix(a, c[0], c[1] != 0);
      Assert.assertNotNull(m);
      // Diagonal positive (~ +1/0.324 = +3.086), off-diagonal ~ 0.
      Assert.assertTrue("m00>0", m[0] > 0);
      Assert.assertTrue("m11>0", m[3] > 0);
      Assert.assertEquals(3.086, m[0], 0.01);
      Assert.assertEquals(3.086, m[3], 0.01);
      Assert.assertEquals(0.0, m[1], 0.02);
      Assert.assertEquals(0.0, m[2], 0.02);
   }

   /**
    * Canonical convention check: the tile with the lowest stage (X,Y) must map to the
    * top-left of the canvas (smallest canvas X and Y) after applying M.
    */
   @Test
   public void lowestStageMapsToTopLeft() {
      AffineTransform a = new AffineTransform(
            -0.3239979811076061, -7.330948792043812e-4,
            6.187140617613031e-4, -0.32369706791816655,
            0, 0);
      int[] c = ImageTransformUtils.correctionFromAffine(a);
      double[] m = ImageTransformUtils.stageToCanvasMatrix(a, c[0], c[1] != 0);

      // Two tiles from the real dataset: (r0c0) high X,Y vs (r3c3) low X,Y.
      double[] hi = applyM(m, 844.11, 847.95);
      double[] lo = applyM(m, -844.71, -846.35);
      // The lowest-stage tile (lo) must be at smaller canvas X and Y (top-left).
      Assert.assertTrue("canvas X: low-stage left of high-stage", lo[0] < hi[0]);
      Assert.assertTrue("canvas Y: low-stage above high-stage", lo[1] < hi[1]);
   }

   @Test
   public void singularAffineReturnsNull() {
      AffineTransform a = new AffineTransform(0, 0, 0, 0, 0, 0);
      Assert.assertNull(ImageTransformUtils.stageToCanvasMatrix(a, 0, false));
      Assert.assertNull(ImageTransformUtils.stageToCanvasMatrix(null, 0, false));
   }

   private static double[] applyM(double[] m, double x, double y) {
      return new double[]{m[0] * x + m[1] * y, m[2] * x + m[3] * y};
   }
}
