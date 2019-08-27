
package org.micromanager.display.internal.displaywindow.missingstrategies;

import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;

/**
 *
 * @author nico
 */
public class MissingImageStrategy {
   
   /**
    *
    * @param dp
    * @param coords
    * @param acquisitionOngoing
    * @return
    */
   public static Coords getImagesToBeDisplayed(DataProvider dp, Coords coords, boolean acquisitionOngoing) {
      return coords;
   }
   
}
