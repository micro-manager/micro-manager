package acq;

import java.util.prefs.Preferences;
import javax.swing.filechooser.FileSystemView;
import main.Magellan;

/**
 * Container for settings specific to explore acquisition
 * @author Henry
 */
public class ExploreAcqSettings {
   
   private static final String EXPLORE_NAME_PREF = "Explore acq name";
   private static final String EXPLORE_DIR_PREF = "Explore acq dir";
   private static final String EXPLORE_Z_STEP = "Explore acq zStep";
   private static final String EXPLORE_TILE_OVERLAP = "Explore tile overlap";

   
   public final double zStep_;
   public final String dir_, name_;
   public final double tileOverlap_;

   public ExploreAcqSettings(double zStep, double overlapPercent, String dir, String name) {
      zStep_ = zStep;
      dir_ = dir;
      name_ = name;   
      tileOverlap_ = overlapPercent;
      Preferences prefs = Magellan.getPrefs();
      //now that explore acquisition is being run, store values
      prefs.put(EXPLORE_DIR_PREF, dir);
      prefs.put(EXPLORE_NAME_PREF, name);
      prefs.putDouble(EXPLORE_Z_STEP, zStep_);
      prefs.putDouble(EXPLORE_TILE_OVERLAP, overlapPercent);
   }
   
   public static String getNameFromPrefs() {
      Preferences prefs = Magellan.getPrefs();
      return prefs.get(EXPLORE_NAME_PREF, "Untitled Explore Acquisition" );
   } 
   
   public static String getDirFromPrefs() {
      Preferences prefs = Magellan.getPrefs();
      return prefs.get(EXPLORE_DIR_PREF, FileSystemView.getFileSystemView().getHomeDirectory().getAbsolutePath() );
   }
   
   public static double getZStepFromPrefs() {
      Preferences prefs = Magellan.getPrefs();
      return prefs.getDouble(EXPLORE_Z_STEP, 1);
   }

   public static double getExploreTileOverlapFromPrefs() {
      Preferences prefs = Magellan.getPrefs();
      return prefs.getDouble(EXPLORE_TILE_OVERLAP, 0);
   }

   

}
