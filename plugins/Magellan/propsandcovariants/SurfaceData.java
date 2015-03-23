/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package propsandcovariants;

import acq.AcquisitionEvent;
import coordinates.AffineUtils;
import coordinates.XYStagePosition;
import ij.IJ;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.micromanager.utils.ReportingUtils;
import surfacesandregions.SingleResolutionInterpolation;
import surfacesandregions.SurfaceInterpolator;

/**
 * Category about interpolated surface (e.g. distance below surface) to be used in
 * covaried settings
 */
public class SurfaceData implements Covariant {

   //all data must start with this prefix so they can be reconstructed when read from a text file on disk
   public static String PREFIX = "Surface data: ";
   //number of test points per dimension for finding minimum distance to surface
   private static final int NUM_XY_TEST_POINTS = 9;
   
   public static String  DISTANCE_BELOW_SURFACE_CENTER = "--Vertical distance below at XY position center";
   public static String  DISTANCE_BELOW_SURFACE_MINIMUM = "--Minimum vertical distance below at XY position";
   public static String  DISTANCE_BELOW_SURFACE_MAXIMUM = "--Maximum vertical distance below at XY position";
   
   private String category_;
   private SurfaceInterpolator surface_;
   
   public SurfaceData(SurfaceInterpolator surface, String type) throws Exception {
      category_ = type;
      surface_ = surface;
      if (!Arrays.asList(enumerateDataTypes()).contains(type)) {
         //not a recognized type
         throw new Exception();
      }
   }
   
   public static String[] enumerateDataTypes() {
      return new String[] {DISTANCE_BELOW_SURFACE_CENTER, DISTANCE_BELOW_SURFACE_MINIMUM, DISTANCE_BELOW_SURFACE_MAXIMUM};
   }
   
   @Override
   public String toString() {
      return getName();
   }
   
   
   @Override
   public String getAbbreviatedName() {
       if (category_.equals(DISTANCE_BELOW_SURFACE_CENTER)) {
         return "Distance below "+ surface_.getName();
      } else if (category_.equals(DISTANCE_BELOW_SURFACE_MINIMUM)) {
         return  "Min distance below "+ surface_.getName();
      } else if (category_.equals(DISTANCE_BELOW_SURFACE_MAXIMUM)) {
         return "Max distance below "+ surface_.getName();
      } else {
         ReportingUtils.showError("Unknown Surface data type");
         throw new RuntimeException();
      }
   }

   @Override
   public String getName() {
      if (category_.equals(DISTANCE_BELOW_SURFACE_CENTER)) {
         return PREFIX + surface_.getName() + DISTANCE_BELOW_SURFACE_CENTER;
      } else if (category_.equals(DISTANCE_BELOW_SURFACE_MINIMUM)) {
         return PREFIX + surface_.getName() + DISTANCE_BELOW_SURFACE_MINIMUM;
      } else if (category_.equals(DISTANCE_BELOW_SURFACE_MAXIMUM)) {
         return PREFIX + surface_.getName() + DISTANCE_BELOW_SURFACE_MAXIMUM;
      } else {
         ReportingUtils.showError("Unknown Surface data type");
         throw new RuntimeException();
      }
   }
 
      @Override
   public boolean isValid(CovariantValue potentialValue) {
      return potentialValue.getType() == CovariantType.DOUBLE;
   }

   @Override
   public CovariantValue[] getAllowedValues() {
      //not applicable because all numerical for now
      return null;
   }

   @Override
   public boolean isDiscrete() {
      return false;
   }

   @Override
   public boolean hasLimits() {
      return false;
   }

   @Override
   public CovariantValue getLowerLimit() {
      return null;
   }

   @Override
   public CovariantValue getUpperLimit() {
      return null;
   }

   @Override
   public CovariantType getType() {
      return CovariantType.DOUBLE;
   }

