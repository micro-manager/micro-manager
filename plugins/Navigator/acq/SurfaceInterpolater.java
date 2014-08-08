/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import java.util.LinkedList;
import javax.vecmath.Point3d;

/**
 *
 * @author Henry
 */
public class SurfaceInterpolater {
   
   private LinkedList<Point3d> points_;
   
   
   public SurfaceInterpolater() {
      points_ = new LinkedList<Point3d>();
   }
   
   public void addPoint(double x, double y, double z) {
      points_.add(new Point3d(x,y,z));
   }
   
   public LinkedList<Point3d> getPoints() {
      return points_;
   }
   
}
