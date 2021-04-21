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

package org.micromanager.display.internal.animate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.micromanager.data.Coords;
import org.micromanager.data.internal.DefaultCoords;

/** @author Mark A. Tsuchida */
public class DataCoordsAnimationStateTest {

  List<String> mockAxes_;
  Set<String> mockAnimatedAxes_;
  Map<Coords, Boolean> mockDataset_;
  DataCoordsAnimationState.CoordsProvider mockCoordsProvider_;

  public DataCoordsAnimationStateTest() {}

  @BeforeClass
  public static void setUpClass() {}

  @AfterClass
  public static void tearDownClass() {}

  @Before
  public void setUp() {
    mockAxes_ = new ArrayList<String>();
    mockAnimatedAxes_ = new HashSet<String>();
    mockDataset_ = new HashMap<Coords, Boolean>();
    mockCoordsProvider_ =
        new DataCoordsAnimationState.CoordsProvider() {
          @Override
          public List<String> getOrderedAxes() {
            return new ArrayList<String>(mockAxes_);
          }

          @Override
          public int getMaximumExtentOfAxis(String axis) {
            int max = 0;
            for (Coords c : mockDataset_.keySet()) {
              if (mockDataset_.get(c)) {
                max = Math.max(max, c.getIndex(axis));
              }
            }
            return max;
          }

          @Override
          public boolean coordsExist(Coords c) {
            try {
              return mockDataset_.get(c);
            } catch (NullPointerException npe) {
              return false;
            }
          }

          @Override
          public Collection<String> getAnimatedAxes() {
            return new ArrayList<String>(mockAnimatedAxes_);
          }
        };
  }

  @After
  public void tearDown() {}

  @Test
  public void testCreate() {
    DataCoordsAnimationState result = DataCoordsAnimationState.create(mockCoordsProvider_);
    assertNotNull(result);
  }

  @Test
  public void testGetSetAdvance() {
    mockAxes_ = Arrays.asList(DefaultCoords.TIME, DefaultCoords.CHANNEL);
    for (int t = 0; t < 10; ++t) {
      for (int ch = 0; ch < 3; ++ch) {
        mockDataset_.put(new DefaultCoords.Builder().time(t).channel(ch).build(), Boolean.TRUE);
      }
    }
    mockAnimatedAxes_ = Collections.singleton(DefaultCoords.TIME);

    DataCoordsAnimationState instance = DataCoordsAnimationState.create(mockCoordsProvider_);

    // Initially, coords should have all axes with indices zero
    Coords c = instance.getAnimationPosition();
    List<String> axes = c.getAxes();
    assertEquals(2, axes.size());
    assertTrue(axes.contains(DefaultCoords.TIME));
    assertTrue(axes.contains(DefaultCoords.CHANNEL));
    assertEquals(0, c.getTime());
    assertEquals(0, c.getChannel());

    // Set coords should be recovered, preserving unset axes
    instance.setAnimationPosition(new DefaultCoords.Builder().time(3).build());
    c = instance.getAnimationPosition();
    axes = c.getAxes();
    assertEquals(axes.size(), 2);
    assertTrue(axes.contains(DefaultCoords.TIME));
    assertTrue(axes.contains(DefaultCoords.CHANNEL));
    assertEquals(3, c.getTime());
    assertEquals(0, c.getChannel());

    // Set coords should be recovered, preserving unset axes
    instance.setAnimationPosition(new DefaultCoords.Builder().channel(1).build());
    c = instance.getAnimationPosition();
    assertEquals(2, c.getAxes().size());
    assertEquals(3, c.getTime());
    assertEquals(1, c.getChannel());

    // Advance by one should advance time (animated) but not channel (not
    // animated)
    c = instance.advanceAnimationPosition(1.0);
    assertEquals(2, c.getAxes().size());
    assertEquals(4, c.getTime());
    assertEquals(1, c.getChannel());

    mockAnimatedAxes_ =
        new HashSet<String>(Arrays.asList(DefaultCoords.TIME, DefaultCoords.CHANNEL));

    // Advance by many; should end up in expected coords
    c = instance.advanceAnimationPosition(11.0);
    assertEquals(2, c.getAxes().size());
    assertEquals(8, c.getTime());
    assertEquals(0, c.getChannel());

    mockDataset_.put(new DefaultCoords.Builder().time(8).channel(1).build(), Boolean.FALSE);

    // Check skipping of nonexistent coords
    c = instance.advanceAnimationPosition(1.0);
    assertEquals(2, c.getAxes().size());
    assertEquals(8, c.getTime());
    assertEquals(2, c.getChannel());

    for (int ch = 0; ch < 3; ++ch) {
      mockDataset_.put(new DefaultCoords.Builder().time(9).channel(ch).build(), Boolean.FALSE);
    }

    // Another test for skipping
    c = instance.advanceAnimationPosition(1.0);
    assertEquals(2, c.getAxes().size());
    assertEquals(0, c.getTime());
    assertEquals(0, c.getChannel());
  }

  @Test
  public void testEmptyDataset() {
    DataCoordsAnimationState instance = DataCoordsAnimationState.create(mockCoordsProvider_);
    Coords c = instance.advanceAnimationPosition(1.0);
    assertEquals(0, c.getAxes().size());
  }
}
