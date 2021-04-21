/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.imagestats;

import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/** @author mark */
public class PowerOf2BinMapperTest {
  public PowerOf2BinMapperTest() {}

  @BeforeClass
  public static void setUpClass() {}

  @AfterClass
  public static void tearDownClass() {}

  @Before
  public void setUp() {}

  @After
  public void tearDown() {}

  @Test
  public void testCreate16_8() {
    PowerOf2BinMapper<UnsignedShortType> mapper = PowerOf2BinMapper.create(16, 8);
    assertEquals(256 + 2, mapper.getBinCount());
    assertEquals(65535, mapper.getEndOfRange());
  }

  @Test
  public void testEdges16_8() {
    PowerOf2BinMapper<UnsignedShortType> mapper = PowerOf2BinMapper.create(16, 8);

    UnsignedShortType value = new UnsignedShortType(0);

    mapper.getLowerBound(0, value);
    assertEquals(0L, value.getIntegerLong());
    assertFalse(mapper.includesLowerBound(0));

    mapper.getUpperBound(0, value);
    assertEquals(0L, value.getIntegerLong());
    assertFalse(mapper.includesUpperBound(0));

    mapper.getLowerBound(1, value);
    assertEquals(0L, value.getIntegerLong());
    assertTrue(mapper.includesLowerBound(1));

    mapper.getUpperBound(1, value);
    assertEquals(255L, value.getIntegerLong());
    assertTrue(mapper.includesUpperBound(1));

    mapper.getLowerBound(256, value);
    assertEquals(65536L - 256L, value.getIntegerLong());
    assertTrue(mapper.includesLowerBound(256));

    mapper.getUpperBound(256, value);
    assertEquals(65535L, value.getIntegerLong());
    assertTrue(mapper.includesUpperBound(256));

    mapper.getLowerBound(257, value);
    assertEquals(65535L, value.getIntegerLong());
    assertFalse(mapper.includesLowerBound(257));

    mapper.getUpperBound(257, value);
    assertEquals(65535L, value.getIntegerLong());
    assertFalse(mapper.includesLowerBound(257));
  }

  @Test
  public void testMapping16_8() {
    PowerOf2BinMapper<UnsignedShortType> mapper = PowerOf2BinMapper.create(16, 8);

    UnsignedShortType value = new UnsignedShortType(0);

    value.setInteger(0);
    assertEquals(1, mapper.map(value));

    value.setInteger(1);
    assertEquals(1, mapper.map(value));

    value.setInteger(255);
    assertEquals(1, mapper.map(value));

    value.setInteger(256);
    assertEquals(2, mapper.map(value));

    value.setInteger(65535);
    assertEquals(256, mapper.map(value));
  }
}
