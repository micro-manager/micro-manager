package org.micromanager.internal.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.text.ParseException;
import org.junit.Test;

/**
 * Regression tests for #2437: displayStringToDouble() must treat "." as the decimal
 * separator regardless of locale, and accept "," as an alternate decimal separator,
 * rather than trying to interpret either character as a locale-specific grouping
 * separator. A mistyped decimal point being silently misread as a grouping separator
 * (e.g. "1.30" becoming 130 under a locale that uses "." for grouping) is exactly what
 * caused #2437. These tests are independent of the JVM's default locale, since parsing
 * no longer depends on it.
 */
public class NumberUtilsLocaleTest {

   private static final double DELTA = 0.0000001;

   @Test
   public void parsesPlainDotDecimal() throws ParseException {
      assertEquals(1.3, NumberUtils.displayStringToDouble("1.3"), DELTA);
      assertEquals(1.3, NumberUtils.displayStringToDouble("1.30"), DELTA);
      assertEquals(1.3, NumberUtils.displayStringToDouble("1.300"), DELTA);
      // The value CalibrationListDlg seeds a new Pixel Size Calibration with; it must
      // remain accepted unmodified.
      assertEquals(0.0, NumberUtils.displayStringToDouble("0.00"), DELTA);
      // A typical pixel size, with a grouping-size-length (3-digit) fraction.
      assertEquals(0.065, NumberUtils.displayStringToDouble("0.065"), DELTA);
   }

   @Test
   public void parsesCommaAsAlternateDecimal() throws ParseException {
      assertEquals(1.3, NumberUtils.displayStringToDouble("1,3"), DELTA);
      assertEquals(-0.5, NumberUtils.displayStringToDouble("-0,5"), DELTA);
   }

   @Test
   public void doesNotTreatCommaAsGroupingSeparator() throws ParseException {
      // Deliberate trade-off (see #2437 discussion): "," is always read as a decimal
      // point, never as a thousands-grouping separator, since users essentially never
      // intentionally type a grouping separator into these fields.
      assertEquals(12.345, NumberUtils.displayStringToDouble("12,345"), DELTA);
   }

   @Test
   public void rejectsAmbiguousMultiSeparatorInput() {
      // Mixing separators (e.g. copy-pasted from software that grouped digits with one
      // separator and used the other as the decimal point) is rejected rather than
      // guessed at.
      assertThrowsParseException("1.234.567");
      assertThrowsParseException("1,234,567");
      assertThrowsParseException("1,234.567");
   }

   private static void assertThrowsParseException(String input) {
      try {
         NumberUtils.displayStringToDouble(input);
         fail("Expected a ParseException for \"" + input + "\"");
      } catch (ParseException expected) {
         // expected
      }
   }
}
