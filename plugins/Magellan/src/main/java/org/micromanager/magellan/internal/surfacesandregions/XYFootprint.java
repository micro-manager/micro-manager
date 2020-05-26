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

import java.awt.geom.Point2D;
import org.micromanager.acqj.api.xystage.XYStagePosition;

import java.util.List;
import org.apache.commons.math3.geometry.euclidean.twod.Euclidean2D;
import org.apache.commons.math3.geometry.euclidean.twod.PolygonsSet;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.geometry.partitioning.Region;
import org.apache.commons.math3.geometry.partitioning.RegionFactory;
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
   protected final RegionFactory<Euclidean2D> regionFactory_ = new RegionFactory<Euclidean2D>();

   
   public XYFootprint(String xyDevice) {
      xyString_ = xyDevice;
   }
   
   public void exportToMicroManager() {
      List<XYStagePosition> list = getXYPositions();
      for (XYStagePosition xy : list) {
         MultiStagePosition mPos = new MultiStagePosition();
         mPos.setLabel(name_ + "-" + xy.getName());
         StagePosition pos = new StagePosition();
         mPos.add(pos);
         pos.set2DPosition(Magellan.getCore().getXYStageDevice(), xy.getCenter().x, xy.getCenter().y);
         Magellan.getStudio().positions().getPositionList().addPosition(mPos);
      }
   }

    public abstract List<XYStagePosition> getXYPositions();
    
    /**
     * @return true if there is any intersection between footprint and position
     */
    public abstract boolean isDefinedAtPosition(Point2D.Double[] posCorners); 
    
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
   
      /**
    * Create a 2D square region corresponding to the the stage position + any
    * extra padding
    *
    * @param pos
    * @return
    */
   protected Region<Euclidean2D> getStagePositionRegion(Point2D.Double[] dispPositionCorners) {
      Region<Euclidean2D> square;
      square = new PolygonsSet(0.0001, new Vector2D[]{
         new Vector2D(dispPositionCorners[0].x, dispPositionCorners[0].y),
         new Vector2D(dispPositionCorners[1].x, dispPositionCorners[1].y),
         new Vector2D(dispPositionCorners[2].x, dispPositionCorners[2].y),
         new Vector2D(dispPositionCorners[3].x, dispPositionCorners[3].y)});
      
      double centerX = (dispPositionCorners[0].x + dispPositionCorners[1].x 
                        + dispPositionCorners[2].x + dispPositionCorners[3].x) / 4;
      double centerY = (dispPositionCorners[0].y + dispPositionCorners[1].y 
                        + dispPositionCorners[2].y + dispPositionCorners[3].y) / 4;

      return square.checkPoint(new Vector2D(centerX, centerY))
              == Region.Location.OUTSIDE ? regionFactory_.getComplement(square) : square;
   }

}
