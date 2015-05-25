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

package propsandcovariants;

import acq.AcquisitionEvent;
import coordinates.AffineUtils;
import coordinates.XYStagePosition;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;
import misc.Log;
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
      //number of test points per dimension for finding minimum distance to surface within angle
//   private static final int NUM_XY_TEST_POINTS_ANGLE = 5;
   
   public static String  SPACER = "--";
   public static String  DISTANCE_BELOW_SURFACE_CENTER = "Vertical distance below at XY position center";
   public static String  DISTANCE_BELOW_SURFACE_MINIMUM = "Minimum vertical distance below at XY position";
   public static String  DISTANCE_BELOW_SURFACE_MAXIMUM = "Maximum vertical distance below at XY position";
   public static String  LN_OPTIMAL_DISTANCE = "Lymph Node optimal distance";
   
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
   
   public SurfaceInterpolator getSurface() {
      return surface_;
   }
   
   public static String[] enumerateDataTypes() {
      return new String[] {DISTANCE_BELOW_SURFACE_CENTER, DISTANCE_BELOW_SURFACE_MINIMUM, 
          DISTANCE_BELOW_SURFACE_MAXIMUM, LN_OPTIMAL_DISTANCE};
   }
   
   @Override
   public String toString() {
      return getName();
   }
   
   
   @Override
   public String getAbbreviatedName() {
      if (category_.equals(DISTANCE_BELOW_SURFACE_CENTER)) {
         return "Vertical distance to " + surface_.getName();
       } else if (category_.equals(DISTANCE_BELOW_SURFACE_MINIMUM)) {
           return "Min vertical distance to " + surface_.getName();
       } else if (category_.equals(DISTANCE_BELOW_SURFACE_MAXIMUM)) {
           return "Min distance to " + surface_.getName();
       } else if (category_.equals(LN_OPTIMAL_DISTANCE)) {
           return "Lymph node optimal distance for " + surface_.getName();
 
       } else {
           Log.log("Unknown Surface data type");
           throw new RuntimeException();
       }
   }

    @Override
    public String getName() {
        return PREFIX + surface_.getName() + SPACER + category_;
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

    private double lnOptimalDistance(XYStagePosition xyPos, double zPosition) throws InterruptedException {
        // a special measure for curved surfaces, which gives:
        //-min distance at surface on flatter parts of curved surface (ie top)
        //-increased distance up to max distance as you go deeper
        //-higher distance at surface on side to account for curved surface blocking out some of exciation light
        //{minDistance,maxDistance, minNormalAngle, maxNormalAngle)
        double[] vals = distanceAndNormalCalc(xyPos.getFullTileCorners(), zPosition);
        double extraDistance = 0; //pretend actually deeper in LN than we are to account for blockage by curved surface
        double angleCutoff = 64; //angle cutoff is maximum nalge colletred by 1.2 NA objective
        double doublingDistance = 50;
        double angleCutoffPercent = 0;
        //twice as much power if angle goes to 0
        //doubling distance ~40-70 um when b = 0.01-0.018 i exponent
        //add extra distance to account for blockage by LN surface
        //never want to make extra distance higher than the double distance,
        //so extra power is capped at 2x
        angleCutoffPercent = Math.min(angleCutoff, vals[3]) / angleCutoff;
        extraDistance = angleCutoffPercent * doublingDistance;

        double curvatureCorrectedMin = vals[0] + extraDistance;
        double ret = Math.min(vals[1], Math.max(curvatureCorrectedMin, Math.pow(vals[0], 1.2)));
        return ret;
    }

   /**
    * 
    * @param corners
    * @param min true to get min, false to get max
    * @return {minDistance,maxDistance, minNormalAngle, maxNormalAngle)
    */
   private double[] distanceAndNormalCalc(Point2D.Double[] corners, double zVal) throws InterruptedException {      
      //check a grid of points spanning entire position        
      //square is aligned with axes in pixel space, so convert to pixel space to generate test points
      double xSpan = corners[2].getX() - corners[0].getX();
      double ySpan = corners[2].getY() - corners[0].getY();
      Point2D.Double pixelSpan = new Point2D.Double();
      AffineTransform transform = AffineUtils.getAffineTransform(surface_.getPixelSizeConfig(),0, 0);
      try {
         transform.inverseTransform(new Point2D.Double(xSpan, ySpan), pixelSpan);
      } catch (NoninvertibleTransformException ex) {
         Log.log("Problem inverting affine transform");
      }
      double minDistance = Integer.MAX_VALUE;
      double maxDistance = 0;
      double minNormalAngle = 90;
      double maxNormalAngle = 0;
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
              if (!surface_.waitForCurentInterpolation().isInterpDefined(stageCoords.x, stageCoords.y)) {
               //if position is outside of convex hull, assume min distance is 0
               minDistance = 0;
               //get extrapolated value for max distance
               float interpVal = surface_.waitForCurentInterpolation().getExtrapolatedValue(stageCoords.x, stageCoords.y);
               maxDistance = Math.max(zVal - interpVal, maxDistance);
               //only take actual values for normals
            } else {
                   float interpVal = surface_.waitForCurentInterpolation().getInterpolatedValue(stageCoords.x, stageCoords.y);
            float normalAngle = surface_.waitForCurentInterpolation().getNormalAngleToVertical(stageCoords.x, stageCoords.y);
               minDistance = Math.min(Math.max(0,zVal - interpVal), minDistance);
               maxDistance = Math.max(zVal - interpVal, maxDistance);
               minNormalAngle = Math.min(minNormalAngle, normalAngle);
               maxNormalAngle = Math.max(maxNormalAngle, normalAngle);
            }
         }
      }
      return new double[]{minDistance, maxDistance, minNormalAngle, maxNormalAngle};
   }

 
   
   @Override
   public CovariantValue getCurrentValue(AcquisitionEvent event) throws Exception {
      XYStagePosition xyPos = event.xyPosition_;
      if (category_.equals(DISTANCE_BELOW_SURFACE_CENTER)) {
         //if interpolation is undefined at position center, assume distance below is 0
         Point2D.Double center = xyPos.getCenter();
        SingleResolutionInterpolation interp = surface_.waitForCurentInterpolation();
        if (interp.isInterpDefined(center.x, center.y)) {
           return new CovariantValue( event.zPosition_ - interp.getInterpolatedValue(center.x, center.y)); 
        }
        return new CovariantValue(0.0);

      } else if (category_.equals(DISTANCE_BELOW_SURFACE_MINIMUM)) {
         return new CovariantValue(distanceAndNormalCalc(xyPos.getFullTileCorners(), event.zPosition_)[0]);
      } else if (category_.equals(DISTANCE_BELOW_SURFACE_MAXIMUM)) {
         return new CovariantValue(distanceAndNormalCalc(xyPos.getFullTileCorners(), event.zPosition_)[1]);
      } else if (category_.equals(LN_OPTIMAL_DISTANCE)) {
          return new CovariantValue(lnOptimalDistance(xyPos, event.zPosition_));
      } else {
         Log.log("Unknown Surface data type",true);
         throw new RuntimeException();
      }
   }  

   @Override
   public void updateHardwareToValue(CovariantValue dVal) {
      Log.log("No hardware associated with Surface data",true);
      throw new RuntimeException();
   }

   
   
}
