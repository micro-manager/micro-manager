package misc;

import java.text.ParseException;
import java.util.Comparator;

public class SortFunctionObjects
{

/* comparators for GUI ordering of numeric strings
 * *
 */
// Comparators for sorting of the possible values
public static class IntStringComp implements Comparator<String>{
 @Override
 public int compare(String strA, String strB)
 {
	 int valA = 0;
	 int valB = 0;
	 try {
		valA = NumberUtils.coreStringToInt(strA);
		valB = NumberUtils.coreStringToInt(strB);
	 
	} catch (ParseException e) {
        Log.log(e);
	}
     return valA - valB;
 }
}
public static class DoubleStringComp implements Comparator<String>{
 @Override
 public int compare(String strA, String strB)
 {
	 double valA = 0.;
	 double valB = 0.;
	 try {
		valA = NumberUtils.coreStringToDouble(strA);
		valB = NumberUtils.coreStringToDouble(strB);
	 
	} catch (ParseException e) {
        Log.log(e);
	}
     return Double.compare(valA, valB);
 }
}
public static class NumericPrefixStringComp implements Comparator<String>{
	private int NumericPrefix( String str0){
		int ret = 0;
		int ii;
		for (ii= 0; ii < str0.length(); ++ii ){
			if( !Character.isDigit(str0.charAt(ii))) break;
		}
		if( 0 < ii)
			ret = Integer.valueOf(str0.substring(0,ii));
		return ret;
		
	}
   @Override
	 public int compare(String strA, String strB) {
	     return NumericPrefix(strA) - NumericPrefix(strB) ;
	 }
}

}