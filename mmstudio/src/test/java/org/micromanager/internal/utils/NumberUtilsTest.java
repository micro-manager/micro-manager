package org.micromanager.internal.utils;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class NumberUtilsTest {
   @Before
   public void beforeMethod() {
      // For now, the tests in this calss are written with the assumption that
      // the decimal point is '.'. Skip if this is not true.
      java.util.Locale loc = java.util.Locale.getDefault();
      org.junit.Assume.assumeTrue(new java.text.DecimalFormatSymbols(loc).
            getDecimalSeparator() == '.');
   }

   @Test
   public void doubleToStringIsCorrect() {
      // Whether we should be displaying "-0" is questionable. For now, test
      // for the existing behavior.
      assertThat(NumberUtils.doubleToDisplayString(0.0), anyOf(is("0"), is("-0")));
      assertThat(NumberUtils.doubleToDisplayString(0.00004), anyOf(is("0"), is("-0")));
      assertThat(NumberUtils.doubleToDisplayString(-0.00004), anyOf(is("0"), is("-0")));

      assertEquals("0.0001", NumberUtils.doubleToDisplayString(0.00005));
      assertEquals("-0.0001", NumberUtils.doubleToDisplayString(-0.00005));

      assertEquals("1", NumberUtils.doubleToDisplayString(1.0));
      assertEquals("1", NumberUtils.doubleToDisplayString(1.00004));
      assertEquals("1", NumberUtils.doubleToDisplayString(0.99995));
      assertEquals("1.0001", NumberUtils.doubleToDisplayString(1.00005));
      assertEquals("0.9999", NumberUtils.doubleToDisplayString(0.99994));

      assertEquals("-1", NumberUtils.doubleToDisplayString(-1.0));
      assertEquals("-1", NumberUtils.doubleToDisplayString(-1.00004));
      assertEquals("-1", NumberUtils.doubleToDisplayString(-0.99995));
      assertEquals("-1.0001", NumberUtils.doubleToDisplayString(-1.00005));
      assertEquals("-0.9999", NumberUtils.doubleToDisplayString(-0.99994));
   }

   @Test
   public void doubleToCoreStringIsCorrect() {
      assertThat(NumberUtils.doubleToCoreString(0.0), anyOf(is("0.0000"), is("-0.0000")));
      assertThat(NumberUtils.doubleToCoreString(0.00004), anyOf(is("0.0000"), is("-0.0000")));
      assertThat(NumberUtils.doubleToCoreString(-0.00004), anyOf(is("0.0000"), is("-0.0000")));

      assertEquals("0.0001", NumberUtils.doubleToCoreString(0.00005));
      assertEquals("-0.0001", NumberUtils.doubleToCoreString(-0.00005));

      assertEquals("1.0000", NumberUtils.doubleToCoreString(1.0));
      assertEquals("1.0000", NumberUtils.doubleToCoreString(1.00004));
      assertEquals("1.0000", NumberUtils.doubleToCoreString(0.99995));
      assertEquals("1.0001", NumberUtils.doubleToCoreString(1.00005));
      assertEquals("0.9999", NumberUtils.doubleToCoreString(0.99994));

      assertEquals("-1.0000", NumberUtils.doubleToCoreString(-1.0));
      assertEquals("-1.0000", NumberUtils.doubleToCoreString(-1.00004));
      assertEquals("-1.0000", NumberUtils.doubleToCoreString(-0.99995));
      assertEquals("-1.0001", NumberUtils.doubleToCoreString(-1.00005));
      assertEquals("-0.9999", NumberUtils.doubleToCoreString(-0.99994));
   }

   @Test
   public void displayToDoubleIsCorrect() throws java.text.ParseException {
      final double delta = 0.0000001;

      // displayStringToDouble() doesn't perform rounding, but do a sanity
      // check

      assertEquals(0.0, NumberUtils.displayStringToDouble("0"), delta);
      assertEquals(0.0, NumberUtils.displayStringToDouble("0.0"), delta);
      assertEquals(0.0, NumberUtils.displayStringToDouble("-0"), delta);
      assertEquals(0.0, NumberUtils.displayStringToDouble("-0.0"), delta);

      assertEquals(0.00004, NumberUtils.displayStringToDouble("0.00004"), delta);
      assertEquals(0.00005, NumberUtils.displayStringToDouble("0.00005"), delta);
      assertEquals(-0.00004, NumberUtils.displayStringToDouble("-0.00004"), delta);
      assertEquals(-0.00005, NumberUtils.displayStringToDouble("-0.00005"), delta);

      assertEquals(1.00004, NumberUtils.displayStringToDouble("1.00004"), delta);
      assertEquals(1.00005, NumberUtils.displayStringToDouble("1.00005"), delta);
      assertEquals(0.99995, NumberUtils.displayStringToDouble("0.99995"), delta);
      assertEquals(0.99994, NumberUtils.displayStringToDouble("0.99994"), delta);

      assertEquals(-1.00004, NumberUtils.displayStringToDouble("-1.00004"), delta);
      assertEquals(-1.00005, NumberUtils.displayStringToDouble("-1.00005"), delta);
      assertEquals(-0.99995, NumberUtils.displayStringToDouble("-0.99995"), delta);
      assertEquals(-0.99994, NumberUtils.displayStringToDouble("-0.99994"), delta);
   }

   /**
    * Regression test for #2437: a grouping separator that does not sit at a valid
    * grouping position (e.g. a decimal separator mistyped as the grouping separator,
    * which can happen when the locale used for number formatting is not what the user
    * expects) must be rejected rather than silently parsed as a different number.
    * A correctly-grouped number in the same locale must still parse correctly.
    */
   @Test
   public void displayStringToDoubleRejectsInvalidGrouping() throws java.text.ParseException {
      java.text.DecimalFormatSymbols symbols =
            new java.text.DecimalFormatSymbols(java.util.Locale.getDefault());
      char group = symbols.getGroupingSeparator();
      char decimal = symbols.getDecimalSeparator();
      org.junit.Assume.assumeTrue(group != decimal);

      // A validly-grouped number (three digits between separators) must parse fine.
      String validlyGrouped = "1" + group + "234";
      assertEquals(1234.0, NumberUtils.displayStringToDouble(validlyGrouped), 0.0000001);

      // A grouping separator followed by only two digits is not a valid group in any
      // locale, and must not silently be read as if the separator were absent.
      String invalidlyGrouped = "1" + group + "30";
      try {
         NumberUtils.displayStringToDouble(invalidlyGrouped);
         fail("Expected a ParseException for \"" + invalidlyGrouped + "\"");
      } catch (java.text.ParseException expected) {
         // expected
      }
   }
}
