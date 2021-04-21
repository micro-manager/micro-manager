package de.embl.rieslab.emu.micromanager.mmproperties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import de.embl.rieslab.emu.controller.log.Logger;

public class FloatMMPropertyTest {

  @Test
  public void testAreEquals() {
    final FloatMMProperty prop =
        new FloatMMProperty(null, new Logger(), "", "", false) {
          @Override
          public Float getValue() { // avoids NullPointerException
            return new Float(0);
          }
        };
    assertTrue(prop.areEquals(new Float(4.15), new Float(4.15)));
  }

  @Test
  public void testArrayFromString() {
    final FloatMMProperty prop =
        new FloatMMProperty(null, new Logger(), "", "", false) {
          @Override
          public Float getValue() { // avoids NullPointerException
            return new Float(0);
          }
        };

    final String[] s = {"1.089", "2.415", "3.01"};
    Float[] floats = prop.arrayFromStrings(s);

    assertEquals(s.length, floats.length);
    for (int i = 0; i < s.length; i++) assertEquals(Float.valueOf(s[i]), floats[i]);
  }

  @Test
  public void testConvertFloatToString() {
    final FloatMMProperty prop =
        new FloatMMProperty(null, new Logger(), "", "", false) {
          @Override
          public Float getValue() { // avoids NullPointerException
            return new Float(0);
          }
        };
    final String s = "4.458";
    final Float val = Float.valueOf(s);
    assertEquals(s, prop.convertToString(val));
  }

  @Test
  public void testConvertStringToValue() {
    final FloatMMProperty prop =
        new FloatMMProperty(null, new Logger(), "", "", false) {
          @Override
          public Float getValue() { // avoids NullPointerException
            return new Float(0);
          }
        };
    final Float i = new Float(5.4588786561);
    assertEquals(i, prop.convertToValue(i.toString()));
  }

  @Test
  public void testConvertDoubleToValue() {
    final FloatMMProperty prop =
        new FloatMMProperty(null, new Logger(), "", "", false) {
          @Override
          public Float getValue() { // avoids NullPointerException
            return new Float(0);
          }
        };
    double d = -0.78451114848535;
    final Float i = new Float(d);
    assertEquals(i, prop.convertToValue(d));
  }

  @Test
  public void testConvertIntegerToValue() {
    final FloatMMProperty prop =
        new FloatMMProperty(null, new Logger(), "", "", false) {
          @Override
          public Float getValue() { // avoids NullPointerException
            return new Float(0);
          }
        };
    final int i = 5;
    assertEquals(new Float(i), prop.convertToValue(i));
  }

  @Test
  public void testFloatProperty() {
    final String device = "My device";
    final String property = "My property";
    final FloatMMProperty prop =
        new FloatMMProperty(null, new Logger(), device, property, false) {
          @Override
          public Float getValue() { // avoids NullPointerException
            return new Float(0);
          }
        };

    assertEquals(device, prop.getDeviceLabel());
    assertEquals(property, prop.getMMPropertyLabel());
    assertEquals(device + "-" + property, prop.getHash());
    assertEquals(MMProperty.MMPropertyType.FLOAT, prop.getType());

    assertFalse(prop.isReadOnly());

    assertFalse(prop.isAllowed(null));
    assertTrue(prop.isAllowed((float) 0.4856));
    assertTrue(prop.isAllowed((float) -5));
    assertTrue(prop.isAllowed((float) 10));

    assertTrue(prop.isStringAllowed("0.484"));
    assertTrue(prop.isStringAllowed("-5.787"));
    assertTrue(prop.isStringAllowed("10"));
    assertTrue(prop.isStringAllowed("10.1564"));
  }

  @Test
  public void testFloatPropertyReadOnly() {
    final String device = "My device";
    final String property = "My property";
    final FloatMMProperty prop =
        new FloatMMProperty(null, new Logger(), device, property, true) {
          @Override
          public Float getValue() { // avoids NullPointerException
            return new Float(0);
          }
        };

    assertTrue(prop.isReadOnly());

    assertFalse(prop.isAllowed(null));
    assertFalse(prop.isAllowed((float) 0.46846));
    assertFalse(prop.isAllowed((float) -5));
    assertFalse(prop.isAllowed((float) 10.488));

    assertFalse(prop.isStringAllowed("0"));
    assertFalse(prop.isStringAllowed("-5"));
    assertFalse(prop.isStringAllowed("10"));
    assertFalse(prop.isStringAllowed("10.1564"));
  }

  @Test
  public void testAllowedValues() {
    final String[] vals = {"5.68463", "2.4564", "3.897211"};
    final FloatMMProperty prop =
        new FloatMMProperty(null, new Logger(), "", "", vals) {
          @Override
          public Float getValue() { // avoids NullPointerException
            return new Float(0);
          }
        };

    Float[] vals_copy = prop.getAllowedValues();
    assertEquals(vals.length, vals_copy.length);
    for (int i = 0; i < vals.length; i++) assertEquals(vals[i], vals_copy[i].toString());

    for (int i = 0; i < vals.length; i++) {
      assertTrue(prop.isAllowed(Float.valueOf(vals[i])));
      assertTrue(prop.isStringAllowed(vals[i]));
    }

    assertFalse(prop.isAllowed(null));
    assertFalse(prop.isStringAllowed(null));
    assertFalse(prop.isStringAllowed("124.45"));
    assertFalse(prop.isAllowed((float) 4));
    assertFalse(prop.isAllowed((float) 4.8498115465));
    assertFalse(prop.isStringAllowed("4.45"));
  }

  @Test
  public void testLimitedValues() {
    final double min = -15.015;
    final double max = 45.874;
    final Float min_int = (float) min;
    final Float max_int = (float) max;
    final FloatMMProperty prop =
        new FloatMMProperty(null, new Logger(), "", "", max, min) {
          @Override
          public Float getValue() { // avoids NullPointerException
            return new Float(0);
          }
        };

    assertEquals(min_int, prop.getMin());
    assertEquals(max_int, prop.getMax());

    assertFalse(prop.isAllowed(null));
    assertFalse(prop.isAllowed(new Float(-17.454)));
    assertFalse(prop.isAllowed(new Float(50.484)));
    assertFalse(prop.isStringAllowed(null));
    assertFalse(prop.isStringAllowed("-16.01"));
    assertFalse(prop.isStringAllowed("46.8"));

    assertTrue(prop.isAllowed(min_int));
    assertTrue(prop.isAllowed(max_int));
    assertTrue(prop.isAllowed(new Float(0.455)));
    assertTrue(prop.isAllowed(new Float(11.4885456)));
    assertFalse(prop.isAllowed(Float.NaN));
    assertTrue(prop.isStringAllowed(min_int.toString()));
    assertTrue(prop.isStringAllowed(max_int.toString()));
    assertTrue(prop.isStringAllowed("0.12"));
    assertTrue(prop.isStringAllowed("11"));
    assertTrue(prop.isStringAllowed("-15.014"));
    assertTrue(prop.isStringAllowed("45.873"));

    assertFalse(prop.isStringAllowed("-15.01501"));
    assertFalse(prop.isStringAllowed("45.87401"));
  }
}
