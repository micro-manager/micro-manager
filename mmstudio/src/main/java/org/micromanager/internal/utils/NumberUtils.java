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
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

public final class NumberUtils {
   private static final NumberFormat FORMAT;
   private static final DecimalFormat COREDOUBLEFORMAT;
   private static final DecimalFormat COREINTEGERFORMAT;
   private static final int MAXDIGITS;

   static {
      MAXDIGITS = 4;
      // The display is supposed to use local formating (e.g., switch commas with periods in Locale.GERMANY).
      FORMAT = NumberFormat.getInstance();
      FORMAT.setRoundingMode(RoundingMode.HALF_UP);
      FORMAT.setMaximumFractionDigits(MAXDIGITS);

      // The core always uses four decimal places in its double strings, and a dot for the decimal separator.
      // This is equivalent to the US locale settings.
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
    * TODO: Check if this is thread safe
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
      return FORMAT.parse((String) numberString).intValue();
   }

   public static long displayStringToLong(Object numberString) throws ParseException {
      return FORMAT.parse((String) numberString).longValue();
   }

   public static double displayStringToDouble(Object numberString) throws ParseException {
      return FORMAT.parse((String) numberString).doubleValue();
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
