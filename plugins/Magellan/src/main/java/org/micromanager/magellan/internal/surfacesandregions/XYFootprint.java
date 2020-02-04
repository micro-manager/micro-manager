///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.magellan.internal.surfacesandregions;

import org.micromanager.magellan.internal.coordinates.XYStagePosition;
import java.util.ArrayList;
import java.util.List;
import org.micromanager.MultiStagePosition;
import org.micromanager.StagePosition;
import org.micromanager.magellan.internal.main.Magellan;

/**
 * Superclass for Surfaces and Grids
 */

public abstract class XYFootprint {

   protected String name_;
   protected final String xyString_;
   protected SurfaceGridManager manager_ = SurfaceGridManager.getInstance();
   
   public XYFootprint(String xyDevice) {
      xyString_ = xyDevice;
   }
   
   public void exportToMicroManager() {
      List<XYStagePosition> list = getXYPositionsNoUpdate();
      for (XYStagePosition xy : list) {
         MultiStagePosition mPos = new MultiStagePosition();
         mPos.setLabel(name_ + "-" + xy.getName());
         StagePosition pos = new StagePosition();
         mPos.add(pos);
         pos.set2DPosition(xy.getXYDevice(), xy.getCenter().x, xy.getCenter().y);
         Magellan.getStudio().positions().getPositionList().addPosition(mPos);
      }
   }
   
   /**
    * @param tileOverlapPercent
    * @return read only list of XY positions after updating them to reflect potential changes in overlap
    */
    public abstract List<XYStagePosition> getXYPositions(double tileOverlapPercent) throws InterruptedException;

    public abstract List<XYStagePosition> getXYPositionsNoUpdate();
    
    /**
     * @return true if there is any intersection between footprint and position
     */
    public abstract boolean isDefinedAtPosition(XYStagePosition position); 
    
    public String getXYDevice() {
       return xyString_;
    }
    
   public String toString() {
      return name_;
   }

   public String getName() {
      return name_;
   }

   public void rename(String newName) {
      name_ = newName;
      manager_.surfaceOrGridRenamed(this);
   }

}
