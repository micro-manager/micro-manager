package de.embl.rieslab.emu.micromanager.mmproperties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.embl.rieslab.emu.controller.log.Logger;
import org.junit.Test;

public class IntegerMMPropertyTest {

   @Test
   public void testAreEquals() {
      final IntegerMMProperty prop = new IntegerMMProperty(null, new Logger(), "", "", false) {
         @Override
         public Integer getValue() { // avoids NullPointerException
            return 0;
         }
      };
      assertTrue(prop.areEquals(new Integer(4), new Integer(4)));
   }

   @Test
   public void testArrayFromString() {
      final IntegerMMProperty prop = new IntegerMMProperty(null, new Logger(), "", "", false) {
         @Override
         public Integer getValue() { // avoids NullPointerException
            return 0;
         }
      };

      final String[] s = {"1", "2", "3"};
      Integer[] ints = prop.arrayFromStrings(s);

      assertEquals(s.length, ints.length);
      for (int i = 0; i < s.length; i++) {
         assertEquals(Integer.valueOf(s[i]), ints[i]);
      }
   }

   @Test
   public void testConvertIntegerToString() {
      final IntegerMMProperty prop = new IntegerMMProperty(null, new Logger(), "", "", false) {
         @Override
         public Integer getValue() { // avoids NullPointerException
            return 0;
         }
      };
      final String s = "4";
      final Integer val = Integer.valueOf(s);
      assertEquals(s, prop.convertToString(val));
   }

   @Test
   public void testConvertStringToValue() {
      final IntegerMMProperty prop = new IntegerMMProperty(null, new Logger(), "", "", false) {
         @Override
         public Integer getValue() { // avoids NullPointerException
            return 0;
         }
      };
      final Integer i = new Integer(5);
      assertEquals(i, prop.convertToValue(i.toString()));

      // test that it rounds double values
      Double d = i + 0.5;
      assertEquals(i, prop.convertToValue(d.toString()));
   }

   @Test
   public void testConvertDoubleToValue() {
      final IntegerMMProperty prop = new IntegerMMProperty(null, new Logger(), "", "", false) {
         @Override
         public Integer getValue() { // avoids NullPointerException
            return 0;
         }
      };
      final Integer i = new Integer(-5);
      double d = i - 0.5;
      assertEquals(i, prop.convertToValue(d));
   }

   @Test
   public void testConvertIntegerToValue() {
      final IntegerMMProperty prop = new IntegerMMProperty(null, new Logger(), "", "", false) {
         @Override
         public Integer getValue() { // avoids NullPointerException
            return 0;
         }
      };
      final int i = 5;
      assertEquals(new Integer(i), prop.convertToValue(i));
   }

   @Test
   public void testIntegerProperty() {
      final String device = "My device";
      final String property = "My property";
      final IntegerMMProperty prop =
            new IntegerMMProperty(null, new Logger(), device, property, false) {
               @Override
               public Integer getValue() { // avoids NullPointerException
                  return 0;
               }
            };

      assertEquals(device, prop.getDeviceLabel());
      assertEquals(property, prop.getMMPropertyLabel());
      assertEquals(device + "-" + property, prop.getHash());
      assertEquals(MMProperty.MMPropertyType.INTEGER, prop.getType());

      assertFalse(prop.isReadOnly());

      assertFalse(prop.isAllowed(null));
      assertTrue(prop.isAllowed(0));
      assertTrue(prop.isAllowed(-5));
      assertTrue(prop.isAllowed(10));

      assertTrue(prop.isStringAllowed("0"));
      assertTrue(prop.isStringAllowed("-5"));
      assertTrue(prop.isStringAllowed("10"));
      assertTrue(prop.isStringAllowed("10.1564")); // is converted to int
   }

   @Test
   public void testIntegerPropertyReadOnly() {
      final String device = "My device";
      final String property = "My property";
      final IntegerMMProperty prop =
            new IntegerMMProperty(null, new Logger(), device, property, true) {
               @Override
               public Integer getValue() { // avoids NullPointerException
                  return 0;
               }
            };

      assertTrue(prop.isReadOnly());

      assertFalse(prop.isAllowed(null));
      assertFalse(prop.isAllowed(0));
      assertFalse(prop.isAllowed(-5));
      assertFalse(prop.isAllowed(10));

      assertFalse(prop.isStringAllowed("0"));
      assertFalse(prop.isStringAllowed("-5"));
      assertFalse(prop.isStringAllowed("10"));
      assertFalse(prop.isStringAllowed("10.1564"));
   }


   @Test
   public void testAllowedValues() {
      final String[] vals = {"1", "2", "3"};
      final IntegerMMProperty prop = new IntegerMMProperty(null, new Logger(), "", "", vals) {
         @Override
         public Integer getValue() { // avoids NullPointerException
            return 0;
         }
      };

      Integer[] valsCopy = prop.getAllowedValues();
      assertEquals(vals.length, valsCopy.length);
      for (int i = 0; i < vals.length; i++) {
         assertEquals(vals[i], valsCopy[i].toString());
      }

      for (int i = 0; i < vals.length; i++) {
         assertTrue(prop.isAllowed(Integer.valueOf(vals[i])));
         assertTrue(prop.isStringAllowed(vals[i]));
      }

      assertFalse(prop.isAllowed(null));
      assertFalse(prop.isStringAllowed(null));
      assertFalse(prop.isStringAllowed("124.45"));
      assertTrue(prop.isStringAllowed("1.0"));
      assertFalse(prop.isAllowed(4));
      assertFalse(prop.isStringAllowed("4.45"));
   }

   @Test
   public void testLimitedValues() {
      final Integer min_int = -15;
      final Integer max_int = 45;
      final double min = -15.015;
      final double max = 45.874;
      final IntegerMMProperty prop = new IntegerMMProperty(null, new Logger(), "", "", min,
            max) { // inverted min and max on purpose
         @Override
         public Integer getValue() { // avoids NullPointerException
            return 0;
         }
      };

      assertEquals(min_int, prop.getMin());
      assertEquals(max_int, prop.getMax());

      assertFalse(prop.isAllowed(null));
      assertFalse(prop.isAllowed(new Integer(-17)));
      assertFalse(prop.isAllowed(new Integer(50)));
      assertFalse(prop.isStringAllowed(null));
      assertFalse(prop.isStringAllowed("-17"));
      assertFalse(prop.isStringAllowed("50"));
      assertFalse(prop.isStringAllowed("-16.01"));
      assertFalse(prop.isStringAllowed("46.8"));

      assertTrue(prop.isAllowed(min_int));
      assertTrue(prop.isAllowed(max_int));
      assertTrue(prop.isAllowed(new Integer(0)));
      assertTrue(prop.isAllowed(new Integer(11)));
      assertTrue(prop.isStringAllowed(min_int.toString()));
      assertTrue(prop.isStringAllowed(max_int.toString()));
      assertTrue(prop.isStringAllowed("0.12"));
      assertTrue(prop.isStringAllowed("11"));
      assertTrue(prop.isStringAllowed("-15.5"));
      assertTrue(prop.isStringAllowed("45.9"));
   }
}
