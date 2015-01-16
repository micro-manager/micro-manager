/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package surfacesandregions;

import delaunay_triangulation.Delaunay_Triangulation;
import delaunay_triangulation.Point_dt;
import delaunay_triangulation.Triangle_dt;
import java.util.LinkedList;
import org.apache.commons.math3.geometry.euclidean.threed.Line;
import org.apache.commons.math3.geometry.euclidean.threed.Plane;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.geometry.partitioning.Region;
import org.micromanager.internal.MMStudio;

/**
 *
 * @author Henry
 */
public class SurfaceInterpolatorSimple extends SurfaceInterpolator{
   

   public SurfaceInterpolatorSimple(SurfaceManager manager) {
      super(manager);
   }

   
      protected void interpolateSurface(LinkedList<Point3d> points) {
      try {

         //provide interpolator with current list of data points
         Point_dt triangulationPoints[] = new Point_dt[points.size()];        
         for (int i = 0; i < points.size(); i++) {
            triangulationPoints[i] = new Point_dt(points.get(i).x,points.get(i).y,points.get(i).z);
         }
         Delaunay_Triangulation dTri = new Delaunay_Triangulation(triangulationPoints);

         int maxPixelDimension = (int) (Math.max(boundXMax_ - boundXMin_, boundYMax_ - boundYMin_) / MMStudio.getInstance().getCore().getPixelSizeUm());
         //Start with at least 20 interp points and go smaller and smaller until every pixel interped?
         int pixelsPerInterpPoint = 1;
         while (maxPixelDimension / (pixelsPerInterpPoint + 1) > 20) {
            pixelsPerInterpPoint *= 2;
         }
         if (Thread.interrupted()) {
            throw new InterruptedException();
         }

         while (pixelsPerInterpPoint >= MIN_PIXELS_PER_INTERP_POINT) {
            int numInterpPointsX = (int) (((boundXMax_ - boundXMin_) / MMStudio.getInstance().getCore().getPixelSizeUm()) / pixelsPerInterpPoint);
            int numInterpPointsY = (int) (((boundYMax_ - boundYMin_) / MMStudio.getInstance().getCore().getPixelSizeUm()) / pixelsPerInterpPoint);
            double dx = (boundXMax_ - boundXMin_) / (numInterpPointsX - 1);
            double dy = (boundYMax_ - boundYMin_) / (numInterpPointsY - 1);
            
            Float[][] interpVals = new Float[numInterpPointsY][numInterpPointsX];
            for(int yInd = 0; yInd < interpVals.length; yInd++) {
               for (int xInd = 0; xInd < interpVals[0].length; xInd++) {
                  double xVal = boundXMin_ + dx*xInd;
                  double yVal = boundYMin_ + dy*yInd;
                  boolean inHull = convexHullRegion_.checkPoint(new Vector2D(xVal,yVal)) == Region.Location.INSIDE;
                  if (inHull) {
                     Triangle_dt tri = dTri.find(new Point_dt(xVal,yVal));
                     //convert to apache commons coordinates to make a plane
                     Vector3D v1 = new Vector3D(tri.p1().x(),tri.p1().y(),tri.p1().z());
                     Vector3D v2 = new Vector3D(tri.p2().x(),tri.p2().y(),tri.p2().z());
                     Vector3D v3 = new Vector3D(tri.p3().x(),tri.p3().y(),tri.p3().z());
                     Plane plane = new Plane(v1,v2,v3);
                     
                     Vector3D pointInPlane = (Vector3D) plane.intersection(new Line(new Vector3D(xVal,yVal,0), new Vector3D(xVal,yVal,1)));             
                     float zVal = (float) pointInPlane.getZ();     
                     interpVals[yInd][xInd] = zVal;
                  } else {
                     interpVals[yInd][xInd] = null;
                  }
               }
            }
            currentInterpolation_ = new SingleResolutionInterpolation(pixelsPerInterpPoint, interpVals, boundXMin_, boundXMax_, boundYMin_, boundYMax_, convexHullRegion_);
            System.out.println("Finished interpolating: " + pixelsPerInterpPoint);
            pixelsPerInterpPoint /= 2;
         }
      } catch (InterruptedException e) {
      }
   }
   
   
}
