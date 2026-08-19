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
   private static final DecimalFormat COREDOUBLEFORMAT;
   private static final DecimalFormat COREINTEGERFORMAT;
   private static final int MAXDIGITS;

   static {
      MAXDIGITS = 4;
      // The display is supposed to use local formating (e.g., switch commas
      // with periods in Locale.GERMANY).
      FORMAT = NumberFormat.getInstance();
      FORMAT.setRoundingMode(RoundingMode.HALF_UP);
      FORMAT.setMaximumFractionDigits(MAXDIGITS);

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

   public static int displayStringToInt(Object numberString) throws ParseException {
      return parseStrict((String) numberString, FORMAT).intValue();
   }

   public static long displayStringToLong(Object numberString) throws ParseException {
      return parseStrict((String) numberString, FORMAT).longValue();
   }

   public static double displayStringToDouble(Object numberString) throws ParseException {
      return parseStrict((String) numberString, FORMAT).doubleValue();
   }

   /**
    * Parses a number using the given locale-dependent format, but rejects input that
    * NumberFormat's normal lenient parsing would silently misread.
    *
    * <p>{@code NumberFormat.parse()} discards grouping-separator characters wherever
    * they occur in the input, instead of requiring them to appear at valid grouping
    * positions (i.e. every {@link DecimalFormat#getGroupingSize()} digits, a value
    * that is usually but not necessarily 3). On systems where the locale used for
    * number formatting is not what the user expects -- e.g. Windows installations
    * with mismatched system/region settings, see issue #2437 -- this lets a mistyped
    * decimal separator be silently read as a grouping separator instead. For example,
    * if "." is the grouping separator and "," is the decimal separator, "1.30" would
    * silently parse as 130 rather than raising an error, while a validly grouped
    * "1.234" must still parse as 1234. To catch the former without breaking the
    * latter, any input that contains a grouping separator must have it in a valid
    * grouping position for the given format, or parsing fails. (Note that
    * {@code java.text.DecimalFormat} itself only ever uses a single, uniform grouping
    * size -- it does not support locale conventions with irregular grouping, such as
    * the Indian numbering system -- so deriving the size from the format covers every
    * grouping this parser could actually be asked to accept.)
    *
    * <p>Package-private (rather than taking no format argument) so that tests can
    * exercise specific, known-problematic locale configurations deterministically,
    * independent of the default locale of the JVM running the test.
    *
    * @param numberString String to be parsed
    * @param format Locale-dependent format to parse with
    * @return Parsed Number
    * @throws ParseException if the string cannot be parsed, or uses a grouping
    *     separator in an invalid position
    */
   static Number parseStrict(String numberString, NumberFormat format) throws ParseException {
      if (format instanceof DecimalFormat) {
         DecimalFormat decimalFormat = (DecimalFormat) format;
         DecimalFormatSymbols symbols = decimalFormat.getDecimalFormatSymbols();
         char groupingSep = symbols.getGroupingSeparator();
         char decimalSep = symbols.getDecimalSeparator();
         int groupingSize = decimalFormat.getGroupingSize();
         if (groupingSep != decimalSep && numberString.indexOf(groupingSep) >= 0) {
            if (!decimalFormat.isGroupingUsed() || groupingSize <= 0) {
               throw new ParseException(
                     "\"" + numberString + "\" is not a valid number", 0);
            }
            String group = Pattern.quote(String.valueOf(groupingSep));
            String decimal = Pattern.quote(String.valueOf(decimalSep));
            String validNumber = "[+-]?\\d{1," + groupingSize + "}("
                  + group + "\\d{" + groupingSize + "})*(" + decimal + "\\d+)?";
            if (!numberString.matches(validNumber)) {
               throw new ParseException(
                     "\"" + numberString + "\" is not a valid number", 0);
            }
         }
      }
      return format.parse(numberString);
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