   @Override
   public CovariantValue getValidValue(List<CovariantValue> vals) {
      double d = 0;
      while (true) {
         if (!vals.contains(new CovariantValue(d))) {
            return new CovariantValue(d);
         }
         d++;
      }
   }

   /**
    * 
    * @param corners
    * @param min true to get min, false to get max
    * @return 
    */
   private double distanceToSurface(Point2D.Double[] corners, double zVal, boolean min) {      
      //check a grid of points spanning entire position        
      //square is aligned with axes in pixel space, so convert to pixel space to generate test points
      double xSpan = corners[2].getX() - corners[0].getX();
      double ySpan = corners[2].getY() - corners[0].getY();
      Point2D.Double pixelSpan = new Point2D.Double();
      AffineTransform transform = AffineUtils.getAffineTransform(surface_.getPixelSizeConfig(),0, 0);
      try {
         transform.inverseTransform(new Point2D.Double(xSpan, ySpan), pixelSpan);
      } catch (NoninvertibleTransformException ex) {
         ReportingUtils.showError("Problem inverting affine transform");
      }
      double minDistance = Integer.MAX_VALUE;
      double maxDistance = Integer.MIN_VALUE;
      for (double x = 0; x <= pixelSpan.x; x += pixelSpan.x / (double) NUM_XY_TEST_POINTS) {
         for (double y = 0; y <= pixelSpan.y; y += pixelSpan.y / (double) NUM_XY_TEST_POINTS) {
            //convert these abritray pixel coordinates back to stage coordinates
            double[] transformMaxtrix = new double[6];
            transform.getMatrix(transformMaxtrix);
            transformMaxtrix[4] = corners[0].getX();
            transformMaxtrix[5] = corners[0].getY();
            //create new transform with translation applied
            transform = new AffineTransform(transformMaxtrix);
            Point2D.Double stageCoords = new Point2D.Double();
            transform.transform(new Point2D.Double(x, y), stageCoords);
            //test point for inclusion of position
            Double interpVal = surface_.getCurrentInterpolation().getInterpolatedValue(stageCoords.x, stageCoords.y, false);
            if (interpVal == null) {
              //if position is outside of convex hull, assume min distance is 0
                if (min) {
                    return 0;
                } else  {
                    //get extrapolated value
                    interpVal = surface_.getCurrentInterpolation().getInterpolatedValue(stageCoords.x, stageCoords.y, false);
                    maxDistance = Math.max(zVal - interpVal, maxDistance);
                }
            } else {
                minDistance = Math.min(zVal - interpVal, minDistance);
                maxDistance = Math.max(zVal - interpVal, maxDistance);
            }
           }
       }
      return min ? minDistance : maxDistance;
   }

   @Override
   public CovariantValue getCurrentValue(AcquisitionEvent event) {
      XYStagePosition xyPos = event.xyPosition_;
      if (category_.equals(DISTANCE_BELOW_SURFACE_CENTER)) {
         Point2D.Double center = xyPos.getCenter();
         SingleResolutionInterpolation interp = surface_.getCurrentInterpolation();
         Double interpValue = interp.getInterpolatedValue(center.x, center.y, false);
         //if interpolation is undefined at position center, assume distance below is 0
         return new CovariantValue((interpValue == null ? 0 : event.zPosition_ - interpValue) );
      } else if (category_.equals(DISTANCE_BELOW_SURFACE_MINIMUM)) {
         return new CovariantValue(distanceToSurface(xyPos.getFullTileCorners(), event.zPosition_, true));
      } else if (category_.equals(DISTANCE_BELOW_SURFACE_MAXIMUM)) {
         return new CovariantValue(distanceToSurface(xyPos.getFullTileCorners(), event.zPosition_, false));         
      } else {
         ReportingUtils.showError("Unknown Surface data type");
         throw new RuntimeException();
      }
   }

   @Override
   public void updateHardwareToValue(CovariantValue dVal) {
      ReportingUtils.showError("No hardware associated with Surface data");
      throw new RuntimeException();
   }

   
   
}
