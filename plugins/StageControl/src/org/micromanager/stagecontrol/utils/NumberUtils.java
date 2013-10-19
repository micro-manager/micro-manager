/*
 * Class emulating Micro-Manager's ReportingUtils
 * Goal is to avoid dependencies on MMJ_.jar
 * 
 */
package org.micromanager.stagecontrol.utils;

import java.text.NumberFormat;
import java.text.ParseException;

/**
 *
 * 
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
   
   public static String doubleToDisplayString(double number) {
		return format_.format(number);
	}

}
