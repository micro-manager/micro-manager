package org.micromanager.projector.internal;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SingularMatrixException;

/**
 * Robust smoothing of the measured camera positions of a regular grid of calibration spots.
 *
 * <p>The projector positions of the spots are exact and lie on a regular grid, so all
 * measurement error is in the camera positions. Each camera position is replaced by a
 * Gaussian-weighted local quadratic fit (in grid index units) over its neighbours. Spots that
 * disagree with the prediction from their neighbours (leave-one-out) are down-weighted with a
 * Tukey biweight and, when rejected completely, reported as flagged.
 */
final class CalibrationSmoother {

   private static final double TUKEY_C = 4.685;
   // floor for the robust residual scale, so near-perfect data does not flag tiny deviations
   private static final double MIN_SCALE_PX = 0.25;
   private static final int ROBUST_ITERATIONS = 3;
   // below this many measured spots there are too few neighbours to judge outliers
   private static final int MIN_SPOTS_FOR_REJECTION = 9;
   private static final double MIN_WEIGHT = 1e-6;

   private CalibrationSmoother() {
   }

   /**
    * A calibration spot that was rejected because it disagrees with its neighbours.
    */
   static final class FlaggedSpot {
      final int i;
      final int j;
      final Point2D.Double measured;
      final Point2D.Double predicted;

      FlaggedSpot(int i, int j, Point2D.Double measured, Point2D.Double predicted) {
         this.i = i;
         this.j = j;
         this.measured = measured;
         this.predicted = predicted;
      }

      double deviation() {
         return measured.distance(predicted);
      }
   }

   /**
    * Smoothed grid (same shape as the input, null where the input was null) and flagged spots.
    */
   static final class Result {
      final Point2D.Double[][] points;
      final List<FlaggedSpot> flagged;

      Result(Point2D.Double[][] points, List<FlaggedSpot> flagged) {
         this.points = points;
         this.flagged = flagged;
      }
   }

   /**
    * Smooths the camera positions cam[i][j] of the calibration grid.
    *
    * @param cam   measured camera positions; entries may be null (spot not found)
    * @param sigma width of the Gaussian neighbourhood weight in grid spacings; 0 or less
    *              returns copies of the input unchanged
    */
   static Result smooth(Point2D.Double[][] cam, double sigma) {
      final int ni = cam.length;
      final int nj = ni == 0 ? 0 : cam[0].length;
      Point2D.Double[][] out = new Point2D.Double[ni][nj];
      if (sigma <= 0) {
         for (int i = 0; i < ni; i++) {
            for (int j = 0; j < nj; j++) {
               out[i][j] = cam[i][j] == null ? null : new Point2D.Double(cam[i][j].x, cam[i][j].y);
            }
         }
         return new Result(out, Collections.<FlaggedSpot>emptyList());
      }

      double[][] robust = new double[ni][nj];
      int nValid = 0;
      for (int i = 0; i < ni; i++) {
         for (int j = 0; j < nj; j++) {
            if (cam[i][j] != null) {
               robust[i][j] = 1.0;
               nValid++;
            }
         }
      }

      if (nValid >= MIN_SPOTS_FOR_REJECTION) {
         for (int iter = 0; iter < ROBUST_ITERATIONS; iter++) {
            double[][] residual = new double[ni][nj];
            List<Double> finite = new ArrayList<>();
            for (int i = 0; i < ni; i++) {
               for (int j = 0; j < nj; j++) {
                  residual[i][j] = Double.NaN;
                  if (cam[i][j] == null) {
                     continue;
                  }
                  Point2D.Double p = fit(cam, robust, sigma, i, j, true);
                  if (p != null) {
                     residual[i][j] = p.distance(cam[i][j]);
                     finite.add(residual[i][j]);
                  }
               }
            }
            if (finite.isEmpty()) {
               break;
            }
            final double scale = Math.max(MIN_SCALE_PX, 1.4826 * median(finite));
            for (int i = 0; i < ni; i++) {
               for (int j = 0; j < nj; j++) {
                  if (cam[i][j] != null && !Double.isNaN(residual[i][j])) {
                     double u = residual[i][j] / (TUKEY_C * scale);
                     robust[i][j] = u < 1.0 ? (1 - u * u) * (1 - u * u) : 0.0;
                  }
               }
            }
         }
      }

      List<FlaggedSpot> flagged = new ArrayList<>();
      for (int i = 0; i < ni; i++) {
         for (int j = 0; j < nj; j++) {
            if (cam[i][j] == null) {
               continue;
            }
            Point2D.Double p = fit(cam, robust, sigma, i, j, false);
            out[i][j] = p != null ? p : new Point2D.Double(cam[i][j].x, cam[i][j].y);
            if (robust[i][j] == 0.0) {
               flagged.add(new FlaggedSpot(i, j, cam[i][j], out[i][j]));
            }
         }
      }
      return new Result(out, flagged);
   }

