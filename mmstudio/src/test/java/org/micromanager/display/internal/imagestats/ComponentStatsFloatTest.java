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
            .minimumDouble(min)
            .minimumExcludingZerosDouble(min)
            .maximumDouble(max)
            .sumDouble(mean * count)
            .sumOfSquaresDouble(sumOfSquares)
            .build();
   }

   // --- Float images keep their real values ---

   @Test
   public void testMinMaxAreNotTruncated() {
      ComponentStats cs = floatStats(REAL_MIN, REAL_MAX, REAL_MEAN, 4194304L, 1000.0);
      assertEquals(REAL_MIN, cs.getMinIntensityDouble(), DELTA);
      assertEquals(REAL_MAX, cs.getMaxIntensityDouble(), DELTA);
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
      assertEquals(REAL_MEAN, cs.getMeanIntensityDouble(), 1e-6);
      // The long mean collapses to zero, which is the bug being fixed.
      assertEquals(0L, cs.getMeanIntensity());
   }

   @Test
   public void testMeanExcludingZeros() {
      long count = 1000L;
      ComponentStats cs = floatStats(-1.0, 1.0, 0.25, count, 100.0);
      assertEquals(0.25, cs.getMeanIntensityExcludingZerosDouble(), 1e-9);
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
      assertEquals(sd, cs.getStandardDeviationDouble(), 1e-9);
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
      double result = cs.getStandardDeviationDouble();
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
      double result = cs.getStandardDeviationDouble();
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
      assertTrue(Double.isNaN(cs.getStandardDeviationDouble()));
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
      assertEquals(10.0, cs.getMinIntensityDouble(), DELTA);
      assertEquals(12.0, cs.getMinIntensityExcludingZerosDouble(), DELTA);
      assertEquals(200.0, cs.getMaxIntensityDouble(), DELTA);
      assertEquals(100.0, cs.getMeanIntensityDouble(), DELTA);
      // Matches the long getters exactly: no behavior change for integer images.
      assertEquals((double) cs.getMinIntensity(), cs.getMinIntensityDouble(), DELTA);
      assertEquals((double) cs.getMaxIntensity(), cs.getMaxIntensityDouble(), DELTA);
      assertEquals((double) cs.getMeanIntensity(), cs.getMeanIntensityDouble(), DELTA);
   }

   @Test
   public void testMeanIsZeroWithNoPixels() {
      ComponentStats cs = ComponentStats.builder()
            .histogram(new long[] {0, 0, 0}, 0)
            .pixelCount(0)
            .build();
      assertEquals(0.0, cs.getMeanIntensityDouble(), DELTA);
      assertEquals(0.0, cs.getMeanIntensityExcludingZerosDouble(), DELTA);
   }
}
