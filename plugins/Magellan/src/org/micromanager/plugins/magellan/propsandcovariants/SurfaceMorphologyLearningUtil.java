package org.micromanager.plugins.magellan.propsandcovariants;

import ij3d.geom.Point3D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;
import org.apache.commons.math.linear.Array2DRowRealMatrix;
import org.apache.commons.math.linear.MatrixUtils;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.linear.RealMatrix;
import org.micromanager.plugins.magellan.acq.AcquisitionEvent;
import org.micromanager.plugins.magellan.bidc.JavaLayerImageConstructor;
import org.micromanager.plugins.magellan.coordinates.AffineUtils;
import org.micromanager.plugins.magellan.coordinates.XYStagePosition;
import org.micromanager.plugins.magellan.main.Magellan;
import org.micromanager.plugins.magellan.misc.Log;
import org.micromanager.plugins.magellan.surfacesandregions.SurfaceInterpolator;

/**
 *
 * @author henrypinkard
 */
public class SurfaceMorphologyLearningUtil {

   private static final double SEARCH_START_DIST = 400.0;
   private static final double SEARCH_TOLERANCE = 2.0;
   private static final int N_SAMPLING_ANGLES = 8;
   private static final double PHI = 40.0 / 360.0 * Math.PI * 2.0;
   private static final int FOV_LASER_MODULATION_RESOLUTION = 16;

     
   
   
   
   public static byte[] getExcitations(AcquisitionEvent e, SurfaceInterpolator surf) throws InterruptedException {
      XYStagePosition xyPos = e.xyPosition_;
      double zPos = e.zPosition_;
      Point2D.Double[] corners = xyPos.getFullTileCorners();
      double tileSize = Math.abs(corners[2].x - corners[0].x);      
      int pixelDim = JavaLayerImageConstructor.getInstance().getImageHeight();
      
      double desiredBrightness = 0.5;
      AffineTransform posTransform = AffineUtils.getAffineTransform(getCurrentPixelSizeConfig(), xyPos.getCenter().x, xyPos.getCenter().y);

      
      double[][] designMat = new double[FOV_LASER_MODULATION_RESOLUTION*FOV_LASER_MODULATION_RESOLUTION][12];
//      designMatrix = [designMatrix tilePosition brightness];
       for (int r = 0; r < designMat.length; r++) {
         for (int c = 0; c < designMat[0].length; c++) {
            if (c < 8) {
               //ixel posiitons relative to center
               int xPosPix = (int) (((r % FOV_LASER_MODULATION_RESOLUTION) / (double) (FOV_LASER_MODULATION_RESOLUTION-1) - 0.5) * pixelDim);
               int yPosPix = (int) (((r / FOV_LASER_MODULATION_RESOLUTION) / (double) (FOV_LASER_MODULATION_RESOLUTION-1) - 0.5) * pixelDim);               
               Point2D.Double stageCoordPos =  new Point2D.Double();
               posTransform.transform(new Point2D.Double(xPosPix, yPosPix), stageCoordPos);
               double val = getSampledDistancesToSurface(c, stageCoordPos.x, stageCoordPos.y, zPos, surf);
               designMat[r][c] = (val - 150.0) / 100.0;
            } else if (c < 9) {     
               if(!surf.waitForCurentInterpolation().isInterpDefined(xyPos.getCenter().x, xyPos.getCenter().y))  {
                  designMat[r][c] = 0;
               } else {
                  double zInterp = surf.waitForCurentInterpolation().getInterpolatedValue(xyPos.getCenter().x, xyPos.getCenter().y);
                  designMat[r][c] = (zPos - zInterp - 150.0) / 100.0;
               }
            } else if (c < 10) { // x position                        
               double xPos = (r % FOV_LASER_MODULATION_RESOLUTION) / (double) (FOV_LASER_MODULATION_RESOLUTION-1);
               designMat[r][c] = xPos;
            } else if (c < 11) { // y position                            
               double yPos = (r / FOV_LASER_MODULATION_RESOLUTION) / (double) (FOV_LASER_MODULATION_RESOLUTION-1);
               designMat[r][c] = yPos;
            } else {
               designMat[r][c] = desiredBrightness;
            }
         }
       }
       //use NN to predict
       return LaserPredNet.singleton_.forwardPass(designMat);
   }

   /**
    *
    * @return a vector of distances from the point to the surface sampled
    * equally around a circle
    */
   private static double getSampledDistancesToSurface(int angleIndex, double x, double y, double z, SurfaceInterpolator surface) throws InterruptedException {
      double dTheta = Math.PI * 2.0 / (double) N_SAMPLING_ANGLES;
      Vector3D initialPoint = new Vector3D(x, y, z);
      double[] distances = new double[N_SAMPLING_ANGLES];
      double theta = angleIndex * dTheta;
      //calculate unit vector in theta phi direction
      Vector3D directionUnitVec = new Vector3D(Math.cos(theta) * Math.sin(PHI), Math.sin(theta) * Math.sin(PHI), Math.cos(PHI)).scalarMultiply(-1);
      //binary search 
      double initialDist = SEARCH_START_DIST;
      //start with a point outside and then binary line search for the distance
      while (isWithinSurace(surface, initialPoint.add(directionUnitVec.scalarMultiply(initialDist)))) {
         initialDist *= 2;
      }
      return binarySearch(initialPoint, directionUnitVec, 0, initialDist, surface);
   }
   
   private static boolean isWithinSurace(SurfaceInterpolator surface, Vector3D point) throws InterruptedException {
     boolean defined = surface.waitForCurentInterpolation().isInterpDefined(point.getX(), point.getY());
     if (!defined) {
        return false;
     }
     float interpVal = surface.waitForCurentInterpolation().getInterpolatedValue(point.getX(), point.getY());
     return point.getZ() > interpVal;
   }
   
   private static double binarySearch(Vector3D initialPoint, Vector3D direction, double minDistance, double maxDistance, SurfaceInterpolator surf) throws InterruptedException {      
      double halfDistance = (minDistance + maxDistance) / 2;
      //if the distance has been narrowed to a sufficiently small interval, return
      if (maxDistance - minDistance < SEARCH_TOLERANCE) {
         return halfDistance;
      }
      //check if point is above surface in 
      Vector3D searchPoint = initialPoint.add(direction.scalarMultiply(halfDistance));
      boolean withinSurface = isWithinSurace(surf, searchPoint);
      if (!withinSurface) {
         return binarySearch(initialPoint, direction, minDistance, halfDistance, surf);
      } else {
         return binarySearch(initialPoint, direction, halfDistance, maxDistance, surf);
      }
   }
   
   
    private static String getCurrentPixelSizeConfig() {   
      try {
         return Magellan.getCore().getCurrentPixelSizeConfig();
      } catch (Exception ex) {
         Log.log("couldnt get pixel size config");
         throw new RuntimeException();
      }
   }
   
}
