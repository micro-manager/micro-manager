package de.embl.rieslab.emu.utils;

import java.awt.Color;
import java.util.regex.Pattern;

public class EmuUtils {


   public static boolean isNumeric(String s) {
      if (s != null) {
         if (s.matches("[-+]?\\d*\\.?\\d+")) {
            return true;
         } else {
            return s.matches("[-+]?\\d*\\,?\\d+");
         }
      }
      return false;
   }

   public static boolean isInteger(String str) {
      if (str == null) {
         return false;
      }
      int length = str.length();
      if (length == 0) {
         return false;
      }
      int i = 0;
      if (str.charAt(0) == '-' || str.charAt(0) == '+') {
         if (length == 1) {
            return false;
         }
         i = 1;
      }
      for (; i < length; i++) {
         char c = str.charAt(i);
         if (c < '0' || c > '9') {
            return false;
         }
      }
      return true;
   }

   /*
    * From https://docs.oracle.com/javase/8/docs/api/java/lang/Double.html
    */
   public static boolean isFloat(String val) {
      if (val == null) {
         return false;
      }

      final String Digits = "(\\p{Digit}+)";
      final String HexDigits = "(\\p{XDigit}+)";
      // an exponent is 'e' or 'E' followed by an optionally
      // signed decimal integer.
      final String Exp = "[eE][+-]?" + Digits;
      final String fpRegex =
            ("[\\x00-\\x20]*"         // Optional leading "whitespace"
                  + "[+-]?("          // Optional sign character
                  + "NaN|"            // "NaN" string
                  + "Infinity|"       // "Infinity" string

                  // A decimal floating-point string representing a finite positive
                  // number without a leading sign has at most five basic pieces:
                  // Digits . Digits ExponentPart FloatTypeSuffix
                  //
                  // Since this method allows integer-only strings as input
                  // in addition to strings of floating-point literals, the
                  // two sub-patterns below are simplifications of the grammar
                  // productions from the Java Language Specification, 2nd
                  // edition, section 3.10.2.

                  // Digits ._opt Digits_opt ExponentPart_opt FloatTypeSuffix_opt
                  + "(((" + Digits + "(\\.)?(" + Digits + "?)(" + Exp + ")?)|"

                  // . Digits ExponentPart_opt FloatTypeSuffix_opt
                  + "(\\.(" + Digits + ")(" + Exp + ")?)|"

                  // Hexadecimal strings
                  + "(("
                  // 0[xX] HexDigits ._opt BinaryExponent FloatTypeSuffix_opt
                  + "(0[xX]" + HexDigits + "(\\.)?)|"

                  // 0[xX] HexDigits_opt . HexDigits BinaryExponent FloatTypeSuffix_opt
                  + "(0[xX]" + HexDigits + "?(\\.)" + HexDigits + ")"

                  + ")[pP][+-]?" + Digits + "))"
                  + "[fFdD]?))"
                  + "[\\x00-\\x20]*"); // Optional trailing "whitespace"

      return Pattern.matches(fpRegex, val);
   }

   public static double round(double value, int precision) {
      int scale = (int) Math.pow(10, precision);
      return (double) Math.round(value * scale) / scale;
   }

   public static Color getColorFromStringArray(String[] c) {
      if (c.length == 3) {
         if (isInteger(c[0]) && isInteger(c[1]) && isInteger(c[2])) {
            return new Color(Integer.parseInt(c[0]), Integer.parseInt(c[1]),
                  Integer.parseInt(c[2]));
         }
      }
      return null;
   }

   public static boolean isBool(String s) {
      return s != null && (s.equals("true") || s.equals("false"));
   }

   public static boolean convertStringToBool(String s) {
      return s.equals("true");
   }

   public static int roundToInt(double d) {
      if (d < 0) {
         return (int) (d - 0.5);
      }
      return (int) (d + 0.5);
   }
}
