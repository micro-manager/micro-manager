package acq;

import surfacesandregions.MultiPosGrid;
import surfacesandregions.SurfaceInterpolator;
import surfacesandregions.XYFootprint;

/**
 *
 * @author Henry
 */
public class FixedAreaAcquisitionSettings  {
   
   public static final int NO_SPACE = 0;
   public static final int SIMPLE_Z_STACK = 1;
   public static final int SURFACE_FIXED_DISTANCE_Z_STACK = 2;
   public static final int VOLUME_BETWEEN_SURFACES_Z_STACK = 3;
   public static final int REGION_2D = 4;
   public static final int TIME_MS = 0;
   public static final int TIME_S = 1;
   public static final int TIME_MIN = 2;
   
   //saving
   public String dir_, name_;
   //time
   public int numTimePoints_;
   public int timeIntervalUnit_; 

   //space
   public double zStep_, zStart_, zEnd_, distanceBelowSurface_, distanceAboveSurface_;
   public int spaceMode_;
   public double timePointInterval_;
   public SurfaceInterpolator topSurface_, bottomSurface_, fixedSurface_;
   public XYFootprint footprint_;
   
}
