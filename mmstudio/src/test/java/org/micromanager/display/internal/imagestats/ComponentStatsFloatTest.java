package org.micromanager.display.internal.imagestats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests the untruncated (double) statistics used for float images.
 *
 * <p>The long-valued statistics cannot represent float data: a mean of -0.16 rounds to 0
 * and a minimum of -2.24 floors to -3. The double counterparts carry the real values while
 * the longs remain for the integer bin and quantile machinery.
 */
public class ComponentStatsFloatTest {

   private static final double DELTA = 1e-9;

   /** Values measured from a real 32-bit float dataset. */
   private static final double REAL_MIN = -2.238095283508301;
   private static final double REAL_MAX = 1.0322580337524414;
   private static final double REAL_MEAN = -0.16218741610646248;

   private static ComponentStats floatStats(double min, double max, double mean,
                                            long count, double sumOfSquares) {
      return ComponentStats.builder()
            .histogram(new long[] {0, 1, 0}, 0)
            .isFloat(true)
            .rangeMin(min)
            .binWidthFloat((max - min) / 256.0)
            .pixelCount(count)
            .pixelCountExcludingZeros(count)
            .minimum((long) Math.floor(min))
            .maximum((long) Math.ceil(max))
            .sum(Math.round(mean * count))
            .sumOfSquares(Math.round(sumOfSquares))
            .floatMinimum(min)
            .floatMinimumExcludingZeros(min)
            .floatMaximum(max)
            .floatSum(mean * count)
            .floatSumOfSquares(sumOfSquares)
            .build();
   }

   // --- Float images keep their real values ---

   @Test
   public void testMinMaxAreNotTruncated() {
      ComponentStats cs = floatStats(REAL_MIN, REAL_MAX, REAL_MEAN, 4194304L, 1000.0);
      assertEquals(REAL_MIN, cs.getFloatMinIntensity(), DELTA);
      assertEquals(REAL_MAX, cs.getFloatMaxIntensity(), DELTA);
   }

   /** The long getters still floor/ceil; that is what the readout used to show. */
   @Test
   public void testLongGettersStillTruncate() {
      ComponentStats cs = floatStats(REAL_MIN, REAL_MAX, REAL_MEAN, 4194304L, 1000.0);
      assertEquals(-3L, cs.getMinIntensity());
      assertEquals(2L, cs.getMaxIntensity());
   }

   @Test
   public void testMeanIsNotRoundedToZero() {
      long count = 4194304L;
      ComponentStats cs = floatStats(REAL_MIN, REAL_MAX, REAL_MEAN, count, 1000.0);
      assertEquals(REAL_MEAN, cs.getFloatMeanIntensity(), 1e-6);
      // The long mean collapses to zero, which is the bug being fixed.
      assertEquals(0L, cs.getMeanIntensity());
   }

   @Test
   public void testMeanExcludingZeros() {
      long count = 1000L;
      ComponentStats cs = floatStats(-1.0, 1.0, 0.25, count, 100.0);
      assertEquals(0.25, cs.getFloatMeanIntensityExcludingZeros(), 1e-9);
   }

   // --- Standard deviation ---

   @Test
   public void testStandardDeviationIsCorrect() {
      long count = 1000L;
      double mean = 2.0;
      double sd = 0.5;
      // E[x^2] = sd^2 + mean^2
      double sumOfSquares = count * (sd * sd + mean * mean);
      ComponentStats cs = floatStats(0.0, 4.0, mean, count, sumOfSquares);
      assertEquals(sd, cs.getFloatStandardDeviation(), 1e-9);
   }

   /**
    * With a near-zero mean the integer version can compute a negative variance and return
    * NaN. The double version must not.
    */
   @Test
   public void testStandardDeviationNeverNaNForNearZeroMean() {
      long count = 4194304L;
      double sd = 0.05;
      double sumOfSquares = count * (sd * sd + REAL_MEAN * REAL_MEAN);
      ComponentStats cs = floatStats(REAL_MIN, REAL_MAX, REAL_MEAN, count, sumOfSquares);
      double result = cs.getFloatStandardDeviation();
      assertFalse("stdev should not be NaN", Double.isNaN(result));
      assertEquals(sd, result, 1e-6);
   }

