/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package propsandcovariants;

import org.micromanager.utils.ReportingUtils;
import propsandcovariants.Covariant;
import surfacesandregions.SurfaceInterpolator;

/**
 * Category about interpolated surface (e.g. distance below surface) to be used in
 * covaried settings
 */
public class SurfaceData implements Covariant {

   //all data must start with this prefix so they can be reconstructed when read from a text file on disk
   public static String PREFIX = "Surface data: ";
   
   public static String  DISTANCE_BELOW_SURFACE_CENTER = "--Vertical distance below at XY position center";
   public static String  DISTANCE_BELOW_SURFACE_MINIMUM = "--Minimum vertical distance below at XY position";
   public static String  DISTANCE_BELOW_SURFACE_MAXIMUM = "--Maximum vertical distance below at XY position";
   
   private String category_;
   private SurfaceInterpolator surface_;
   
   public SurfaceData(SurfaceInterpolator surface, String type) {
      category_ = type;
      surface_ = surface;
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
   
   
}
