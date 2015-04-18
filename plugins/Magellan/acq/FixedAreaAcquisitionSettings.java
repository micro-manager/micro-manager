package acq;

import channels.ChannelSettings;
import imageconstruction.FrameIntegrationMethod;
import propsandcovariants.CovariantPairing;
import propsandcovariants.CovariantPairingsManager;
import java.util.ArrayList;
import java.util.prefs.Preferences;
import javax.swing.filechooser.FileSystemView;
import main.Magellan;
import org.micromanager.utils.ReportingUtils;
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
   public double zStep_, zStart_, zEnd_, distanceBelowFixedSurface_, distanceAboveFixedSurface_,
           distanceAboveTopSurface_, distanceBelowBottomSurface_;
   public int spaceMode_;
   public SurfaceInterpolator topSurface_, bottomSurface_, fixedSurface_;
   public XYFootprint footprint_;
   public int useTopOrBottomFootprint_;
   public double tileOverlap_; //stored as percent * 100, i.e. 55 => 55%
   
   //channels
   public ArrayList<ChannelSettings> channels_ = new ArrayList<ChannelSettings>();
   
   //Covarying props
   public ArrayList<CovariantPairing> propPairings_ = new ArrayList<CovariantPairing>();
   
   //autofocus
   public boolean autofocusEnabled_;
   public String autoFocusZDevice_;
   public String autofocusChannelName_;
   public double autofocusMaxDisplacemnet_um_;
   public boolean setInitialAutofocusPosition_;
   public double initialAutofocusPosition_;
   
   //image construction
   public int imageFilterType_;
   public double rank_;
   
   public FixedAreaAcquisitionSettings() {
      Preferences prefs = Magellan.getPrefs();
      name_ = prefs.get(PREF_PREFIX + "NAME", "Untitled");
      timeEnabled_ = prefs.getBoolean(PREF_PREFIX + "TE", false);
      timePointInterval_ = prefs.getDouble(PREF_PREFIX + "TPI", 0);
      numTimePoints_ = prefs.getInt(PREF_PREFIX + "NTP", 1);
      timeIntervalUnit_ = prefs.getInt(PREF_PREFIX + "TPIU", 0);
      //space
      zStep_ = prefs.getDouble(PREF_PREFIX + "ZSTEP", 1);
      zStart_ = prefs.getDouble(PREF_PREFIX + "ZSTART", 0);
      zEnd_ = prefs.getDouble(PREF_PREFIX + "ZEND", 0);
      distanceBelowFixedSurface_ = prefs.getDouble(PREF_PREFIX + "ZDISTBELOWFIXED", 0);
      distanceAboveFixedSurface_ = prefs.getDouble(PREF_PREFIX + "ZDISTABOVEFIXED", 0);
      distanceBelowBottomSurface_ = prefs.getDouble(PREF_PREFIX + "ZDISTBELOWBOTTOM", 0);
      distanceAboveTopSurface_ = prefs.getDouble(PREF_PREFIX + "ZDISTABOVETOP", 0);
      spaceMode_ = prefs.getInt(PREF_PREFIX + "SPACEMODE", 0);
      tileOverlap_ = prefs.getDouble(PREF_PREFIX + "TILEOVERLAP", 5);
      //autofocus
      autofocusMaxDisplacemnet_um_ =  prefs.getDouble(PREF_PREFIX + "AFMAXDISP", 0.0);
      autofocusChannelName_ = prefs.get(PREF_PREFIX + "AFCHANNELNAME", null);
      autoFocusZDevice_ = prefs.get(PREF_PREFIX + "AFZNAME", null);      
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
      //image filtering
      //dont load so defaults to frame average for now
//      imageFilterType_ = prefs.getInt(PREF_PREFIX + "IMAGE_FILTER", FrameIntegrationMethod.FRAME_AVERAGE);   
      rank_ = prefs.getDouble(PREF_PREFIX + "RANK", 0.95);
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
   
   public static double getStoredTileOverlapPercentage() {
      return Magellan.getPrefs().getDouble(PREF_PREFIX + "TILEOVERLAP", 5);
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
      prefs.put(PREF_PREFIX + "NAME", name_);
      prefs.putBoolean(PREF_PREFIX + "TE", timeEnabled_);
      prefs.putDouble(PREF_PREFIX + "TPI", timePointInterval_);
      prefs.putInt(PREF_PREFIX + "NTP", numTimePoints_);
      prefs.putInt(PREF_PREFIX + "TPIU", timeIntervalUnit_);
      //space
      prefs.putDouble(PREF_PREFIX + "ZSTEP", zStep_);
      prefs.putDouble(PREF_PREFIX + "ZSTART", zStart_);
      prefs.putDouble(PREF_PREFIX + "ZEND", zEnd_);
      prefs.putDouble(PREF_PREFIX + "ZDISTBELOWFIXED", distanceBelowFixedSurface_);
      prefs.putDouble(PREF_PREFIX + "ZDISTABOVEFIXED", distanceAboveFixedSurface_);
      prefs.putDouble(PREF_PREFIX + "ZDISTBELOWBOTTOM", distanceBelowBottomSurface_);
      prefs.putDouble(PREF_PREFIX + "ZDISTABOVETOP", distanceAboveTopSurface_);
      prefs.putInt(PREF_PREFIX + "SPACEMODE", spaceMode_);
      prefs.putDouble(PREF_PREFIX + "TILEOVERLAP", tileOverlap_);
      //autofocus
      prefs.putDouble(PREF_PREFIX + "AFMAXDISP", autofocusMaxDisplacemnet_um_);
      if (autofocusChannelName_ != null) {
         prefs.put(PREF_PREFIX + "AFCHANNELNAME", autofocusChannelName_);
      }
      if (autoFocusZDevice_ != null) {
         prefs.put(PREF_PREFIX + "AFZNAME", autoFocusZDevice_);
      }
      //image filtering
      //store but dont load filter type for now
      prefs.putInt(PREF_PREFIX + "IMAGE_FILTER", FrameIntegrationMethod.FRAME_AVERAGE);      
      prefs.putDouble(PREF_PREFIX + "RANK", rank_);
      
   }
   
}
