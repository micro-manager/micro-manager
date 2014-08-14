/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import edu.mines.jtk.dsp.Sampling;
import edu.mines.jtk.interp.BlendedGridder2;
import edu.mines.jtk.interp.DiscreteSibsonGridder2;
import edu.mines.jtk.interp.Gridder2;
import edu.mines.jtk.interp.NearestGridder2;
import edu.mines.jtk.interp.RadialGridder2;
import edu.mines.jtk.interp.RadialInterpolator2;
import edu.mines.jtk.interp.SibsonGridder2;
import edu.mines.jtk.interp.SplinesGridder2;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import javax.vecmath.Point3d;
import org.apache.commons.math3.exception.ConvergenceException;
import org.apache.commons.math3.exception.NullArgumentException;
import org.apache.commons.math3.geometry.euclidean.twod.Euclidean2D;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.geometry.euclidean.twod.hull.ConvexHull2D;
import org.apache.commons.math3.geometry.euclidean.twod.hull.ConvexHullGenerator2D;
import org.apache.commons.math3.geometry.euclidean.twod.hull.MonotoneChain;
import org.apache.commons.math3.geometry.partitioning.Region;

/**
 *
 * @author Henry
 */
public class SurfaceInterpolater {
   
   private LinkedList<Point3d> points_;
   private Gridder2 gridder_;
   private boolean interpFlipX_, interpFlipY_;
   private MonotoneChain mChain_;
   private Vector2D[] convexHullVertices_;
   private Region<Euclidean2D> convexHullRegion_;
   private LinkedList<Vector2D> xyPoints_;
   
   public SurfaceInterpolater() {
      points_ = new LinkedList<Point3d>();
      mChain_ = new MonotoneChain(true);  
      xyPoints_ = new LinkedList<Vector2D>();
      
//      Other possible interp methods:
//      RadialGridder2 //Good interp, but time scales with number of points
//      NearestGridder2 //looks good and fast, but discrete nature might lead to problems for interp between slices
//      BlendedGridder2 //Similar to natural neighbor since euclidean distances are used
//      SibsonGridder2 //slowwwww
      
      //useful for debugging
//           gridder_ = new NearestGridder2(new float[0],new float[0],new float[0]); //fast, like nearest neighbor, complexity decreases with # samples
      
      //Biggest difference between these two appears to be that Splines can come up with z values outside the range of smaple points
      //   not sure if this is good or bad yet
//     gridder_ = new DiscreteSibsonGridder2(new float[0],new float[0],new float[0]); //fast, like nearest neighbor, complexity decreases with # samples
      gridder_ = new SplinesGridder2(new float[0], new float[0], new float[0]);  //Fast and appears good

   }
   
   public Vector2D[] getConvexHullPoints() {
      return convexHullVertices_;
   }
   
   private boolean isInsideConvexHull(double x, double y) {
      if (convexHullRegion_ == null) {
         return false;
      }
      return convexHullRegion_.checkPoint(new Vector2D(x,y)) != Region.Location.OUTSIDE;
   }
   
   public void addPoint(double x, double y, double z) {
      points_.add(new Point3d(x,y,z)); //for interpolation
      xyPoints_.add(new Vector2D(x,y)); //for convex hull
      //calc convex hull 
      if (points_.size() > 2) {
         ConvexHull2D hull = mChain_.generate(xyPoints_);
         convexHullRegion_ = hull.createRegion();
         convexHullVertices_ = hull.getVertices();
      }
      setInterpolatorData();
   }
   
   public LinkedList<Point3d> getPoints() {
      return points_;
   }
   
   public boolean getFlipX() {
      return interpFlipX_;
   }
   
   public boolean getFlipY() {
      return interpFlipY_;
   }

   /**
    * Since this function flips start and end as needed, getFlipX and getFlipY
    * should be called right after to determine the orientation of output data
    * @param xStart - stage coordinate
    * @param yStart - stage coordinate
    * @param xEnd - stage coordinate
    * @param yEnd - stage coordinate
    * @param numSamplesX
    * @param numSamplesY
    * @return Object[] with 0 as a float[][] of interpolated values and 1 as a boolean[][] indicating
    * whether those values are in the convex hull
    */
   public Object[] getInterpolatedValues(double xStart, double yStart, double xEnd, double yEnd, int numSamplesX, int numSamplesY) {
      //check if theres anyhting to interpolate
      if (points_.size() == 0) {
         return null;
      }
      interpFlipX_ = xEnd < xStart;
      interpFlipY_ = yEnd < yStart;
      
      Sampling xSampling = new Sampling(numSamplesX, Math.abs(xStart-xEnd) / (numSamplesX - 1), Math.min(xStart, xEnd));
      Sampling ySampling = new Sampling(numSamplesY, Math.abs(yStart-yEnd) / (numSamplesY - 1), Math.min(yStart, yEnd));
      float[][] interp =  gridder_.grid(xSampling, ySampling);

      
      //check if interpolated values are actually inside convex hull
      boolean[][] inside = new boolean[numSamplesY][numSamplesX];
      for (boolean[] row : inside) {
         Arrays.fill(row, false);
      }
      if (points_.size() >= 3) {
         double[] xVals = xSampling.getValues();
         double[] yVals = ySampling.getValues();
         for (int xInd = 0; xInd < xVals.length; xInd++) {
            for (int yInd = 0; yInd < yVals.length; yInd++) {
               inside[yInd][xInd] = isInsideConvexHull(xVals[xInd], yVals[yInd]);
            }
         }
      }
      return new Object[]{interp,inside};
   }
   
   private void setInterpolatorData() {
      float x[] = new float[points_.size()];
      float y[] = new float[points_.size()];
      float z[] = new float[points_.size()];
      for (int i = 0; i < points_.size(); i++ ) {
         x[i] = (float) points_.get(i).x;
         y[i] = (float) points_.get(i).y;
         z[i] = (float) points_.get(i).z;
      }     
      gridder_.setScattered(z, x, y);
   }
   
   
}
