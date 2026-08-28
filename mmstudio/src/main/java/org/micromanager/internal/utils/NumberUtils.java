///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu, March 21, 2009
//
// COPYRIGHT:    University of California, San Francisco, 2009
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
//
//

package org.micromanager.internal.utils;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Collection of functions helping to convert between core and display formats of numbers.
 */
public final class NumberUtils {
   private static final NumberFormat FORMAT;
   private static final NumberFormat INTEGER_PARSE_FORMAT;
   private static final DecimalFormat COREDOUBLEFORMAT;
   private static final DecimalFormat COREINTEGERFORMAT;
   private static final int MAXDIGITS;
   private static final Pattern DECIMAL_NUMBER_PATTERN =
         Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

   static {
      MAXDIGITS = 4;
      NumberFormat base = NumberFormat.getInstance();
      base.setRoundingMode(RoundingMode.HALF_UP);
      base.setMaximumFractionDigits(MAXDIGITS);

      // Used only by displayStringToInt/Long, to tolerantly accept a deliberately,
      // validly grouped integer (e.g. "10,000" typed into a buffer-size field). Kept
      // as a separate instance from FORMAT so the int parser's behavior does not
      // depend on how FORMAT's own grouping flag happens to be configured for output.
      INTEGER_PARSE_FORMAT = (NumberFormat) base.clone();

      // FORMAT is used for *display* output (doubleToDisplayString, intToDisplayString,
      // ...). Grouping is switched off so that a string this class formats itself never
      // contains a grouping separator: displayStringToDouble treats "." and "," purely
      // as decimal points and does not understand grouping, so a value we format
      // ourselves must always be parseable by our own parser -- with grouping left on, a
      // formatted "1.000" would not round-trip back through displayStringToDouble to
      // 1000 (see issue #2444).
      FORMAT = base;
      FORMAT.setGroupingUsed(false);

      // The core always uses four decimal places in its double strings, and a dot for
      // the decimal separator. This is equivalent to the US locale settings.
      COREDOUBLEFORMAT = (DecimalFormat) DecimalFormat.getInstance(Locale.US);
      COREDOUBLEFORMAT.setRoundingMode(RoundingMode.HALF_UP);
      COREDOUBLEFORMAT.applyPattern("0.0000");

      COREINTEGERFORMAT = (DecimalFormat) DecimalFormat.getInstance(Locale.US);
      COREINTEGERFORMAT.applyPattern("0");
   }

   // Display string methods

   public static String intToDisplayString(int number) {
      return FORMAT.format(number);
   }

   public static String longToDisplayString(long number) {
      return FORMAT.format(number);
   }

   public static String doubleToDisplayString(double number) {
      return FORMAT.format(number);
   }

   /**
    * TODO: Check if this is thread safe.
    *
    * @param number       Number to converted to a String
    * @param maxPrecision - Maximum number of digits in the resulting String
    * @return String representation of the number in given Locale
    */
   public static String doubleToDisplayString(double number, int maxPrecision) {
      FORMAT.setMaximumFractionDigits(maxPrecision);
      String result = FORMAT.format(number);
      FORMAT.setMaximumFractionDigits(MAXDIGITS);
      return result;
   }

   /**
    * Returns a NumberFormat suitable for a JFormattedTextField whose contents are read
    * back with {@link #displayStringToDouble}. Grouping is switched off: the parser
    * treats both "." and "," as decimal separators and has no notion of grouping, so a
    * grouped "2,000" would come back as 2.0 (see issue #2444). A fresh instance is
    * returned each call because a JFormattedTextField keeps a reference to it.
    *
    * @param maxFractionDigits Maximum number of digits after the decimal separator
    * @return A new, non-grouping NumberFormat in the default locale
    */
   public static NumberFormat getDisplayFormat(int maxFractionDigits) {
      NumberFormat format = NumberFormat.getNumberInstance();
      format.setRoundingMode(RoundingMode.HALF_UP);
      format.setMaximumFractionDigits(maxFractionDigits);
      format.setGroupingUsed(false);
      return format;
   }

   public static int displayStringToInt(Object numberString) throws ParseException {
      return parseIntegerStrict(numberString, INTEGER_PARSE_FORMAT).intValue();
   }

   public static long displayStringToLong(Object numberString) throws ParseException {
      return parseIntegerStrict(numberString, INTEGER_PARSE_FORMAT).longValue();
   }

