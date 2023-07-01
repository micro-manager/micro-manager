// Copyright (C) 2017 Open Imaging, Inc.
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.display.internal.imagestats;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Mark A. Tsuchida
 */
public class ComponentStatsTest {

   public ComponentStatsTest() {
   }

   @BeforeClass
   public static void setUpClass() {
   }

   @AfterClass
   public static void tearDownClass() {
   }

   @Before
   public void setUp() {
   }

   @After
   public void tearDown() {
   }

   @Test
   public void testEmpty() {
      IntegerComponentStats cs = IntegerComponentStats.builder().build();
      assertNull(cs.getInRangeHistogram());
   }

   @Test
   public void testSize1() {
      IntegerComponentStats cs = IntegerComponentStats.builder()
                  .histogram(new long[] {1, 2, 3}, 0)
                  .pixelCount(6)
                  .minimum(-1)
                  .maximum(1)
                  .sum(0)
                  .sumOfSquares(4)
                  .build();
      assertEquals(1, cs.getInRangeHistogram().length);
      assertEquals(2, cs.getInRangeHistogram()[0]);
      assertEquals(1, cs.getHistogramBinCount());
      assertEquals(1, cs.getHistogramBinWidth());
      assertEquals(1, cs.getPixelCountBelowRange());
      assertEquals(3, cs.getPixelCountAboveRange());
      assertEquals(0, cs.getHistogramRangeMin());
      assertEquals(0, cs.getHistogramRangeMax());
      assertEquals(6, cs.getPixelCount());
      assertEquals(0, cs.getMeanIntensity());
      assertEquals(1.0, cs.getQuantile(0.5), 0.001);
      assertEquals(0.0, cs.getQuantileIgnoringZeros(0.5), 0.001);
      assertEquals(1.0, cs.getQuantileIgnoringZeros(0.75), 0.001);
      assertEquals(4, cs.getSumOfSquares());
      assertTrue(cs.getStandardDeviation() > 0.0);
   }

   @Test
   public void testSize2() {
      IntegerComponentStats cs = IntegerComponentStats.builder()
            .histogram(new long[] { 0, 1, 2, 0 }, 1)
            .pixelCount(3)
            .minimum(0)
            .maximum(1)
            .sum(6)
            .sumOfSquares(14)
            .build();
      assertEquals(2, cs.getInRangeHistogram().length);
      assertEquals(1, cs.getInRangeHistogram()[0]);
      assertEquals(2, cs.getInRangeHistogram()[1]);
      assertEquals(2, cs.getHistogramBinCount());
      assertEquals(2, cs.getHistogramBinWidth());
      assertEquals(0, cs.getPixelCountBelowRange());
      assertEquals(0, cs.getPixelCountAboveRange());
      assertEquals(0, cs.getHistogramRangeMin());
      assertEquals(3, cs.getHistogramRangeMax());
      assertEquals(3, cs.getPixelCount());
      assertEquals(2, cs.getMeanIntensity());
      assertEquals(2.5, cs.getQuantile(0.5), 0.001);
      assertEquals(2.5, cs.getQuantileIgnoringZeros(0.5), 0.001);
      assertEquals(14, cs.getSumOfSquares());
      assertEquals(0.8165, cs.getStandardDeviation(), 0.001);
   }

   @Test
   public void test8bit() {
      // 256-bin histogram for 8-bit samples
      long[] hist = new long[258];
      hist[1] = 1;
      hist[2] = 2;
      hist[128] = 94;
      hist[255] = 2;
      hist[256] = 1;
      IntegerComponentStats cs = IntegerComponentStats.builder()
            .histogram(hist, 0)
            .pixelCount(100)
            .minimum(0)
            .maximum(255)
            .sum(0 * 1 + 1 * 2 + 127 * 94 + 254 * 2 + 255 * 1)
            .sumOfSquares(0 * 1 + 1 * 2 + 16129 * 94 + 64516 * 2 + 65025 * 1)
            .build();
      assertEquals(256, cs.getInRangeHistogram().length);
      assertEquals(1, cs.getInRangeHistogram()[0]);
      assertEquals(1, cs.getInRangeHistogram()[255]);
      assertEquals(256, cs.getHistogramBinCount());
      assertEquals(1, cs.getHistogramBinWidth());
      assertEquals(0, cs.getPixelCountBelowRange());
      assertEquals(0, cs.getPixelCountAboveRange());
      assertEquals(0, cs.getHistogramRangeMin());
      assertEquals(255, cs.getHistogramRangeMax());
      assertEquals(100, cs.getPixelCount());
      assertEquals(127, cs.getMeanIntensity());
      assertEquals(1.0, cs.getQuantile(0.01), 0.001);
      assertEquals(1.5, cs.getQuantile(0.02), 0.001);
      assertEquals(254.5, cs.getQuantile(0.98), 0.001);
      assertEquals(255.0, cs.getQuantile(0.99), 0.001);
   }

   @Test
   public void test16bit256() {
      // 256-bin histogram for 16-bit samples
      long[] hist = new long[258];
      for (int i = 1; i < 257; ++i) {
         hist[i] = 5;
      }
      IntegerComponentStats cs = IntegerComponentStats.builder()
            .histogram(hist, 8)
            .pixelCount(5 * 256)
            .minimum(0)
            .maximum(65535)
            .build();
      assertEquals(256, cs.getInRangeHistogram().length);
      assertEquals(5, cs.getInRangeHistogram()[0]);
      assertEquals(5, cs.getInRangeHistogram()[255]);
      assertEquals(256, cs.getHistogramBinCount());
      assertEquals(256, cs.getHistogramBinWidth());
      assertEquals(0, cs.getPixelCountBelowRange());
      assertEquals(0, cs.getPixelCountAboveRange());
      assertEquals(0, cs.getHistogramRangeMin());
      assertEquals(65535, cs.getHistogramRangeMax());
      assertEquals(5 * 256, cs.getPixelCount());
      assertEquals(655.36, cs.getQuantile(0.01), 0.001);
      assertEquals(1310.72, cs.getQuantile(0.02), 0.001);
      assertEquals(64225.28, cs.getQuantile(0.98), 0.001);
      assertEquals(64880.64, cs.getQuantile(0.99), 0.001);
   }
}