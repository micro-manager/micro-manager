/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.imagestats;

import org.junit.Test;
import static org.junit.Assert.*;

/** @author mark */
public class IntegerComponentStatsTest {
  public IntegerComponentStatsTest() {}

  private static final double e = 0.001;

  @Test
  public void testGetQuantile() {
    IntegerComponentStats s =
        IntegerComponentStats.builder().histogram(new long[] {0, 1, 0}, 0).pixelCount(1).build();
    assertEquals(0.0, s.getQuantile(0.0), e);
    assertEquals(1.0, s.getQuantile(1.0), e);
    assertEquals(0.5, s.getQuantile(0.5), e);

    s = IntegerComponentStats.builder().histogram(new long[] {0, 1, 2, 0}, 0).pixelCount(3).build();
    assertEquals(0.0, s.getQuantile(0.0), e);
    assertEquals(1.25, s.getQuantile(0.5), e);
    assertEquals(2.0, s.getQuantile(1.0), e);
  }

  @Test
  public void testGetQuantileOutOfRange() {
    IntegerComponentStats s =
        IntegerComponentStats.builder().histogram(new long[] {1, 1, 1, 1}, 0).pixelCount(4).build();
    assertEquals(0.0, s.getQuantile(0.0), e);
    assertEquals(0.0, s.getQuantile(0.25), e);
    assertEquals(1.0, s.getQuantile(0.5), e);
    assertEquals(2.0, s.getQuantile(0.75), e);
    assertEquals(2.0, s.getQuantile(1.0), e);
  }
}
