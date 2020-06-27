/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.channelcorrector.utils;

import java.text.NumberFormat;
import java.text.ParseException;

/**
 *
 * @author nico
 */
public class NumberUtils {
   
   private static final NumberFormat format_;
   
   static {
		// The display is supposed to use local formating (e.g., switch commas with periods in Locale.GERMANY).
		format_ = NumberFormat.getInstance();
      format_.setMaximumFractionDigits(4);
   }
   
   public static double displayStringToDouble(Object numberString) throws ParseException {
		return format_.parse((String) numberString).doubleValue();
	}
   
   public static int displayStringToInt(Object numberString) throws ParseException {
		return format_.parse((String) numberString).intValue();      
	}
   
   public static String doubleToDisplayString(double number) {
		return format_.format(number);
	}


}