   @Test
   public void testStandardDeviationClampsInsteadOfNaN() {
      // sumOfSquares slightly below mean^2 * count (only possible via rounding) must give
      // 0 rather than NaN.
      long count = 100L;
      double mean = 1.0;
      ComponentStats cs = floatStats(0.0, 2.0, mean, count, count * 0.999999);
      double result = cs.getFloatStandardDeviation();
      assertFalse(Double.isNaN(result));
      assertTrue(result >= 0.0);
   }

   @Test
   public void testStandardDeviationNaNWithNoPixels() {
      ComponentStats cs = ComponentStats.builder()
            .histogram(new long[] {0, 0, 0}, 0)
            .isFloat(true)
            .pixelCount(0)
            .build();
      assertTrue(Double.isNaN(cs.getFloatStandardDeviation()));
   }

   // --- Integer images fall back to the long values, unchanged ---

   @Test
   public void testIntegerStatsFallBackToLongValues() {
      ComponentStats cs = ComponentStats.builder()
            .histogram(new long[] {0, 4, 0}, 0)
            .pixelCount(4)
            .pixelCountExcludingZeros(4)
            .minimum(10L)
            .minimumExcludingZeros(12L)
            .maximum(200L)
            .sum(400L)
            .sumOfSquares(40000L)
            .build();
      assertFalse(cs.isFloat());
      assertEquals(10.0, cs.getFloatMinIntensity(), DELTA);
      assertEquals(12.0, cs.getFloatMinIntensityExcludingZeros(), DELTA);
      assertEquals(200.0, cs.getFloatMaxIntensity(), DELTA);
      assertEquals(100.0, cs.getFloatMeanIntensity(), DELTA);
      // Matches the long getters exactly: no behavior change for integer images.
      assertEquals((double) cs.getMinIntensity(), cs.getFloatMinIntensity(), DELTA);
      assertEquals((double) cs.getMaxIntensity(), cs.getFloatMaxIntensity(), DELTA);
      assertEquals((double) cs.getMeanIntensity(), cs.getFloatMeanIntensity(), DELTA);
   }

   @Test
   public void testMeanIsZeroWithNoPixels() {
      ComponentStats cs = ComponentStats.builder()
            .histogram(new long[] {0, 0, 0}, 0)
            .pixelCount(0)
            .build();
      assertEquals(0.0, cs.getFloatMeanIntensity(), DELTA);
      assertEquals(0.0, cs.getFloatMeanIntensityExcludingZeros(), DELTA);
   }

   // --- Autoscale keeps pixel values (issue: long autoscale rounds them away) ---

   /**
    * Builds float stats whose histogram spreads pixels evenly over [min, max].
    */
   private static ComponentStats evenlySpreadFloatStats(double min, double max) {
      int bins = 256;
      long[] hist = new long[bins + 2];
      for (int i = 1; i <= bins; ++i) {
         hist[i] = 100L;
      }
      return ComponentStats.builder()
            .histogram(hist, 0)
            .isFloat(true)
            .rangeMin(min)
            .binWidthFloat((max - min) / bins)
            .pixelCount(100L * bins)
            .pixelCountExcludingZeros(100L * bins)
            .minimum((long) Math.floor(min))
            .maximum((long) Math.ceil(max))
            .floatMinimum(min)
            .floatMaximum(max)
            .build();
   }

   @Test
   public void testFloatAutoscaleStaysInsideTheDataRange() {
      // A range narrower than one unit: the long-valued autoscale rounds this to 0/1 and
      // then widens it to width >= 2, i.e. wider than the data itself.
      ComponentStats cs = evenlySpreadFloatStats(-0.65, 1.04);
      double[] minMax = new double[2];
      cs.getFloatAutoscaleMinMaxForQuantile(0.001, minMax);

      assertTrue("min " + minMax[0] + " below data", minMax[0] >= -0.65);
      assertTrue("max " + minMax[1] + " above data", minMax[1] <= 1.04);
      assertTrue("range must be non-empty", minMax[1] > minMax[0]);
      // The whole point: the bounds are fractional, not rounded to whole numbers.
      assertTrue("min should not be a whole number", minMax[0] != Math.rint(minMax[0]));
   }

