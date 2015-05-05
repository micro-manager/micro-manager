/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package surfacesandregions;

import coordinates.XYStagePosition;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Henry
 */
public interface XYFootprint {
   
   /**
    * @return read only list of XY positions
    */
    public List<XYStagePosition> getXYPositions(double tileOverlapPercent) throws InterruptedException;

   public String getXYDevice();
    
}
