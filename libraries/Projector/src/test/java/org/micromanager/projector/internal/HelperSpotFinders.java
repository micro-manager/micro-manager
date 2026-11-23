package org.micromanager.projector.internal;

import edu.ucsf.valelab.gaussianfit.algorithm.GaussianFit;
import ij.plugin.filter.GaussianBlur;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.micromanager.internal.utils.imageanalysis.ImageUtils;

/**
 * Candidate spot localization methods operating on a (spot - background) difference image.
 * Convention for all returned positions: the center of pixel (i, j) is at (i, j).
 * A method returns null when it cannot produce a position.
 */
final class HelperSpotFinders {

   interface SpotFinder {
      String name();

      Point2D.Double find(FloatProcessor diff);
   }

   static final String PROJECTOR = "Projector (Calibrator)";
   static final String CENTROID = "Centroid (Calibrator)";

   private HelperSpotFinders() {
   }

   /** All methods compared by the tests, the current Projector algorithm first. */
   static List<SpotFinder> all() {
      List<SpotFinder> list = new ArrayList<>();
      list.add(named(PROJECTOR, Calibrator::findPeak));
      list.add(named("Raw max pixel", d -> toDouble(ImageUtils.findMaxPixel(d))));
      list.add(named("Blur2+max+parabola", d -> blurredMaxParabolic(d, 2.0)));
      list.add(named("Centroid >50% of peak", d -> thresholdCentroid(d, 0.5, 15)));
      list.add(named(CENTROID, Calibrator::findCentroid));
      list.add(named("Gauss LM r=6", d -> gaussianFit(d, 6, GaussianFit.LEVENBERGMARQUARD)));
      list.add(named("Gauss LM r=12", d -> gaussianFit(d, 12, GaussianFit.LEVENBERGMARQUARD)));
      return list;
   }

   static SpotFinder named(final String name, final java.util.function.Function<FloatProcessor,
         Point2D.Double> f) {
      return new SpotFinder() {
         @Override
         public String name() {
            return name;
         }

         @Override
         public Point2D.Double find(FloatProcessor diff) {
            return f.apply(diff);
         }
      };
   }

   static Point blurredMax(FloatProcessor diff, double sigma) {
      ImageProcessor blurImage = diff.duplicate();
      new GaussianBlur().blurGaussian(blurImage, sigma, sigma, 0.01);
      return ImageUtils.findMaxPixel(blurImage);
   }

   /** Max of the blurred image refined by a 1D parabola through the 3 neighbors per axis. */
   static Point2D.Double blurredMaxParabolic(FloatProcessor diff, double sigma) {
      FloatProcessor blurImage = (FloatProcessor) diff.duplicate();
      new GaussianBlur().blurGaussian(blurImage, sigma, sigma, 0.01);
      Point p = ImageUtils.findMaxPixel(blurImage);
      if (p.x < 1 || p.y < 1 || p.x >= diff.getWidth() - 1 || p.y >= diff.getHeight() - 1) {
         return toDouble(p);
      }
      double dx = parabolaVertex(blurImage.getf(p.x - 1, p.y), blurImage.getf(p.x, p.y),
            blurImage.getf(p.x + 1, p.y));
      double dy = parabolaVertex(blurImage.getf(p.x, p.y - 1), blurImage.getf(p.x, p.y),
            blurImage.getf(p.x, p.y + 1));
      return new Point2D.Double(p.x + dx, p.y + dy);
   }

   private static double parabolaVertex(double left, double center, double right) {
      double denom = left - 2 * center + right;
      if (denom >= 0) {
         return 0.0;
      }
      return 0.5 * (left - right) / denom;
   }