   @Test
   public void testLongAutoscaleStillRoundsForIntegerPath() {
      // The long-valued method is unchanged; the float path exists precisely because
      // this one cannot represent the range.
      ComponentStats cs = evenlySpreadFloatStats(-0.65, 1.04);
      long[] minMax = new long[2];
      cs.getAutoscaleMinMaxForQuantile(0.001, minMax);
      assertTrue("long autoscale rounds to whole units", minMax[1] - minMax[0] >= 2);
   }

   @Test
   public void testFloatAutoscaleWidensDegenerateRange() {
      ComponentStats cs = evenlySpreadFloatStats(2.5, 2.5);
      double[] minMax = new double[2];
      cs.getFloatAutoscaleMinMaxForQuantile(0.0, minMax);
      assertTrue("degenerate range must be widened", minMax[1] > minMax[0]);
   }

   /**
    * Callers of the ignoring-zeros autoscale pair it with a minimum of zero, so its result
    * has to stay above zero. Data with a negative minimum used to clamp against that
    * minimum instead, which could return a maximum below the caller's floor.
    */
   @Test
   public void testAutoscaleIgnoringZerosStaysAboveZeroForNegativeData() {
      ComponentStats cs = evenlySpreadFloatStats(-2.238, -0.5);
      assertTrue("data minimum is negative", cs.getFloatMinIntensity() < 0.0);
      double max = cs.getFloatAutoscaleMaxForQuantileIgnoringZeros(0.001);
      assertTrue("max " + max + " must exceed the zero floor callers use", max > 0.0);
   }

   @Test
   public void testAutoscaleIgnoringZerosIsPositiveForAllNegativeData() {
      ComponentStats cs = evenlySpreadFloatStats(-5.0, -1.0);
      double max = cs.getFloatAutoscaleMaxForQuantileIgnoringZeros(0.0);
      assertTrue("max " + max + " must be greater than zero", max > 0.0);
   }

   @Test
   public void testAutoscaleIgnoringZerosKeepsPositiveQuantile() {
      ComponentStats cs = evenlySpreadFloatStats(0.0, 4.0);
      double max = cs.getFloatAutoscaleMaxForQuantileIgnoringZeros(0.001);
      assertTrue("a usable quantile must be kept as-is", max > 1.0 && max <= 4.0);
   }

   // --- merge() must carry the float fields and the axis ---

   @Test
   public void testMergeKeepsFloatStatistics() {
      ComponentStats a = floatStats(-2.0, 1.0, -0.5, 100L, 50.0);
      ComponentStats b = floatStats(-1.0, 3.0, 0.25, 100L, 40.0);
      ComponentStats m = ComponentStats.merge(a, b);

      assertTrue(m.isFloat());
      // Without the fix these fall back to the floored/ceiled longs (-2.0 and 3.0 here
      // by coincidence of the data, so assert the sums too, which cannot coincide).
      assertEquals(-2.0, m.getFloatMinIntensity(), DELTA);
      assertEquals(3.0, m.getFloatMaxIntensity(), DELTA);
      assertEquals(-0.5 * 100 + 0.25 * 100, m.getFloatSum(), 1e-6);
      assertEquals(90.0, m.getFloatSumOfSquares(), 1e-6);
   }

   @Test
   public void testMergeKeepsFloatAxis() {
      ComponentStats a = floatStats(-2.0, 1.0, -0.5, 100L, 50.0);
      ComponentStats b = floatStats(-1.0, 3.0, 0.25, 100L, 40.0);
      ComponentStats m = ComponentStats.merge(a, b);

      // Without the fix the axis collapses to rangeMin=0.0, binWidth=1.0, which makes
      // every bin edge and quantile wrong.
      assertEquals(-2.0, m.getHistogramRangeMinDouble(), DELTA);
      assertTrue("bin width must come from the float axis, not 1<<0",
            m.getBinWidthDouble() != 1.0);
   }

