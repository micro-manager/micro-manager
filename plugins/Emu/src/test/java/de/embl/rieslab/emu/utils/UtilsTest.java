package de.embl.rieslab.emu.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UtilsTest {

  @Test
  public void testIsNumeric() {
    assertFalse(EmuUtils.isNumeric(null));
    assertFalse(EmuUtils.isNumeric(""));
    assertFalse(EmuUtils.isNumeric("d"));
    assertFalse(EmuUtils.isNumeric("+h485,12"));
    assertFalse(EmuUtils.isNumeric("+485,12d"));

    assertTrue(EmuUtils.isNumeric("156"));
    assertTrue(EmuUtils.isNumeric("+156"));
    assertTrue(EmuUtils.isNumeric("-156"));
    assertTrue(EmuUtils.isNumeric("846.1654"));
    assertTrue(EmuUtils.isNumeric("846,1654"));
    assertTrue(EmuUtils.isNumeric("+846.1654"));
    assertTrue(EmuUtils.isNumeric("+846,1654"));
    assertTrue(EmuUtils.isNumeric("-846.1654"));
    assertTrue(EmuUtils.isNumeric("-846,1654"));
  }

  @Test
  public void testIsInteger() {
    assertFalse(EmuUtils.isInteger(null));
    assertFalse(EmuUtils.isInteger(""));
    assertFalse(EmuUtils.isInteger("d"));
    assertFalse(EmuUtils.isInteger("15.46"));
    assertFalse(EmuUtils.isInteger("-15.46"));
    assertFalse(EmuUtils.isInteger("+15,46"));
    assertFalse(EmuUtils.isInteger("-15,46"));

    assertTrue(EmuUtils.isInteger("-6541"));
    assertTrue(EmuUtils.isInteger("+6541"));
    assertTrue(EmuUtils.isInteger("4581"));
  }

  // here should maybe change the method to accept ","
  @Test
  public void testIsFloat() {
    assertFalse(EmuUtils.isFloat(null));
    assertFalse(EmuUtils.isFloat(""));
    assertFalse(EmuUtils.isFloat("d"));
    assertFalse(EmuUtils.isFloat("+h485,12"));
    assertFalse(EmuUtils.isFloat("+485,12d"));

    assertTrue(EmuUtils.isFloat("156"));
    assertTrue(EmuUtils.isFloat("+156"));
    assertTrue(EmuUtils.isFloat("-156"));
    assertTrue(EmuUtils.isFloat("846.1654"));
    // assertTrue(utils.isFloat("846,165"));
    assertTrue(EmuUtils.isFloat("+846.1654"));
    // assertTrue(utils.isFloat("+846,1654"));
    assertTrue(EmuUtils.isFloat("-846.1654"));
    // assertTrue(utils.isFloat("-846,1654"));
  }

  @Test
  public void testRound() {
    double v = 15.123456789;
    assertEquals(15, EmuUtils.round(v, 0), 1E-20);
    assertEquals(15.1, EmuUtils.round(v, 1), 1E-20);
    assertEquals(15.12, EmuUtils.round(v, 2), 1E-20);
    assertEquals(15.123, EmuUtils.round(v, 3), 1E-20);
    assertEquals(15.1235, EmuUtils.round(v, 4), 1E-20);
    assertEquals(15.12346, EmuUtils.round(v, 5), 1E-20);
  }

  @Test
  public void testIsBool() {
    assertTrue(EmuUtils.isBool("true"));
    assertTrue(EmuUtils.isBool("false"));
    assertTrue(EmuUtils.isBool(String.valueOf(true)));
    assertTrue(EmuUtils.isBool(String.valueOf(false)));

    assertFalse(EmuUtils.isBool("0"));
    assertFalse(EmuUtils.isBool("1"));
    assertFalse(EmuUtils.isBool("True"));
    assertFalse(EmuUtils.isBool("False"));
  }
}
