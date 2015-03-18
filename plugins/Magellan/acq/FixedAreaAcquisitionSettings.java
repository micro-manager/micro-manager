package acq;

import propsandcovariants.CovariantPairing;
import propsandcovariants.CovariantPairingsManager;
import java.util.ArrayList;
import java.util.prefs.Preferences;
import javax.swing.filechooser.FileSystemView;
import main.Magellan;
import org.micromanager.utils.ReportingUtils;
import surfacesandregions.MultiPosRegion;
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
   public static int FOOTPRINT_FROM_TOP = 0, FOOTPRINT_FROM_BOTTOM = 1;
   
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
   public int useTopOrBottomFootprint_;
   
   //Covarying props
   public ArrayList<CovariantPairing> propPairings_ = new ArrayList<CovariantPairing>();
   
   
   
   public FixedAreaAcquisitionSettings() {
      Preferences prefs = Magellan.getPrefs();
      dir_ =  prefs.get(PREF_PREFIX + "DIR", FileSystemView.getFileSystemView().getHomeDirectory().getAbsolutePath());
      name_ = prefs.get(PREF_PREFIX + "NAME", "Untitled");
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
      //add all pairings currently present
      CovariantPairingsManager pairManager = CovariantPairingsManager.getInstance();
      //null on startup, but no pairings to add anyway  
      if (pairManager != null) {
         for (int i = 0; i < pairManager.getNumPairings(); i++) {
            CovariantPairing potentialPair = pairManager.getPair(i);
            if (!checkForRedundantPairing(potentialPair)) {
               propPairings_.add(potentialPair);
            }
         }
      }
   }
   
   public boolean hasPairing(CovariantPairing pair) {
      return propPairings_.contains(pair);
   }
   
   public void removePropPairing(CovariantPairing pair) {
      propPairings_.remove(pair);
   }
   
   public void addPropPairing(CovariantPairing pair) {
      if (propPairings_.contains(pair)) {
         ReportingUtils.showError("Tried to add property pair that was already present");
      }
      if (checkForRedundantPairing(pair)) {
         ReportingUtils.showMessage("Must existing pairing between same two properties first");
      }
      propPairings_.add(pair);
      
   }
   
   private boolean checkForRedundantPairing(CovariantPairing pair) {
      for (CovariantPairing p : propPairings_) {
         if (p.getIndependentName(false).equals(pair.getIndependentName(false)) && p.getDependentName(false).equals(pair.getDependentName(false))) {
            return true;
         }
      }
      return false;
   }
   
   public void storePreferedValues() {
      Preferences prefs = Magellan.getPrefs();
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
