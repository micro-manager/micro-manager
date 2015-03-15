/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package propsandcovariants;

import acq.AcquisitionEvent;
import coordinates.AffineUtils;
import coordinates.XYStagePosition;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.List;
import org.micromanager.utils.ReportingUtils;
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
   
   public SurfaceData(SurfaceInterpolator surface, String type) {
      category_ = type;
      surface_ = surface;
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
      long start = System.currentTimeMillis();
      
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
            Float interpVal = surface_.getCurrentInterpolation().getInterpolatedValue(stageCoords.x, stageCoords.y, true);
            minDistance = Math.min(zVal - interpVal,minDistance);
            maxDistance = Math.max(zVal - interpVal,maxDistance);
            if (interpVal == null) {
               ReportingUtils.showError("Null surface interpolation value!");
            }
         }
      }
      
      System.out.println("Minmax distance calc time: " + (System.currentTimeMillis() - start));
      return min ? minDistance : maxDistance;
   }

   @Override
   public CovariantValue getCurrentValue(AcquisitionEvent event) {
      XYStagePosition xyPos = surface_.getXYPositions().get(event.positionIndex_);
      if (category_.equals(DISTANCE_BELOW_SURFACE_CENTER)) {
         Point2D.Double center = xyPos.getCenter();
         return new CovariantValue(event.zPosition_ - surface_.getCurrentInterpolation().getInterpolatedValue(center.x, center.y, true));
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