   @Test
   public void testMergeOfIntegerStatsIsUnaffected() {
      ComponentStats a = ComponentStats.builder()
            .histogram(new long[] {0, 5, 0}, 0)
            .pixelCount(5L).pixelCountExcludingZeros(5L)
            .minimum(2L).maximum(9L).sum(25L).sumOfSquares(150L)
            .build();
      ComponentStats m = ComponentStats.merge(a, a);
      assertFalse(m.isFloat());
      assertEquals(50L, m.getSum());
      assertEquals(2L, m.getMinIntensity());
      assertEquals(9L, m.getMaxIntensity());
   }

   // --- Quantile edges must not fall back to the floored/ceiled longs ---

   @Test
   public void testZeroIgnoreQuantileGivesTheRealDataRange() {
      // q=0 hits the "exact edge of the non-zero histogram" branch, which used to return
      // the long minimum_/maximum_+1: for [-0.65, 1.04] that gave [-1.0, 3.0], a range
      // both wider than the data and outside it.
      ComponentStats cs = evenlySpreadFloatStats(-0.65, 1.04);
      double[] minMax = new double[2];
      cs.getFloatAutoscaleMinMaxForQuantile(0.0, minMax);

      assertEquals(-0.65, minMax[0], 1e-9);
      assertEquals(1.04, minMax[1], 1e-9);
   }

   @Test
   public void testQuantileEdgesStayIntegralForIntegerStats() {
      // The integer convention ("one past the maximum") must be preserved.
      ComponentStats cs = ComponentStats.builder()
            .histogram(new long[] {0, 10, 20, 0}, 0)
            .pixelCount(30L).pixelCountExcludingZeros(30L)
            .minimum(5L).maximum(9L).sum(200L).sumOfSquares(2000L)
            .build();
      assertEquals(5.0, cs.getQuantile(0.0), 1e-9);
      assertEquals(10.0, cs.getQuantile(1.0), 1e-9);
   }

   @Test
   public void testRangeNeverCollapsesWhenOneBinDominates() {
      // A background spike holding nearly every pixel puts both quantiles in the same
      // bin, which would otherwise yield a range of width ~0 and a black/white image.
      int bins = 256;
      long[] hist = new long[bins + 2];
      for (int i = 1; i <= bins; ++i) {
         hist[i] = 1L;
      }
      hist[40] = 4_000_000L;
      long total = 0;
      for (long v : hist) {
         total += v;
      }
      double min = -0.65;
      double max = 1.04;
      ComponentStats cs = ComponentStats.builder()
            .histogram(hist, 0)
            .isFloat(true)
            .rangeMin(min)
            .binWidthFloat((max - min) / bins)
            .pixelCount(total)
            .pixelCountExcludingZeros(total)
            .minimum((long) Math.floor(min))
            .maximum((long) Math.ceil(max))
            .floatMinimum(min)
            .floatMaximum(max)
            .build();

      double[] minMax = new double[2];
      cs.getFloatAutoscaleMinMaxForQuantile(0.5, minMax);
      assertTrue("range collapsed to " + (minMax[1] - minMax[0]),
            minMax[1] - minMax[0] >= (max - min) / bins - 1e-12);
   }

   @Test
   public void testAutoscaleRespondsToIgnoreFractionOnANonUnitRange() {
      // Regression: a range such as 2.7..22.7 rounds to 3..22 under the long-valued
      // autoscale, and stays pinned there for every ignore fraction up to ~1%. The float
      // autoscale must move monotonically inward as the fraction rises.
      ComponentStats cs = evenlySpreadFloatStats(2.7, 22.7);
      double[] prev = new double[2];
      cs.getFloatAutoscaleMinMaxForQuantile(0.0, prev);
      assertEquals(2.7, prev[0], 1e-9);
      assertEquals(22.7, prev[1], 1e-9);

      for (double q : new double[] {0.005, 0.01, 0.05, 0.2}) {
         double[] cur = new double[2];
         cs.getFloatAutoscaleMinMaxForQuantile(q, cur);
         assertTrue("min must rise at q=" + q + " (was " + prev[0] + ", got " + cur[0] + ")",
               cur[0] > prev[0]);
         assertTrue("max must fall at q=" + q + " (was " + prev[1] + ", got " + cur[1] + ")",
               cur[1] < prev[1]);
         prev = cur;
      }
   }
}
