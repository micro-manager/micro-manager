
package org.micromanager.pointandshootanalysis.data;

import georegression.struct.point.Point2D_I32;
import java.util.ArrayList;
import java.util.List;
import org.micromanager.pointandshootanalysis.algorithm.ContourStats;

/**
 * Stores a binary mask for a particle
 * 
 * @author nico
 */
public class ParticleData {
   private final List<Point2D_I32> mask_;
   private Point2D_I32 centroid_;
   private Point2D_I32 bleachSpot_;
   
   public ParticleData(List<Point2D_I32> mask) {
      mask_ = mask;
   }
   
   public ParticleData(List<Point2D_I32> mask, Point2D_I32 offset) {
      mask_ = new ArrayList<>(mask.size());
      mask.forEach( (p) -> {
         mask_.add(new Point2D_I32(p.x + offset.x, p.y + offset.y));
      });
   }
   
   public Point2D_I32 getCentroid() {
      if (centroid_ == null) {
         centroid_ = ContourStats.centroid(mask_);
      }
      return centroid_;
   }
   
   public void setBleachSpot(Point2D_I32 bleachSpot) {
      bleachSpot_ = bleachSpot;
   }
   
   public Point2D_I32 getBleachSpot() {
      return bleachSpot_;
   }
   
   public List<Point2D_I32> getMask() {
      return mask_;
   }
   
   
}
