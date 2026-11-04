package org.micromanager.projector.internal;

import edu.ucsf.valelab.gaussianfit.algorithm.GaussianFit;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.micromanager.internal.utils.imageanalysis.ImageUtils;

/**
 * Judges the spot localization used by the Projector calibration (Calibrator.findPeak) against
 * alternative methods.
 *
 * <p>Real data (CollectFindSpotData.bsh output) has no ground truth, so each method is scored
 * by how well its camera positions fit a smooth projector-to-camera mapping: the residual of an
 * affine and of a quadratic least-squares fit, and the leave-one-out prediction error of the
 * quadratic fit. Lower is better. Constant offsets are absorbed by the fit and are therefore
 * reported separately (difference to the Gaussian fit). The data directory is set with
 * -Dprojector.calibration.data=...; the real-data test is skipped when it is absent.
 *
 * <p>Synthetic data has a known ground truth and reveals systematic bias.
 */
public class SpotFindingQualityTest {

   private static final String DATA_PROPERTY = "projector.calibration.data";
   private static final String DEFAULT_DATA_DIR = "Z:/TIRF_Data/Calibration3";
   private static final String REFERENCE_METHOD = "Gauss LM r=12";

   private static List<HelperCalibrationSpotData.Sample> samples_;

   @BeforeClass
   public static void loadData() throws Exception {
      File dir = new File(System.getProperty(DATA_PROPERTY, DEFAULT_DATA_DIR));
      if (dir.isDirectory()) {
         samples_ = HelperCalibrationSpotData.load(dir);
      }
   }

   // ------------------------------------------------------------------------
   // Real data
   // ------------------------------------------------------------------------

   @Test
   public void realDataMappingResiduals() {
      Assume.assumeTrue("Calibration data not found; set -D" + DATA_PROPERTY,
            samples_ != null && !samples_.isEmpty());
      int n = samples_.size();
      List<HelperSpotFinders.SpotFinder> finders = HelperSpotFinders.all();
      Point2D.Double[][] found = new Point2D.Double[finders.size()][n];
      for (int m = 0; m < finders.size(); m++) {
         for (int i = 0; i < n; i++) {
            found[m][i] = finders.get(m).find(samples_.get(i).diff);
         }
      }
      int ref = indexOf(finders, REFERENCE_METHOD);

      System.out.println();
      System.out.println("=== Real data: " + n + " spots. Residuals in camera pixels ===");
      System.out.println(String.format(Locale.US, "%-26s %5s %9s %9s %9s %9s %9s %16s %9s",
            "method", "found", "affRMS", "affMax", "quadRMS", "quadMax", "looRMS",
            "meanD(ref) x,y", "rmsD(ref)"));
      double[] quadRms = new double[finders.size()];
      for (int m = 0; m < finders.size(); m++) {
         List<Point2D.Double> proj = new ArrayList<>();
         List<Point2D.Double> cam = new ArrayList<>();
         List<Point2D.Double> refCam = new ArrayList<>();
         for (int i = 0; i < n; i++) {
            if (found[m][i] != null) {
               proj.add(samples_.get(i).projector);
               cam.add(found[m][i]);
               refCam.add(found[ref][i]);
            }
         }
         FitStats aff = fitStats(proj, cam, false);
         FitStats quad = fitStats(proj, cam, true);
         double loo = leaveOneOutRms(proj, cam, true);
         quadRms[m] = quad.rms;
         double[] d = differenceStats(cam, refCam);
         System.out.println(String.format(Locale.US,
               "%-26s %2d/%2d %9.3f %9.3f %9.3f %9.3f %9.3f %7.2f,%7.2f %9.3f",
               finders.get(m).name(), cam.size(), n, aff.rms, aff.max, quad.rms, quad.max, loo,
               d[0], d[1], d[2]));
      }

      printPerSpotComparison(finders, found, 0, ref);
      printReferenceSpotWidths();

      // The current algorithm must at least find every spot.
      for (int i = 0; i < n; i++) {
         Assert.assertNotNull("Projector algorithm missed " + samples_.get(i).name, found[0][i]);
      }
      // Every method must land on the spot: a mapping residual this large means it did not.
      for (int m = 0; m < finders.size(); m++) {
         Assert.assertTrue(finders.get(m).name() + " quadratic RMS " + quadRms[m],
               quadRms[m] < 5.0);
      }
   }

   private void printPerSpotComparison(List<HelperSpotFinders.SpotFinder> finders,
         Point2D.Double[][] found, int a, int b) {
      System.out.println();
      System.out.println("--- Per spot: " + finders.get(a).name() + " vs "
            + finders.get(b).name() + " ---");
      for (int i = 0; i < samples_.size(); i++) {
         Point2D.Double pa = found[a][i];
         Point2D.Double pb = found[b][i];
         System.out.println(String.format(Locale.US,
               "proj (%7.1f,%7.1f)  A %s  B %s  B-A %s",
               samples_.get(i).projector.x, samples_.get(i).projector.y, fmt(pa), fmt(pb),
               (pa == null || pb == null) ? "n/a"
                     : String.format(Locale.US, "(%6.2f,%6.2f)", pb.x - pa.x, pb.y - pa.y)));
      }
   }

