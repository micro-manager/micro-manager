package de.embl.rieslab.emu.controller.utils;

import de.embl.rieslab.emu.utils.settings.BoolSetting;
import de.embl.rieslab.emu.utils.settings.Setting;
import java.util.HashMap;

public class GlobalSettings {

   /////////////////////////////////
   //// Write/read

   /**
    * Default configuration folder relative path.
    */
   public final static String HOME = "EMU/"; // home of the default configuration file

   /**
    * Configuration file's extension.
    */
   public final static String CONFIG_EXT = "uicfg"; // extension of the configuration file

   /**
    * Path to the default configuration file.
    */
   public final static String CONFIG_NAME = HOME + "config." + CONFIG_EXT;
         // path to the configuration file

   /////////////////////////////////
   //// Numerical

   /**
    * Used to compare floats.
    */
   public final static double EPSILON = 0.00001;


   /////////////////////////////////
   //// Global settings
   public final static String GLOBALSETTING_ENABLEUNALLOCATEDWARNINGS =
         "Enable unallocated warnings";

   @SuppressWarnings("rawtypes")
   public static HashMap<String, Setting> getDefaultGlobalSettings() {
      HashMap<String, Setting> settings = new HashMap<String, Setting>();

      // enable unallocated warnings
      Setting enableUnalloc = new BoolSetting(GLOBALSETTING_ENABLEUNALLOCATEDWARNINGS,
            "When enabled, a message will be prompted to the user if some UI properties are note allocated.",
            true);
      settings.put(GLOBALSETTING_ENABLEUNALLOCATEDWARNINGS, enableUnalloc);

      return settings;
   }
}