   /**
    * Weighted least-squares fit of a local quadratic (or affine, when there are too few
    * distinct rows/columns) around node (i0, j0), evaluated at that node.
    *
    * @return the fitted position, or null if there is not enough data for a fit
    */
   private static Point2D.Double fit(Point2D.Double[][] cam, double[][] robust, double sigma,
         int i0, int j0, boolean leaveOut) {
      final double twoSigmaSq = 2 * sigma * sigma;
      List<double[]> rows = new ArrayList<>();
      boolean[] usedI = new boolean[cam.length];
      boolean[] usedJ = new boolean[cam[0].length];
      for (int i = 0; i < cam.length; i++) {
         for (int j = 0; j < cam[i].length; j++) {
            if (cam[i][j] == null || (leaveOut && i == i0 && j == j0)) {
               continue;
            }
            double u = i - i0;
            double v = j - j0;
            double w = Math.exp(-(u * u + v * v) / twoSigmaSq) * robust[i][j];
            if (w < MIN_WEIGHT) {
               continue;
            }
            rows.add(new double[] {u, v, Math.sqrt(w), cam[i][j].x, cam[i][j].y});
            usedI[i] = true;
            usedJ[j] = true;
         }
      }
      final int distinctI = count(usedI);
      final int distinctJ = count(usedJ);
      Point2D.Double p = null;
      if (rows.size() >= 6 && distinctI >= 3 && distinctJ >= 3) {
         p = solve(rows, true);
      }
      if (p == null && rows.size() >= 3 && distinctI >= 2 && distinctJ >= 2) {
         p = solve(rows, false);
      }
      return p;
   }

   private static Point2D.Double solve(List<double[]> rows, boolean quadratic) {
      final int nParams = quadratic ? 6 : 3;
      RealMatrix a = new Array2DRowRealMatrix(rows.size(), nParams);
      RealVector bx = new ArrayRealVector(rows.size());
      RealVector by = new ArrayRealVector(rows.size());
      for (int r = 0; r < rows.size(); r++) {
         double[] row = rows.get(r);
         double u = row[0];
         double v = row[1];
         double sw = row[2];
         a.setEntry(r, 0, sw);
         a.setEntry(r, 1, sw * u);
         a.setEntry(r, 2, sw * v);
         if (quadratic) {
            a.setEntry(r, 3, sw * u * u);
            a.setEntry(r, 4, sw * u * v);
            a.setEntry(r, 5, sw * v * v);
         }
         bx.setEntry(r, sw * row[3]);
         by.setEntry(r, sw * row[4]);
      }
      try {
         DecompositionSolver solver = new QRDecomposition(a, 1e-10).getSolver();
         // coordinates are relative to the target node, so the constant term is the fit there
         return new Point2D.Double(solver.solve(bx).getEntry(0), solver.solve(by).getEntry(0));
      } catch (SingularMatrixException e) {
         return null;
      }
   }

   private static int count(boolean[] flags) {
      int n = 0;
      for (boolean f : flags) {
         if (f) {
            n++;
         }
      }
      return n;
   }

   private static double median(List<Double> values) {
      double[] v = new double[values.size()];
      for (int k = 0; k < v.length; k++) {
         v[k] = values.get(k);
      }
      Arrays.sort(v);
      int n = v.length;
      return n % 2 == 1 ? v[n / 2] : (v[n / 2 - 1] + v[n / 2]) / 2;
   }
}
