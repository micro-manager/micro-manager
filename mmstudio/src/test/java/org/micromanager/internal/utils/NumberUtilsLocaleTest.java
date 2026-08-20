package org.micromanager.internal.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import org.junit.Test;

/**
 * Regression tests for #2437/#2444: displayStringToDouble() must treat "." as the
 * decimal separator regardless of locale, and accept "," as an alternate decimal
 * separator, rather than trying to interpret either character as a locale-specific
 * grouping separator. A mistyped decimal point being silently misread as a grouping
 * separator (e.g. "1.30" becoming 130 under a locale that uses "." for grouping) is
 * exactly what caused #2437. displayStringToInt/Long (via parseIntegerStrict) instead
 * keep locale-aware grouping support, since integers have no decimal-point ambiguity to
 * confuse a grouping separator with. These tests are independent of the JVM's default
 * locale: the double tests because parsing no longer depends on it, and the integer
 * tests because they inject an explicit format.
 */
public class NumberUtilsLocaleTest {

   private static final double DELTA = 0.0000001;

   private static NumberFormat formatWithSeparators(char decimalSep, char groupingSep) {
      DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
      symbols.setDecimalSeparator(decimalSep);
      symbols.setGroupingSeparator(groupingSep);
      return new DecimalFormat("#,##0.###", symbols);
   }

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

   @Test
   public void rejectsNonNumericDoubleLiterals() {
      // Double.parseDouble() is more permissive than a plain decimal number: block the
      // forms that don't make sense for a physical quantity like a pixel size.
      assertThrowsParseException("NaN");
      assertThrowsParseException("Infinity");
      assertThrowsParseException("-Infinity");
      assertThrowsParseException("1d");
      assertThrowsParseException("2F");
      assertThrowsParseException("0x1p3");
   }

   @Test
   public void doubleNullInputThrowsParseExceptionNotNpe() {
      assertThrowsParseException(null);
   }

   @Test
   public void integerAcceptsValidGroupingChStyle() throws ParseException {
      // Grouping separator "." with 3 digits per group is validly grouped, and has no
      // competing decimal-fraction reading for an integer field.
      NumberFormat format = formatWithSeparators(',', '.');
      assertEquals(1300, NumberUtils.parseIntegerStrict("1.300", format).intValue());
   }

   @Test
   public void integerRejectsInvalidGroupingChStyle() {
      NumberFormat format = formatWithSeparators(',', '.');
      assertThrowsIntParseException("1.3", format);
      assertThrowsIntParseException("1.30", format);
   }

   @Test
   public void integerRejectsDecimalSeparatorChStyle() {
      // "," is the decimal separator here, which has no valid meaning in an integer.
      NumberFormat format = formatWithSeparators(',', '.');
      assertThrowsIntParseException("1,3", format);
   }

   @Test
   public void integerAcceptsValidGroupingUsStyle() throws ParseException {
      NumberFormat format = formatWithSeparators('.', ',');
      assertEquals(10000, NumberUtils.parseIntegerStrict("10,000", format).intValue());
   }

   @Test
   public void integerRejectsInvalidGroupingUsStyle() {
      NumberFormat format = formatWithSeparators('.', ',');
      assertThrowsIntParseException("1,0", format);
   }

   @Test
   public void integerRejectsDecimalSeparatorUsStyle() {
      NumberFormat format = formatWithSeparators('.', ',');
      assertThrowsIntParseException("1.3", format);
   }

   @Test
   public void integerAcceptsPlainUngroupedDigits() throws ParseException {
      // A long, ungrouped digit string (typical of e.g. a 16-bit device property
      // value) must not be rejected just because it exceeds the grouping size.
      NumberFormat format = formatWithSeparators('.', ',');
      assertEquals(65535, NumberUtils.parseIntegerStrict("65535", format).intValue());
   }

   @Test
   public void integerNullInputThrowsParseExceptionNotNpe() {
      NumberFormat format = formatWithSeparators('.', ',');
      assertThrowsIntParseException(null, format);
   }

   private static void assertThrowsParseException(String input) {
      try {
         NumberUtils.displayStringToDouble(input);
         fail("Expected a ParseException for \"" + input + "\"");
      } catch (ParseException expected) {
         // expected
      }
   }

   private static void assertThrowsIntParseException(String input, NumberFormat format) {
      try {
         NumberUtils.parseIntegerStrict(input, format);
         fail("Expected a ParseException for \"" + input + "\"");
      } catch (ParseException expected) {
         // expected
      }
   }
}
