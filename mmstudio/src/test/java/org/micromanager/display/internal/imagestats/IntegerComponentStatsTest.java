/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.imagestats;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author mark
 */
public class IntegerComponentStatsTest {
   public IntegerComponentStatsTest() {
   }

   private static final double e = 0.001;

   @Test
   public void testGetQuantile() {
      // histogram_[1]=1 means 1 pixel at intensity 0; quantile is bin-edge based.
      ComponentStats s = ComponentStats.builder().
            histogram(new long[] { 0, 1, 0 }, 0).pixelCount(1).build();
      assertEquals(0.0, s.getQuantile(0.0), e);
      assertEquals(1.0, s.getQuantile(1.0), e);
      assertEquals(0.5, s.getQuantile(0.5), e);

      s = ComponentStats.builder().
            histogram(new long[] { 0, 1, 2, 0 }, 0).pixelCount(3).maximum(1).build();
      assertEquals(0.0, s.getQuantile(0.0), e);
      assertEquals(1.25, s.getQuantile(0.5), e);
      assertEquals(2.0, s.getQuantile(1.0), e);
   }

   @Test
   public void testGetQuantileOutOfRange() {
      ComponentStats s = ComponentStats.builder().
            histogram(new long[] { 1, 1, 1, 1 }, 0).pixelCount(4).build();
      assertEquals(0.0, s.getQuantile(0.0), e);
      assertEquals(0.0, s.getQuantile(0.25), e);
      assertEquals(1.0, s.getQuantile(0.5), e);
      assertEquals(2.0, s.getQuantile(0.75), e);
      assertEquals(2.0, s.getQuantile(1.0), e);
   }

   // Helper: build stats from a histogram (no out-of-range bins) with bin width 1.
   // histogram[i] = count of pixels with intensity i. Range is [0, histogram.length-1].
   // Sets minimum to the first non-zero bin and maximum to the last non-zero bin.
   private static ComponentStats buildStats(long[] histogram) {
      long[] full = new long[histogram.length + 2];
      System.arraycopy(histogram, 0, full, 1, histogram.length);
      long count = 0;
      long minimum = 0;
      long maximum = histogram.length - 1;
      boolean foundMin = false;
      for (int i = 0; i < histogram.length; i++) {
         count += histogram[i];
         if (histogram[i] > 0) {
            if (!foundMin) {
               minimum = i;
               foundMin = true;
            }
            maximum = i;
         }
      }
      return ComponentStats.builder()
            .histogram(full, 0)
            .pixelCount(count)
            .minimum(minimum)
            .maximum(maximum)
            .build();
   }

   @Test
   public void testAutoscaleNormalRange() {
      // 256 bins, uniform distribution. With q=0.0 and min=0, max=255:
      // getQuantile(0.0) returns minimum_=0, getQuantile(1.0) returns maximum_+1=256,
      // so max = round(256)-1 = 255. Range = [0, 255], width=255 >= 2.
      long[] hist = new long[256];
      for (int i = 0; i < 256; i++) {
         hist[i] = 1;
      }
      ComponentStats s = buildStats(hist);
      long[] minMax = new long[2];
      s.getAutoscaleMinMaxForQuantile(0.0, minMax);
      assertEquals(0L, minMax[0]);
      assertEquals(255L, minMax[1]);
      assertTrue(minMax[1] - minMax[0] >= 2);
   }

   @Test
   public void testAutoscaleConstantImage() {
      // All pixels have intensity 100; range should be widened to [99, 101].
      long[] hist = new long[256];
      hist[100] = 1000;
      ComponentStats s = buildStats(hist);
      long[] minMax = new long[2];
      s.getAutoscaleMinMaxForQuantile(0.0, minMax);
      assertEquals(99L, minMax[0]);
      assertEquals(101L, minMax[1]);
      assertTrue(minMax[1] - minMax[0] >= 2);
   }

   @Test
   public void testAutoscaleConstantImageAtZero() {
      // All pixels have intensity 0 (at the histogram range minimum).
      // mid == 0 == getHistogramRangeMin(), so min=0, max=2.
      long[] hist = new long[256];
      hist[0] = 500;
      ComponentStats s = buildStats(hist);
      long[] minMax = new long[2];
      s.getAutoscaleMinMaxForQuantile(0.0, minMax);
      assertEquals(0L, minMax[0]);
      assertEquals(2L, minMax[1]);
      assertTrue(minMax[1] - minMax[0] >= 2);
   }

   @Test
   public void testAutoscaleConstantImageAtMax() {
      // All pixels saturated (intensity 255 = range max).
      // mid == 255 == getHistogramRangeMax(), so max=255, min=253.
      long[] hist = new long[256];
      hist[255] = 500;
      ComponentStats s = buildStats(hist);
      long[] minMax = new long[2];
      s.getAutoscaleMinMaxForQuantile(0.0, minMax);
      assertEquals(255L, minMax[1]);
      assertEquals(253L, minMax[0]);
      assertTrue(minMax[1] - minMax[0] >= 2);
   }

   @Test
   public void testAutoscaleSmallRangeWidth() {
      // Two adjacent intensities only — range width is 1, must be widened.
      long[] hist = new long[256];
      hist[50] = 100;
      hist[51] = 100;
      ComponentStats s = buildStats(hist);
      long[] minMax = new long[2];
      s.getAutoscaleMinMaxForQuantile(0.0, minMax);
      assertTrue(minMax[1] - minMax[0] >= 2);
   }

   @Test
   public void testAutoscaleResultAlwaysMinWidthTwo() {
      // Property test: for any single-valued image at any position in range,
      // the result width must be >= 2.
      long[] hist = new long[256];
      long[] minMax = new long[2];
      for (int i = 0; i < 256; i++) {
         java.util.Arrays.fill(hist, 0);
         hist[i] = 1;
         ComponentStats s = buildStats(hist);
         s.getAutoscaleMinMaxForQuantile(0.0, minMax);
         assertTrue("width < 2 at intensity " + i, minMax[1] - minMax[0] >= 2);
      }
   }
}