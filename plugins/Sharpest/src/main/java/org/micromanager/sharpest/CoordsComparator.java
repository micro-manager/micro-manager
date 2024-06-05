
package org.micromanager.sharpest;

import java.util.Comparator;
import java.util.HashMap;
import org.micromanager.data.Coords;

/**
 * Helper class to order Coords.
 *
 * @author Nico
 */
public class CoordsComparator implements Comparator<String> {
   HashMap<String, Integer> cAxesNumeric = new HashMap<String, Integer>();


   /**
    * Constructor of the comparator, sets the order in which we want the axes ordered.
    */
   public CoordsComparator() {
      cAxesNumeric.put(Coords.T, 1);
      cAxesNumeric.put(Coords.TIME_POINT, 1);
      cAxesNumeric.put(Coords.C, 3);
      cAxesNumeric.put(Coords.CHANNEL, 3);
      cAxesNumeric.put(Coords.Z, 2);
      cAxesNumeric.put(Coords.Z_SLICE, 2);
      cAxesNumeric.put(Coords.P, 4);
      cAxesNumeric.put(Coords.STAGE_POSITION, 4);
   }
   
   /**
    * Desired order.
    * C, Z, T, P, other
    *
    * @param s1 First axis to compare
    * @param s2 Second axis.
    * @return 0 if equal, 1 if s1 > s2
    */
   @Override
   public int compare(String s1, String s2) {
      if (s1.equals(s2)) {
         return 0;
      }
      if (cAxesNumeric.keySet().contains(s1) && cAxesNumeric.keySet().contains(s2)) {
         int val1 = cAxesNumeric.get(s1);
         int val2 = cAxesNumeric.get(s2);
         return val1 > val2 ? 1 : -1;
      } else if (cAxesNumeric.keySet().contains(s1)) {
         return -1;
      } else if (cAxesNumeric.keySet().contains(s2)) {
         return 1;
      } 
      return s1.compareTo(s2);

   }
   
}
