
package org.micromanager.assembledata;

import java.util.Iterator;
import java.util.Set;
import org.micromanager.assembledata.exceptions.MalFormedFileNameException;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.display.DataViewer;

/**
 *
 * @author Nico
 */
public class Utils {
   
   public static double getSmallestZoom(DataViewer dv1, DataViewer dv2) {
      double zoom = dv1.getDisplaySettings().getZoomRatio();
      if (dv2.getDisplaySettings().getZoomRatio() < zoom) { 
         zoom = dv2.getDisplaySettings().getZoomRatio(); 
      }
      return zoom;
   }
   
   public static DataProvider singlePositionData(DataProvider dp1, DataProvider dp2) {
      if (dp1.getNextIndex(Coords.P) <= 1 && dp2.getNextIndex(Coords.P) > 1) {
         return dp1;
      } else if (dp2.getNextIndex(Coords.P) <= 1 && dp1.getNextIndex(Coords.P) > 1) {
         return dp2;
      }
      return null;
   }
   
   public static DataProvider multiPositionData(DataProvider dp1, DataProvider dp2) {
      if (dp1.getNextIndex(Coords.P) <= 1 && dp2.getNextIndex(Coords.P) > 1) {
         return dp2;
      } else if (dp2.getNextIndex(Coords.P) <= 1 && dp1.getNextIndex(Coords.P) > 1) {
         return dp1;
      }
      return null;
   }
   
   public static String findRoot(String dirName, Set<String> roots) {
      Iterator<String> it = roots.iterator();   
      while (it.hasNext()) {
         String root = it.next();
         if (root.equals(dirName.substring(0, root.length()))) {
            return root;
         }
      }
      return "";
   }
   
   /**
    * Find the entry in the Set ( of size 2) that is not the given String
    * @param root
    * @param roots
    * @return 
    */
   public static String findOtherRoot(String root, Set<String> roots) {
      Iterator<String> it = roots.iterator();   
      while (it.hasNext()) {
         String test = it.next();
         if (!root.equals(test)) {
            return test;
         }
      }
      return "";
   }
   
   /**
    * Filenames are expected to be formatted as"
    * TIRF-B2-Site_0-7
    * Where the last number is the sequence in the total acquistion series,
    * the number before the site number in the well
    * the letter/number before Site is the well indicator
    * and the text at the beginning the modality (here: TIRF or Confocal, but 
    * can be more or less anything without an underscore
    * 
    * There should be an underscore behind "Site", and dashes separating the other parts
    * 
    * @param input - input file name
    * @param root - Text at the beginning (Tirf or confocal in this case)
    * @param well - Well indicator (from HCS: letter plus number)
    * @param site - Site number
    * @param sequence - sequence number over the complete acquisition
    * @throws MalFormedFileNameException 
    */
   public static void extractFileNameInfo(String input, 
           String root, 
           String well, 
           Integer site,
           Integer sequence) throws MalFormedFileNameException {
      String[] lSplit = input.split("_");
      if (lSplit.length != 2) {
         throw new MalFormedFileNameException(
                 "The DataSetNames must contain exactly one underscore (_)");
      }
      int lI = lSplit[0].lastIndexOf("-");
      int lI2 = lSplit[0].substring(0, lI).lastIndexOf("-");
      root = lSplit[0].substring(0, lI2);
      well = lSplit[0].substring(lI2 + 1, lI);
      String[] numbers = lSplit[1].split("-");
      if (numbers.length != 2) {
         throw new MalFormedFileNameException(
                 "The DataSetNames must contain 2 numbers sepearated by a dash behind the underscore");         
      }
      site = Integer.parseInt(numbers[0]);
      sequence = Integer.parseInt(numbers[1]);
   }
}
