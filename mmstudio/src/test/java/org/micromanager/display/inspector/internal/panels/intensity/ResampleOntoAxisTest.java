package org.micromanager.display.inspector.internal.panels.intensity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.micromanager.display.internal.imagestats.ComponentStats;

/**
 * Tests rebinning a float image's histogram onto the displayed axis.
 *
 * <p>The image's bins span its own min..max, which need not match the axis the user is
 * looking at. Counts have to land at the pixel value they came from, without being lost
 * or duplicated by rounding along the way.
 */
public class ResampleOntoAxisTest {

   private static final int BINS = 256;

   /** Float stats with the given per-bin counts spanning [min, max]. */
   private static ComponentStats statsSpanning(double min, double max, long[] inRange) {
      long[] hist = new long[inRange.length + 2];
      System.arraycopy(inRange, 0, hist, 1, inRange.length);
      long count = 0;
      for (long v : inRange) {
         count += v;
      }
      return ComponentStats.builder()
            .histogram(hist, 0)
            .isFloat(true)
            .rangeMin(min)
            .binWidthFloat((max - min) / inRange.length)
            .pixelCount(count)
            .pixelCountExcludingZeros(count)
            .minimum((long) Math.floor(min))
            .maximum((long) Math.ceil(max))
            .floatMinimum(min)
            .floatMaximum(max)
            .build();
   }

   private static long[] flatCounts(long perBin) {
      long[] counts = new long[BINS];
      for (int i = 0; i < BINS; ++i) {
         counts[i] = perBin;
      }
      return counts;
   }

   private static long total(long[] values) {
      long sum = 0;
      for (long v : values) {
         sum += v;
      }
      return sum;
   }

   /**
    * A source bin spread over three or more axis bins gives each of them less than half a
    * count. Rounding those contributions individually floored every one of them to zero,
    * leaving a completely empty histogram for an image displayed on a much narrower axis.
    */
   @Test
   public void testNarrowAxisDoesNotEmptyTheHistogram() {
      ComponentStats cs = statsSpanning(-2.0, 1.0, flatCounts(1L));
      // Axis pinned far narrower than the image: each source bin covers many axis bins.
      FloatCoordinateMapper mapper = new FloatCoordinateMapper(0.0, 0.1, BINS);

      long[] out = ChannelIntensityController.resampleOntoAxis(cs, mapper, BINS);

      assertTrue("histogram must not be empty", total(out) > 0);
   }

   /**
    * A source bin straddling an axis boundary contributes half a count to each side.
    * Rounding both halves up turned one count into two, doubling the histogram.
    */
   @Test
   public void testStraddlingBinsDoNotDoubleCounts() {
      ComponentStats cs = statsSpanning(0.5, 256.5, flatCounts(1L));
      // Axis offset by exactly half a bin from the source binning.
      FloatCoordinateMapper mapper = new FloatCoordinateMapper(0.0, 256.0, BINS);

      long[] out = ChannelIntensityController.resampleOntoAxis(cs, mapper, BINS);

      assertTrue("counts must not be inflated: " + total(out), total(out) <= BINS);
   }

   /** An axis matching the image exactly must pass the counts through untouched. */
   @Test
   public void testIdenticalAxisPreservesCounts() {
      long[] counts = new long[BINS];
      for (int i = 0; i < BINS; ++i) {
         counts[i] = i * 3L;
      }
      ComponentStats cs = statsSpanning(-1.5, 2.5, counts);
      FloatCoordinateMapper mapper = new FloatCoordinateMapper(-1.5, 2.5, BINS);

      long[] out = ChannelIntensityController.resampleOntoAxis(cs, mapper, BINS);

      assertEquals(total(counts), total(out));
   }

   /** Counts outside the axis are dropped, so clipped data reads as clipped. */
   @Test
   public void testCountsOutsideTheAxisAreDropped() {
      ComponentStats cs = statsSpanning(0.0, 4.0, flatCounts(10L));
      // Axis covers only the lower half of the image's range.
      FloatCoordinateMapper mapper = new FloatCoordinateMapper(0.0, 2.0, BINS);

      long[] out = ChannelIntensityController.resampleOntoAxis(cs, mapper, BINS);

      long all = total(flatCounts(10L));
      assertTrue("about half the counts should remain, got " + total(out),
            total(out) < all);
      assertTrue("counts inside the axis must survive", total(out) > 0);
   }
}
