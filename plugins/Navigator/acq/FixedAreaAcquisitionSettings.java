package acq;

import surfacesandregions.SurfaceInterpolator;

/**
 *
 * @author Henry
 */
public class FixedAreaAcquisitionSettings  {
   
   public static final int NO_Z_STACK = 0;
   public static final int SIMPLE_Z_STACK = 1;
   public static final int SURFACE_FIXED_DISTANCE_Z_STACK = 2;
   public static final int VOLUME_BETWEEN_SURFACES_Z_STACK = 3;

   
   public String dir_, name_;
   public double zStep_, zStart_, zEnd_, distanceBelowSurface_;
   public int numTimePoints_, zStackMode_;
   public double timePointInterval_ms_;
   public SurfaceInterpolator surface_, bottomSurface_;
   
   public FixedAreaAcquisitionSettings(String dir, String name, 
           int numTimePoints, Number timePointInterval, int timeUnit,
           int zStackMode, Number zStart, Number zEnd,
           SurfaceInterpolator surface, Number distanceBelowSurface,
           SurfaceInterpolator topSurface, SurfaceInterpolator bottomSurface,
           Number zStep) {
      dir_ = dir;
      name_ = name;
      if (numTimePoints > 1) {
         numTimePoints_ = numTimePoints;
         timePointInterval_ms_ = timePointInterval.doubleValue() * (timeUnit == 1 ? 1000 : (timeUnit == 2 ? 60000 : 1));
      } else {
         numTimePoints_ = 1;
      }
      zStackMode_ = zStackMode;
      zStep_ =  zStep.doubleValue();
      if (zStackMode_ == SIMPLE_Z_STACK) {
         zStart_ = zStart.doubleValue();
         zEnd_ = zEnd.doubleValue();
      } else if (zStackMode == SURFACE_FIXED_DISTANCE_Z_STACK) {
         surface_ = surface;
         distanceBelowSurface_ = distanceBelowSurface.doubleValue();
      } else if (zStackMode == VOLUME_BETWEEN_SURFACES_Z_STACK) {
         surface_ = topSurface;
         bottomSurface_ = bottomSurface;
      }      
   }
   
}
