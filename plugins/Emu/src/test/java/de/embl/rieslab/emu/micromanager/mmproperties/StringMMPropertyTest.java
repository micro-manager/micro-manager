package de.embl.rieslab.emu.micromanager.mmproperties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import de.embl.rieslab.emu.controller.log.Logger;

public class StringMMPropertyTest {

  @Test
  public void testAreEquals() {
    final StringMMProperty prop =
        new StringMMProperty(null, new Logger(), MMProperty.MMPropertyType.STRING, "", "") {
          @Override
          public String getValue() { // avoids NullPointerException
            return "";
          }
        };
    assertTrue(prop.areEquals("1234", "1234"));
  }

  @Test
  public void testArrayFromString() {
    final StringMMProperty prop =
        new StringMMProperty(null, new Logger(), MMProperty.MMPropertyType.STRING, "", "") {
          @Override
          public String getValue() { // avoids NullPointerException
            return "";
          }
        };

    final String[] s = {"1.089", "2.415", "3.01"};
    String[] vals = prop.arrayFromStrings(s);

    assertEquals(s.length, vals.length);
    for (int i = 0; i < s.length; i++) assertEquals(s[i], vals[i]);
  }

  @Test
  public void testConvertFloatToString() {
    final StringMMProperty prop =
        new StringMMProperty(null, new Logger(), MMProperty.MMPropertyType.STRING, "", "") {
          @Override
          public String getValue() { // avoids NullPointerException
            return "";
          }
        };

    final String s = "4.56";
    assertEquals(s, prop.convertToString(s));
  }

  @Test
  public void testConvertStringToValue() {
    final StringMMProperty prop =
        new StringMMProperty(null, new Logger(), MMProperty.MMPropertyType.STRING, "", "") {
          @Override
          public String getValue() { // avoids NullPointerException
            return "";
          }
        };
    final Float i = new Float(5.4588786561);
    assertEquals(i.toString(), prop.convertToValue(i.toString()));
  }

  @Test
  public void testConvertDoubleToValue() {
    final StringMMProperty prop =
        new StringMMProperty(null, new Logger(), MMProperty.MMPropertyType.STRING, "", "") {
          @Override
          public String getValue() { // avoids NullPointerException
            return "";
          }
        };
    final String s = "1.465498";
    assertEquals(s, prop.convertToValue(new Double(s)));
  }

  @Test
  public void testConvertIntegerToValue() {
    final StringMMProperty prop =
        new StringMMProperty(null, new Logger(), MMProperty.MMPropertyType.STRING, "", "") {
          @Override
          public String getValue() { // avoids NullPointerException
            return "";
          }
        };
    final String s = "2";
    assertEquals(s, prop.convertToValue(new Integer(s)));
  }

  @Test
  public void testStringProperty() {
    final String device = "My device";
    final String property = "My property";
    final StringMMProperty prop =
        new StringMMProperty(
            null, new Logger(), MMProperty.MMPropertyType.STRING, device, property) {
          @Override
          public String getValue() { // avoids NullPointerException
            return "";
          }
        };

    assertEquals(device, prop.getDeviceLabel());
    assertEquals(property, prop.getMMPropertyLabel());
    assertEquals(device + "-" + property, prop.getHash());
    assertEquals(MMProperty.MMPropertyType.STRING, prop.getType());

    assertTrue(prop.isReadOnly());

    assertFalse(prop.isAllowed(null));
    assertFalse(prop.isAllowed("0.484"));
    assertFalse(prop.isAllowed("-5.787"));
    assertFalse(prop.isAllowed("10"));
    assertFalse(prop.isAllowed("10.1564"));
    assertFalse(prop.isAllowed("dsfgrgdg"));

    assertFalse(prop.isStringAllowed((String) null));
    assertFalse(prop.isStringAllowed("0.484"));
    assertFalse(prop.isStringAllowed("-5.787"));
    assertFalse(prop.isStringAllowed("10"));
    assertFalse(prop.isStringAllowed("10.1564"));
    assertFalse(prop.isStringAllowed("fdfdsfs"));
  }

  @Test
  public void testAllowedValues() {
    final String[] vals = {"5.68463", "-2.4564dadw", "sdfefs"};
    final StringMMProperty prop =
        new StringMMProperty(null, new Logger(), MMProperty.MMPropertyType.STRING, "", "", vals) {
          @Override
          public String getValue() { // avoids NullPointerException
            return "";
          }
        };

    String[] vals_copy = prop.getAllowedValues();
    assertEquals(vals.length, vals_copy.length);
    for (int i = 0; i < vals.length; i++) assertEquals(vals[i], vals_copy[i].toString());

    for (int i = 0; i < vals.length; i++) {
      assertTrue(prop.isAllowed(vals[i]));
      assertTrue(prop.isStringAllowed(vals[i]));
    }

    assertFalse(prop.isAllowed(null));
    assertFalse(prop.isStringAllowed(null));
    assertFalse(prop.isStringAllowed("124.45"));
    assertFalse(prop.isAllowed("sdfef"));
    assertFalse(prop.isAllowed("-2.4564"));
    assertFalse(prop.isStringAllowed("4.45"));
  }
}