   private void printReferenceSpotWidths() {
      double sum = 0;
      int count = 0;
      for (HelperCalibrationSpotData.Sample s : samples_) {
         double[] p = HelperSpotFinders.gaussianFitParams(s.diff, 12,
               GaussianFit.LEVENBERGMARQUARD);
         if (p != null) {
            sum += p[GaussianFit.S];
            count++;
         }
      }
      System.out.println(String.format(Locale.US,
            "Mean fitted core sigma (Gauss LM r=12): %.2f px over %d spots", sum / count, count));
   }

   // ------------------------------------------------------------------------
   // Synthetic data with known positions
   // ------------------------------------------------------------------------

   /**
    * Symmetric spot (narrow core + broad halo, roughly matching the real data) at random
    * sub-pixel positions with shot and read noise, put through the same background
    * subtraction as the calibration.
    */
   @Test
   public void syntheticBiasAndPrecision() throws Exception {
      final int size = 256;
      final int trials = 60;
      final double coreSigma = 2.5;
      final double coreAmp = 10000;
      final double haloSigma = 40;
      final double haloAmp = 800;
      final double offset = 110;
      final double readNoise = 3;
      Random rnd = new Random(42);
      List<HelperSpotFinders.SpotFinder> finders = HelperSpotFinders.all();
      double[][] sumD = new double[finders.size()][2];
      double[] sumSq = new double[finders.size()];
      int[] ok = new int[finders.size()];
      for (int t = 0; t < trials; t++) {
         double tx = 80 + rnd.nextDouble() * (size - 160);
         double ty = 80 + rnd.nextDouble() * (size - 160);
         ShortProcessor bg = new ShortProcessor(size, size);
         ShortProcessor spot = new ShortProcessor(size, size);
         for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
               double r2 = (x - tx) * (x - tx) + (y - ty) * (y - ty);
               double signal = coreAmp * Math.exp(-r2 / (2 * coreSigma * coreSigma))
                     + haloAmp * Math.exp(-r2 / (2 * haloSigma * haloSigma));
               bg.set(x, y, noisy(offset, 0, readNoise, rnd));
               spot.set(x, y, noisy(offset, signal, readNoise, rnd));
            }
         }
         FloatProcessor diff = (FloatProcessor) ImageUtils.subtractImageProcessors(
               spot.convertToFloatProcessor(), bg.convertToFloatProcessor());
         for (int m = 0; m < finders.size(); m++) {
            Point2D.Double p = finders.get(m).find(diff);
            if (p != null) {
               double dx = p.x - tx;
               double dy = p.y - ty;
               sumD[m][0] += dx;
               sumD[m][1] += dy;
               sumSq[m] += dx * dx + dy * dy;
               ok[m]++;
            }
         }
      }
      System.out.println();
      System.out.println("=== Synthetic: " + trials + " symmetric spots, core sigma " + coreSigma
            + " px. Error = found - true, in pixels ===");
      System.out.println(String.format(Locale.US, "%-26s %5s %16s %9s",
            "method", "found", "bias x,y", "rmsErr"));
      for (int m = 0; m < finders.size(); m++) {
         System.out.println(String.format(Locale.US, "%-26s %2d/%2d %7.3f,%7.3f %9.3f",
               finders.get(m).name(), ok[m], trials, sumD[m][0] / ok[m], sumD[m][1] / ok[m],
               Math.sqrt(sumSq[m] / ok[m])));
      }

      // Whole-pixel output limits precision, but there must be no systematic offset.
      int proj = indexOf(finders, HelperSpotFinders.PROJECTOR);
      Assert.assertEquals("Projector found all", trials, ok[proj]);
      Assert.assertEquals("Projector bias x", 0.0, sumD[proj][0] / ok[proj], 0.15);
      Assert.assertEquals("Projector bias y", 0.0, sumD[proj][1] / ok[proj], 0.15);

      // Sub-pixel methods must be unbiased and well below a pixel on an ideal spot.
      for (String name : new String[] {"Blur2+max+parabola", "Centroid >50% of peak",
            HelperSpotFinders.CENTROID, "Gauss LM r=6", "Gauss LM r=12"}) {
         int m = indexOf(finders, name);
         Assert.assertEquals(name + " found all", trials, ok[m]);
         Assert.assertEquals(name + " bias x", 0.0, sumD[m][0] / ok[m], 0.05);
         Assert.assertEquals(name + " bias y", 0.0, sumD[m][1] / ok[m], 0.05);
         Assert.assertTrue(name + " rms " + Math.sqrt(sumSq[m] / ok[m]),
               Math.sqrt(sumSq[m] / ok[m]) < 0.2);
      }
   }

   private static int noisy(double offset, double signal, double readNoise, Random rnd) {
      double v = offset + signal + rnd.nextGaussian() * Math.sqrt(signal + readNoise * readNoise);
      return (int) Math.max(0, Math.min(65535, Math.round(v)));
   }

   // ------------------------------------------------------------------------
   // Least squares helpers
   // ------------------------------------------------------------------------

   private static final class FitStats {
      final double rms;
      final double max;

      FitStats(double rms, double max) {
         this.rms = rms;
         this.max = max;
      }
   }

   /** Residuals of a least-squares fit cam = f(proj), f affine or full quadratic. */
   private static FitStats fitStats(List<Point2D.Double> proj, List<Point2D.Double> cam,
         boolean quadratic) {
      double[][] coef = fit(proj, cam, quadratic, -1);
      double ss = 0;
      double max = 0;
      for (int i = 0; i < proj.size(); i++) {
         double r = residual(coef, proj.get(i), cam.get(i), quadratic, proj);
         ss += r * r;
         max = Math.max(max, r);
      }
      return new FitStats(Math.sqrt(ss / proj.size()), max);
   }

   private static double leaveOneOutRms(List<Point2D.Double> proj, List<Point2D.Double> cam,
         boolean quadratic) {
      double ss = 0;
      for (int i = 0; i < proj.size(); i++) {
         double[][] coef = fit(proj, cam, quadratic, i);
         double r = residual(coef, proj.get(i), cam.get(i), quadratic, proj);
         ss += r * r;
      }
      return Math.sqrt(ss / proj.size());
   }

   private static double residual(double[][] coef, Point2D.Double p, Point2D.Double c,
         boolean quadratic, List<Point2D.Double> all) {
      double[] b = basis(p, quadratic, all);
      double fx = 0;
      double fy = 0;
      for (int k = 0; k < b.length; k++) {
         fx += coef[0][k] * b[k];
         fy += coef[1][k] * b[k];
      }
      return Math.hypot(c.x - fx, c.y - fy);
   }

   /** Fits both camera axes; row {@code skip} is left out (-1 = use all). */
   private static double[][] fit(List<Point2D.Double> proj, List<Point2D.Double> cam,
         boolean quadratic, int skip) {
      int n = proj.size() - (skip >= 0 ? 1 : 0);
      int k = quadratic ? 6 : 3;
      RealMatrix a = new Array2DRowRealMatrix(n, k);
      RealVector bx = new ArrayRealVector(n);
      RealVector by = new ArrayRealVector(n);
      int row = 0;
      for (int i = 0; i < proj.size(); i++) {
         if (i == skip) {
            continue;
         }
         a.setRow(row, basis(proj.get(i), quadratic, proj));
         bx.setEntry(row, cam.get(i).x);
         by.setEntry(row, cam.get(i).y);
         row++;
      }
      DecompositionSolver solver = new QRDecomposition(a).getSolver();
      return new double[][] {solver.solve(bx).toArray(), solver.solve(by).toArray()};
   }

   /** Basis functions in projector coordinates normalized to about [-1, 1]. */
   private static double[] basis(Point2D.Double p, boolean quadratic,
         List<Point2D.Double> all) {
      double minX = Double.MAX_VALUE;
      double maxX = -Double.MAX_VALUE;
      double minY = Double.MAX_VALUE;
      double maxY = -Double.MAX_VALUE;
      for (Point2D.Double q : all) {
         minX = Math.min(minX, q.x);
         maxX = Math.max(maxX, q.x);
         minY = Math.min(minY, q.y);
         maxY = Math.max(maxY, q.y);
      }
      double u = 2 * (p.x - minX) / Math.max(1e-9, maxX - minX) - 1;
      double v = 2 * (p.y - minY) / Math.max(1e-9, maxY - minY) - 1;
      return quadratic ? new double[] {1, u, v, u * u, u * v, v * v} : new double[] {1, u, v};
   }

   /** {mean dx, mean dy, rms of |d|} of a - b. */
   private static double[] differenceStats(List<Point2D.Double> a, List<Point2D.Double> b) {
      double sx = 0;
      double sy = 0;
      double ss = 0;
      int n = 0;
      for (int i = 0; i < a.size(); i++) {
         if (a.get(i) == null || b.get(i) == null) {
            continue;
         }
         double dx = a.get(i).x - b.get(i).x;
         double dy = a.get(i).y - b.get(i).y;
         sx += dx;
         sy += dy;
         ss += dx * dx + dy * dy;
         n++;
      }
      return n == 0 ? new double[] {Double.NaN, Double.NaN, Double.NaN}
            : new double[] {sx / n, sy / n, Math.sqrt(ss / n)};
   }

   private static int indexOf(List<HelperSpotFinders.SpotFinder> finders, String name) {
      for (int i = 0; i < finders.size(); i++) {
         if (finders.get(i).name().equals(name)) {
            return i;
         }
      }
      throw new IllegalArgumentException(name);
   }

   private static String fmt(Point2D.Double p) {
      return p == null ? "      n/a      "
            : String.format(Locale.US, "(%6.2f,%6.2f)", p.x, p.y);
   }
}
