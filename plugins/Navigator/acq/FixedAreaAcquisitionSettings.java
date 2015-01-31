package acq;

import java.util.prefs.Preferences;
import main.Navigator;
import surfacesandregions.MultiPosGrid;
import surfacesandregions.SurfaceInterpolator;
import surfacesandregions.XYFootprint;

/**
 *
 * @author Henry
 */
public class FixedAreaAcquisitionSettings  {
   
   private static final String PREF_PREFIX = "Fixed area acquisition ";

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
   public boolean timeEnabled_;
   public double timePointInterval_;
   public int numTimePoints_;
   public int timeIntervalUnit_; 

   //space
   public double zStep_, zStart_, zEnd_, distanceBelowSurface_, distanceAboveSurface_;
   public int spaceMode_;
   public SurfaceInterpolator topSurface_, bottomSurface_, fixedSurface_;
   public XYFootprint footprint_;
   
   public FixedAreaAcquisitionSettings() {
      Preferences prefs = Navigator.getPrefs();
      dir_ =  prefs.get(PREF_PREFIX + "DIR", "");
      name_ = prefs.get(PREF_PREFIX + "NAME", "");
      timeEnabled_ = prefs.getBoolean(PREF_PREFIX + "TE", false);
      timePointInterval_ = prefs.getDouble(PREF_PREFIX + "TPI", 0);
      numTimePoints_ = prefs.getInt(PREF_PREFIX + "NTP", 1);
      timeIntervalUnit_ = prefs.getInt(PREF_PREFIX + "TPIU", 0);
      zStep_ = prefs.getDouble(PREF_PREFIX + "ZSTEP", 1);
      zStart_ = prefs.getDouble(PREF_PREFIX + "ZSTART", 0);
      zEnd_ = prefs.getDouble(PREF_PREFIX + "ZEND", 0);
      distanceBelowSurface_ = prefs.getDouble(PREF_PREFIX + "ZDISTBELOW", 0);
      distanceAboveSurface_ = prefs.getDouble(PREF_PREFIX + "ZDISTABOVE", 0);
      spaceMode_ = prefs.getInt(PREF_PREFIX + "SPACEMODE", 0);
   }
   
   public void storePreferedValues() {
      Preferences prefs = Navigator.getPrefs();
      prefs.put(PREF_PREFIX + "DIR", dir_);
      prefs.put(PREF_PREFIX + "NAME", name_);
      prefs.putBoolean(PREF_PREFIX + "TE", timeEnabled_);
      prefs.putDouble(PREF_PREFIX + "TPI", timePointInterval_);
      prefs.putInt(PREF_PREFIX + "NTP", numTimePoints_);
      prefs.putInt(PREF_PREFIX + "TPIU", timeIntervalUnit_);
      prefs.putDouble(PREF_PREFIX + "ZSTEP", zStep_);
      prefs.putDouble(PREF_PREFIX + "ZSTART", zStart_);
      prefs.putDouble(PREF_PREFIX + "ZEND", zEnd_);
      prefs.putDouble(PREF_PREFIX + "ZDISTBELOW", distanceBelowSurface_);
      prefs.putDouble(PREF_PREFIX + "ZDISTABOVE", distanceAboveSurface_);
      prefs.putInt(PREF_PREFIX + "SPACEMODE", spaceMode_);
   }
   
}
