
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
   private List<Point2D_I32> bleachMask_;
   private List<Point2D_I32> maskIncludingBleach_;
   private Point2D_I32 centroid_;
   private Point2D_I32 bleachSpot_;
   
   /**
    * Applies the offset to each point in the list
    * 
    * @param mask Input list of points
    * @param offset Offset to be applied
    * @param override Returns the input list with the offset applied to each
    *                   element when true, otherwise create a new list
    * 
    * @return List with input points offset by the given values
    */
   public static List<Point2D_I32> offset(List<Point2D_I32> mask, 
           final Point2D_I32 offset, final boolean override) {
      List<Point2D_I32> output;
      if (override) {
         output = mask;
         for (Point2D_I32 p : mask) {
            p.set(p.x + offset.x, p.y + offset.y);
         }
      } else {
         output = new ArrayList<>();
         for (Point2D_I32 p : mask) {
            output.add(new Point2D_I32(p.x + offset.x, p.y + offset.y));
         }
      }
      return output;
   }
   
   public ParticleData(List<Point2D_I32> mask) {
      mask_ = mask;
   }
   
   public ParticleData(List<Point2D_I32> mask, Point2D_I32 offset) {
      mask_ = offset(mask, offset, true);
   }
   
   public ParticleData(List<Point2D_I32> mask, List<Point2D_I32> bleachMask,
           List<Point2D_I32> maskIncludingBleach, Point2D_I32 centroid,
           Point2D_I32 bleachSpot) {
      mask_ = mask;
      bleachMask_ = bleachMask;
      maskIncludingBleach_ = maskIncludingBleach;
      centroid_ = centroid;
      bleachSpot_ = bleachSpot;
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
   
   public List<Point2D_I32> getBleachMask() {
      return bleachMask_;
   }
   
   public List<Point2D_I32> getMaskIncludingBleach() {
      return maskIncludingBleach_;
   }
   
   
}
