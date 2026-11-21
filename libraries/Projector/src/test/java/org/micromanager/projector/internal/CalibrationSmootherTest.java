package org.micromanager.projector.internal;

import java.awt.geom.Point2D;
import org.junit.Assert;
import org.junit.Test;

/**
 * Synthetic-data tests for CalibrationSmoother.
 */
public class CalibrationSmootherTest {

   // affine grid plus a pincushion-like quadratic distortion
   private static Point2D.Double truth(int i, int j, int n) {
      double c = (n - 1) / 2.0;
      double x = 120 + 210 * i + 12 * j + 1.5 * (j - c) * (j - c);
      double y = 90 - 9 * i + 205 * j + 1.2 * (i - c) * (i - c);
      return new Point2D.Double(x, y);
   }

   private static Point2D.Double[][] grid(int n) {
      Point2D.Double[][] g = new Point2D.Double[n][n];
      for (int i = 0; i < n; i++) {
         for (int j = 0; j < n; j++) {
            g[i][j] = truth(i, j, n);
         }
      }
      return g;
   }

   @Test
   public void sigmaZeroReturnsInput() {
      Point2D.Double[][] g = grid(5);
      g[2][2].x += 3;
      CalibrationSmoother.Result r = CalibrationSmoother.smooth(g, 0);
      Assert.assertTrue(r.flagged.isEmpty());
      for (int i = 0; i < 5; i++) {
         for (int j = 0; j < 5; j++) {
            Assert.assertEquals(g[i][j], r.points[i][j]);
         }
      }
   }

   @Test
   public void cleanDistortedGridIsPreserved() {
      for (int n : new int[] {3, 5, 8}) {
         for (double sigma : Calibrator.CALIBRATION_SMOOTHING_CHOICES) {
            Point2D.Double[][] g = grid(n);
            CalibrationSmoother.Result r = CalibrationSmoother.smooth(g, sigma);
            Assert.assertTrue("n=" + n + " sigma=" + sigma, r.flagged.isEmpty());
            for (int i = 0; i < n; i++) {
               for (int j = 0; j < n; j++) {
                  Assert.assertEquals(0.0, r.points[i][j].distance(g[i][j]), 0.05);
               }
            }
         }
      }
   }

   @Test
   public void outlierIsFlaggedAndCorrected() {
      for (int n : new int[] {5, 8}) {
         for (int[] bad : new int[][] {{2, 2}, {0, 3}, {n - 1, n - 1}}) {
            for (double sigma : new double[] {0.5, 1.0, 2.0}) {
               String msg = "n=" + n + " bad=" + bad[0] + "," + bad[1] + " sigma=" + sigma;
               Point2D.Double[][] g = grid(n);
               g[bad[0]][bad[1]] = new Point2D.Double(g[bad[0]][bad[1]].x + 2.4,
                     g[bad[0]][bad[1]].y - 1.8);
               CalibrationSmoother.Result r = CalibrationSmoother.smooth(g, sigma);
               Assert.assertEquals(msg, 1, r.flagged.size());
               Assert.assertEquals(msg, bad[0], r.flagged.get(0).i);
               Assert.assertEquals(msg, bad[1], r.flagged.get(0).j);
               for (int i = 0; i < n; i++) {
                  for (int j = 0; j < n; j++) {
                     double err = r.points[i][j].distance(truth(i, j, n));
                     double tol = (i == bad[0] && j == bad[1]) ? 0.3 : 0.1;
                     Assert.assertEquals(msg + " at " + i + "," + j, 0.0, err, tol);
                  }
               }
            }
         }
      }
   }

   @Test
   public void missingSpotIsTolerated() {
      Point2D.Double[][] g = grid(5);
      g[1][3] = null;
      CalibrationSmoother.Result r = CalibrationSmoother.smooth(g, 1.0);
      Assert.assertNull(r.points[1][3]);
      Assert.assertTrue(r.flagged.isEmpty());
      Assert.assertEquals(0.0, r.points[2][2].distance(g[2][2]), 0.05);
   }

   @Test
   public void twoByTwoGridUsesAffineFallback() {
      Point2D.Double[][] g = grid(2);
      CalibrationSmoother.Result r = CalibrationSmoother.smooth(g, 1.0);
      Assert.assertTrue(r.flagged.isEmpty());
      for (int i = 0; i < 2; i++) {
         for (int j = 0; j < 2; j++) {
            Assert.assertNotNull(r.points[i][j]);
            // 4 points cannot be exactly affine with distortion; just stay close
            Assert.assertEquals(0.0, r.points[i][j].distance(g[i][j]), 2.0);
         }
      }
   }
}