   /**
    * Parses an integer typed by the user, or produced by this class's own
    * int/long-to-display-string formatting.
    *
    * <p>A decimal separator has no valid meaning in an integer field. A grouping
    * separator is accepted, but only where it forms a genuinely valid group (i.e.
    * every {@link DecimalFormat#getGroupingSize()} digits, not hard-coded to three)
    * for the given format -- so "10,000" parses as 10000, but a mistyped "1,0" or
    * "1.3" is rejected rather than silently becoming 1 or 13 (see issue #2437/#2444).
    * A stray decimal separator is rejected by the same mechanism, without a separate
    * check: it is neither a digit nor the grouping character, so it can never be part
    * of a valid match.
    *
    * <p>Unlike {@link #displayStringToDouble}, grouping support is kept here because
    * integers have no decimal-point ambiguity to confuse it with (a double like
    * "0.325" is indistinguishable from a validly grouped integer, but an int never has
    * a fractional part in the first place), and because typing a genuine thousands
    * separator into an integer field (e.g. a buffer size in MB) is a realistic,
    * intentional action -- unlike for the physical quantities displayStringToDouble is
    * used for.
    *
    * <p>Package-private (rather than taking no format argument) so tests can exercise
    * specific locale configurations deterministically, independent of the default
    * locale of the JVM running the test.
    *
    * @param numberString String to be parsed
    * @param format Locale-dependent format to derive separators from and parse with
    * @return Parsed Number
    * @throws ParseException if the string cannot be parsed as a plain or validly
    *     grouped integer
    */
   static Number parseIntegerStrict(Object numberString, NumberFormat format)
         throws ParseException {
      if (numberString == null) {
         throw new ParseException("Cannot parse null as a number", 0);
      }
      String s = ((String) numberString).trim();
      // DecimalFormat cannot parse a leading "+" in any locale, so drop it here;
      // displayStringToDouble() accepts one, and the two should agree.
      if (s.startsWith("+")) {
         s = s.substring(1);
      }
      String validNumber = "-?\\d+";
      if (format instanceof DecimalFormat) {
         DecimalFormat decimalFormat = (DecimalFormat) format;
         char groupingSep = decimalFormat.getDecimalFormatSymbols().getGroupingSeparator();
         int groupingSize = decimalFormat.getGroupingSize();
         if (groupingSize > 0) {
            String group = Pattern.quote(String.valueOf(groupingSep));
            validNumber += "|-?\\d{1," + groupingSize + "}("
                  + group + "\\d{" + groupingSize + "})+";
         }
      }
      if (!s.matches(validNumber)) {
         throw new ParseException("\"" + s + "\" is not a valid integer", 0);
      }
      return format.parse(s);
   }

   /**
    * Parses a number typed by the user, such as a pixel size or exposure time, or
    * produced by this class's own double-to-display-string formatting.
    *
    * <p>"." is always treated as the decimal separator, regardless of locale, and ","
    * is accepted as an alternate decimal separator. Locale-specific grouping
    * separators are intentionally not supported here: users essentially never type a
    * thousands-grouping separator into fields like these, whereas NumberFormat's
    * normal locale-aware parsing silently discards a grouping separator wherever it
    * occurs in the input instead of raising an error -- which previously let a
    * mistyped decimal point be misread as a completely different number (see issue
    * #2437, where e.g. "1.30" silently became 130 under a locale that uses "." for
    * grouping). Input using both "." and "," is rejected as ambiguous, as is anything
    * that is not a plain decimal number (e.g. "NaN", "Infinity", a Java type suffix
    * like "1d", or a hex float like "0x1p3" -- all of which the more permissive
    * {@link Double#parseDouble} would otherwise silently accept).
    *
    * @param numberString String to be parsed
    * @return Parsed value
    * @throws ParseException if the string cannot be parsed as a plain decimal number
    */
   public static double displayStringToDouble(Object numberString) throws ParseException {
      if (numberString == null) {
         throw new ParseException("Cannot parse null as a number", 0);
      }
      String s = ((String) numberString).trim().replace(',', '.');
      if (!DECIMAL_NUMBER_PATTERN.matcher(s).matches()) {
         throw new ParseException("\"" + numberString + "\" is not a valid number", 0);
      }
      double value = Double.parseDouble(s);
      if (!Double.isFinite(value)) {
         throw new ParseException(
               "\"" + numberString + "\" is out of range for a double", 0);
      }
      return value;
   }


   // Core string methods

   public static String intToCoreString(long number) {
      return COREINTEGERFORMAT.format(number);
   }

   public static String longToCoreString(long number) {
      return COREINTEGERFORMAT.format(number);
   }

   public static String doubleToCoreString(double number) {
      return COREDOUBLEFORMAT.format(number);
   }

   public static int coreStringToInt(Object numberString) throws ParseException {
      return COREINTEGERFORMAT.parse((String) numberString).intValue();
   }

   public static long coreStringToLong(Object numberString) throws ParseException {
      return COREINTEGERFORMAT.parse((String) numberString).longValue();
   }

   public static double coreStringToDouble(Object numberString) throws ParseException {
      return COREDOUBLEFORMAT.parse((String) numberString).doubleValue();
   }


   // Conversion between display and core strings.

   public static String doubleStringDisplayToCore(Object numberDouble) throws ParseException {
      return doubleToCoreString(displayStringToDouble(numberDouble));
   }

   public static String doubleStringCoreToDisplay(Object numberDouble) throws ParseException {
      return doubleToDisplayString(coreStringToDouble(numberDouble));
   }

   public static String intStringDisplayToCore(Object numberInt) throws ParseException {
      return intToCoreString(displayStringToInt(numberInt));
   }

   public static String intStringCoreToDisplay(Object numberInt) throws ParseException {
      return intToDisplayString(coreStringToInt(numberInt));
   }


}
