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
 * Regression tests for #2437: NumberUtils' locale-aware parsing must reject a grouping
 * separator that is not in a valid grouping position for the format's actual grouping
 * size, instead of silently discarding it the way NumberFormat's normal lenient parsing
 * does. The grouping size itself must be read from the format rather than assumed to be
 * three digits, since some formats use a different size.
 *
 * <p>These tests build explicit DecimalFormat instances for the locale configurations
 * that trigger the bug (and the ones that must keep working), rather than relying on
 * the JVM's actual default locale, so they run deterministically in any environment.
 */
public class NumberUtilsLocaleTest {

   private static NumberFormat formatWithSeparators(char decimalSep, char groupingSep) {
      DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
      symbols.setDecimalSeparator(decimalSep);
      symbols.setGroupingSeparator(groupingSep);
      return new DecimalFormat("#,##0.###", symbols);
   }

   private static NumberFormat formatWithGroupingSize(char decimalSep, char groupingSep,
         int groupingSize) {
      DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
      symbols.setDecimalSeparator(decimalSep);
      symbols.setGroupingSeparator(groupingSep);
      StringBuilder lastGroup = new StringBuilder();
      for (int i = 0; i < groupingSize; i++) {
         lastGroup.append(i == groupingSize - 1 ? '0' : '#');
      }
      return new DecimalFormat("#," + lastGroup + ".###", symbols);
   }

   @Test
   public void rejectsMistypedDecimalAsGroupingSeparator() {
      // e.g. Windows systems where the locale used for number formatting has "."
      // as the grouping separator and "," as the decimal separator: "1.30" must
      // not silently be misread as 130.
      NumberFormat format = formatWithSeparators(',', '.');
      try {
         NumberUtils.parseStrict("1.30", format);
         fail("Expected a ParseException for \"1.30\"");
      } catch (ParseException expected) {
         // expected
      }
   }

   @Test
   public void stillAcceptsValidGroupingWithDotSeparator() throws ParseException {
      NumberFormat format = formatWithSeparators(',', '.');
      assertEquals(1234.0, NumberUtils.parseStrict("1.234", format).doubleValue(), 0.0000001);
   }

   @Test
   public void plainCommaDecimalWithoutGroupingStillParses() throws ParseException {
      NumberFormat format = formatWithSeparators(',', '.');
      assertEquals(1.3, NumberUtils.parseStrict("1,3", format).doubleValue(), 0.0000001);
   }

   @Test
   public void rejectsMistypedDecimalWithCommaGrouping() {
      // The standard US-style convention: "," groups, "." is the decimal point.
      // Typing "1,30" meaning 1.30 must not silently be misread as 130.
      NumberFormat format = formatWithSeparators('.', ',');
      try {
         NumberUtils.parseStrict("1,30", format);
         fail("Expected a ParseException for \"1,30\"");
      } catch (ParseException expected) {
         // expected
      }
   }

   @Test
   public void stillAcceptsValidGroupingWithCommaSeparator() throws ParseException {
      NumberFormat format = formatWithSeparators('.', ',');
      assertEquals(1234.0, NumberUtils.parseStrict("1,234", format).doubleValue(), 0.0000001);
   }

   @Test
   public void acceptsNonThreeDigitGroupingSize() throws ParseException {
      // Not every format groups digits in threes; the valid grouping positions must
      // be derived from the format itself (DecimalFormat.getGroupingSize()) rather
      // than hard-coded, or this would wrongly reject correctly-grouped input.
      NumberFormat format = formatWithGroupingSize(',', '.', 2);
      assertEquals(1234567.0,
            NumberUtils.parseStrict("1.23.45.67", format).doubleValue(), 0.0000001);
   }

   @Test
   public void rejectsInvalidGroupingForNonThreeDigitGroupingSize() {
      // With a grouping size of 2, a lone trailing "." followed by a single digit
      // is not a valid group and must be rejected rather than silently dropped.
      NumberFormat format = formatWithGroupingSize(',', '.', 2);
      try {
         NumberUtils.parseStrict("12.3", format);
         fail("Expected a ParseException for \"12.3\"");
      } catch (ParseException expected) {
         // expected
      }
   }
}
