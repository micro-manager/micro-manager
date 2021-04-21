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

package org.micromanager.internal.utils.performance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/** @author Mark A. Tsuchida */
public class IndexableOrderedSkipListTest {

  public IndexableOrderedSkipListTest() {}

  @BeforeClass
  public static void setUpClass() {}

  @AfterClass
  public static void tearDownClass() {}

  @Before
  public void setUp() {}

  @After
  public void tearDown() {}

  @Test
  public void testEmpty() {
    IndexableOrderedSkipList<Integer, Integer> instance = IndexableOrderedSkipList.create(1);
    assertEquals(0, instance.size());
    assertEquals(0, instance.sublist(0, 0).size());
    instance = IndexableOrderedSkipList.create(2);
    assertEquals(0, instance.size());
    assertEquals(0, instance.sublist(0, 0).size());
  }

  @Test
  public void testSize1SingleLevel() {
    IndexableOrderedSkipList<Integer, Integer> instance = IndexableOrderedSkipList.create(1);
    instance.overrideFiftyPercentChanceForDeterministicTesting = false;
    assertEquals(0, instance.insert(3, 7));
    assertEquals(1, instance.size());
    assertEquals(3, instance.get(0).getKey().intValue());
    assertEquals(7, instance.get(0).getValue().intValue());
    assertEquals(1, instance.sublist(0, 1).size());
    assertEquals(3, instance.sublist(0, 1).get(0).getKey().intValue());
    assertEquals(7, instance.sublist(0, 1).get(0).getValue().intValue());
    instance.removeOldest();
    assertEquals(0, instance.size());

    instance = IndexableOrderedSkipList.create(1);
    instance.overrideFiftyPercentChanceForDeterministicTesting = true;
    assertEquals(0, instance.insert(3, 7));
    assertEquals(1, instance.size());
    assertEquals(3, instance.get(0).getKey().intValue());
    assertEquals(7, instance.get(0).getValue().intValue());
    assertEquals(1, instance.sublist(0, 1).size());
    assertEquals(3, instance.sublist(0, 1).get(0).getKey().intValue());
    assertEquals(7, instance.sublist(0, 1).get(0).getValue().intValue());
    instance.removeOldest();
    assertEquals(0, instance.size());
  }

  @Test
  public void testSize1TwoLevel() {
    IndexableOrderedSkipList<Integer, Integer> instance = IndexableOrderedSkipList.create(2);
    instance.overrideFiftyPercentChanceForDeterministicTesting = false;
    assertEquals(0, instance.insert(3, 7));
    System.out.println(instance.dump());
    assertEquals(1, instance.size());
    assertEquals(3, instance.get(0).getKey().intValue());
    assertEquals(7, instance.get(0).getValue().intValue());
    assertEquals(1, instance.sublist(0, 1).size());
    assertEquals(3, instance.sublist(0, 1).get(0).getKey().intValue());
    assertEquals(7, instance.sublist(0, 1).get(0).getValue().intValue());
    instance.removeOldest();
    assertEquals(0, instance.size());

    instance = IndexableOrderedSkipList.create(2);
    instance.overrideFiftyPercentChanceForDeterministicTesting = true;
    assertEquals(0, instance.insert(3, 7));
    System.out.println(instance.dump());
    assertEquals(1, instance.size());
    assertEquals(3, instance.get(0).getKey().intValue());
    assertEquals(7, instance.get(0).getValue().intValue());
    assertEquals(1, instance.sublist(0, 1).size());
    assertEquals(3, instance.sublist(0, 1).get(0).getKey().intValue());
    assertEquals(7, instance.sublist(0, 1).get(0).getValue().intValue());
    instance.removeOldest();
    assertEquals(0, instance.size());
  }

  @Test
  public void testSize2TwoLevel() {
    IndexableOrderedSkipList<Integer, Integer> instance = IndexableOrderedSkipList.create(2);
    instance.overrideFiftyPercentChanceForDeterministicTesting = false;
    assertEquals(0, instance.insert(5, 0));
    assertEquals(1, instance.size());
    // Insertion point in upper level is at both left edge and right edge
    instance.overrideFiftyPercentChanceForDeterministicTesting = true;
    assertEquals(1, instance.insert(10, 0));
    System.out.println(instance.dump());
    assertEquals(2, instance.size());

    instance = IndexableOrderedSkipList.create(2);
    instance.overrideFiftyPercentChanceForDeterministicTesting = true;
    assertEquals(0, instance.insert(4, 0));
    // Right edge
    assertEquals(1, instance.insert(10, 0));
    System.out.println(instance.dump());
    assertEquals(2, instance.size());

    instance = IndexableOrderedSkipList.create(2);
    instance.overrideFiftyPercentChanceForDeterministicTesting = true;
    assertEquals(0, instance.insert(8, 0));
    // Left edge
    assertEquals(0, instance.insert(2, 0));
    System.out.println(instance.dump());
    assertEquals(2, instance.size());

    instance = IndexableOrderedSkipList.create(2);
    instance.overrideFiftyPercentChanceForDeterministicTesting = true;
    assertEquals(0, instance.insert(4, 0));
    assertEquals(1, instance.insert(8, 0));
    // Middle
    assertEquals(1, instance.insert(6, 0));
    System.out.println(instance.dump());
    assertEquals(3, instance.size());
  }

  @Test
  public void testPerformance100() {
    // Note: Results are rather variable even with 10 iterations
    final int MAX_LEVEL = 9;
    final int ITERATIONS = 10;
    List<List<Double>> times = new ArrayList<List<Double>>();
    for (int levels = 1; levels <= MAX_LEVEL; ++levels) {
      times.add(new ArrayList<Double>());
    }
    for (int iteration = 0; iteration < ITERATIONS; ++iteration) {
      for (int levels = 1; levels <= MAX_LEVEL; ++levels) {
        IndexableOrderedSkipList<Double, Integer> instance =
            IndexableOrderedSkipList.create(levels);
        Random random = new Random();
        CPUTimer timer = CPUTimer.createStarted();
        for (int i = 0; i < 100000; ++i) {
          instance.insert((double) i, 0);
          if (i > 100) {
            instance.removeOldest();
          }
          int index = Math.min(i, 33);
          if (index == 0) {
            continue;
          }
          List<Map.Entry<Double, Integer>> sublist = instance.sublist(index - 1, 2);
          assertEquals(2, sublist.size());
        }
        times.get(levels - 1).add(timer.getMs());
      }
    }
    for (int levels = 1; levels <= MAX_LEVEL; ++levels) {
      double avgMs = 0.0;
      for (Double d : times.get(levels - 1)) {
        avgMs += d;
      }
      avgMs /= ITERATIONS;
      System.out.println(String.format("%d-level: %.3g ms", levels, avgMs));
    }
  }
}
