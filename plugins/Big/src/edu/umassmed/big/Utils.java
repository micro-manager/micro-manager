package edu.umassmed.big;


import java.util.Calendar;

public class Utils {

	public String generateDate ()
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

}