   /**
    * Centroid of the pixels above fraction * (peak - local background), within a square of
    * half-size r around the lightly blurred maximum. Ignores the broad halo.
    */
   static Point2D.Double thresholdCentroid(FloatProcessor diff, double fraction, int r) {
      Point seed = blurredMax(diff, 2.0);
      Rect rect = Rect.around(seed, r, diff);
      double bg = rect.borderMedian(diff);
      double peak = diff.getf(seed.x, seed.y);
      double thr = bg + fraction * (peak - bg);
      double sw = 0;
      double sx = 0;
      double sy = 0;
      for (int y = rect.y0; y <= rect.y1; y++) {
         for (int x = rect.x0; x <= rect.x1; x++) {
            double v = diff.getf(x, y);
            if (v > thr) {
               double w = v - thr;
               sw += w;
               sx += w * x;
               sy += w * y;
            }
         }
      }
      return sw > 0 ? new Point2D.Double(sx / sw, sy / sw) : null;
   }

   /**
    * Symmetric 2D Gaussian fit (libraries/Gaussian) in a square of half-size r around the
    * lightly blurred maximum. The crop is offset so its minimum is 0 (GaussianFit wants
    * unsigned 16-bit data).
    */
   static Point2D.Double gaussianFit(FloatProcessor diff, int r, int fitMode) {
      double[] p = gaussianFitParams(diff, r, fitMode);
      return p == null ? null : new Point2D.Double(p[GaussianFit.XC], p[GaussianFit.YC]);
   }

   /** Returns {A, b, xc, yc, sigma} in full-image coordinates, or null. */
   static double[] gaussianFitParams(FloatProcessor diff, int r, int fitMode) {
      Point seed = blurredMax(diff, 2.0);
      Rect rect = Rect.around(seed, r, diff);
      int w = rect.x1 - rect.x0 + 1;
      int h = rect.y1 - rect.y0 + 1;
      float min = Float.MAX_VALUE;
      for (int y = rect.y0; y <= rect.y1; y++) {
         for (int x = rect.x0; x <= rect.x1; x++) {
            min = Math.min(min, diff.getf(x, y));
         }
      }
      short[] pix = new short[w * h];
      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            double v = diff.getf(rect.x0 + x, rect.y0 + y) - min;
            pix[y * w + x] = (short) Math.min(65535, Math.round(v));
         }
      }
      ShortProcessor crop = new ShortProcessor(w, h, pix, null);
      GaussianFit fitter = new GaussianFit(GaussianFit.CIRCLE, fitMode);
      double[] params = fitter.dogaussianfit(crop, 1000).getParms();
      if (params.length <= GaussianFit.S
            || Double.isNaN(params[GaussianFit.XC]) || Double.isNaN(params[GaussianFit.YC])) {
         return null;
      }
      double xc = params[GaussianFit.XC];
      double yc = params[GaussianFit.YC];
      // A fit that wandered out of its window has failed
      if (xc < 0 || yc < 0 || xc > w - 1 || yc > h - 1) {
         return null;
      }
      double[] out = Arrays.copyOf(params, params.length);
      out[GaussianFit.XC] = xc + rect.x0;
      out[GaussianFit.YC] = yc + rect.y0;
      out[GaussianFit.S] = Math.abs(out[GaussianFit.S]);
      return out;
   }

   static Point2D.Double toDouble(Point p) {
      return new Point2D.Double(p.x, p.y);
   }

   /** Inclusive pixel rectangle clipped to the image. */
   private static final class Rect {
      final int x0;
      final int y0;
      final int x1;
      final int y1;

      private Rect(int x0, int y0, int x1, int y1) {
         this.x0 = x0;
         this.y0 = y0;
         this.x1 = x1;
         this.y1 = y1;
      }

      static Rect around(Point c, int r, ImageProcessor ip) {
         return new Rect(Math.max(0, c.x - r), Math.max(0, c.y - r),
               Math.min(ip.getWidth() - 1, c.x + r), Math.min(ip.getHeight() - 1, c.y + r));
      }

      double borderMedian(ImageProcessor ip) {
         List<Float> v = new ArrayList<>();
         for (int x = x0; x <= x1; x++) {
            v.add(ip.getf(x, y0));
            v.add(ip.getf(x, y1));
         }
         for (int y = y0 + 1; y < y1; y++) {
            v.add(ip.getf(x0, y));
            v.add(ip.getf(x1, y));
         }
         v.sort(null);
         return v.get(v.size() / 2);
      }
   }
}
