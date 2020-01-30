/*
 * 
 * Karl Bellve
 * Biomedical Imaging Group
 * University of Massachusetts Medical School
 * Karl.Bellve@umassmed.edu
 * http://big.umassmed.edu/
 *
 */

package edu.umassmed.big;


import java.util.Calendar;

public class Utils {

	public static String generateDate ()
	{
		String Date;
		
		Calendar today = Calendar.getInstance();
		if (today.get(Calendar.MONTH) < 10)
		{
			if (today.get(Calendar.DAY_OF_MONTH) < 10) Date = "" + today.get(Calendar.YEAR) + "0" + (today.get(Calendar.MONTH) + 1) + "0" + today.get(Calendar.DAY_OF_MONTH);
			else Date = "" + today.get(Calendar.YEAR) + "0" + (today.get(Calendar.MONTH) + 1) + today.get(Calendar.DAY_OF_MONTH);
		}
		else 
			if (today.get(Calendar.DAY_OF_MONTH) < 10) Date = "" + today.get(Calendar.YEAR) + (today.get(Calendar.MONTH) + 1) + "0" + today.get(Calendar.DAY_OF_MONTH);
			else Date = "" + today.get(Calendar.YEAR) +  (today.get(Calendar.MONTH) + 1) + today.get(Calendar.DAY_OF_MONTH);
	
		return Date;
	}

	// get only a piece of the array, use only for 2D, since I think 3D sizes run into memory issues
	public static short[] getarray(short[] array, int offset, int length) 
	{
		short[] result;
	    result = new short[length];
	    System.arraycopy(array, offset, result, 0, length);
	    return result;
		   
	}
}
