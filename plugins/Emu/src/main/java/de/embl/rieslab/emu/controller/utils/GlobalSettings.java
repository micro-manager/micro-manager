package de.embl.rieslab.emu.controller.utils;

import java.util.HashMap;

import de.embl.rieslab.emu.utils.settings.BoolSetting;
import de.embl.rieslab.emu.utils.settings.Setting;

public class GlobalSettings {

  /////////////////////////////////
  //// Write/read

  /** Default configuration folder relative path. */
  public static final String HOME = "EMU/"; // home of the default configuration file

  /** Configuration file's extension. */
  public static final String CONFIG_EXT = "uicfg"; // extension of the configuration file

  /** Path to the default configuration file. */
  public static final String CONFIG_NAME =
      HOME + "config." + CONFIG_EXT; // path to the configuration file

  /////////////////////////////////
  //// Numerical

  /** Used to compare floats. */
  public static final double EPSILON = 0.00001;

  /////////////////////////////////
  //// Global settings
  public static final String GLOBALSETTING_ENABLEUNALLOCATEDWARNINGS =
      "Enable unallocated warnings";

  @SuppressWarnings("rawtypes")
  public static HashMap<String, Setting> getDefaultGlobalSettings() {
    HashMap<String, Setting> settings = new HashMap<String, Setting>();

    // enable unallocated warnings
    Setting enableUnalloc =
        new BoolSetting(
            GLOBALSETTING_ENABLEUNALLOCATEDWARNINGS,
            "When enabled, a message will be prompted to the user if some UI properties are note allocated.",
            true);
    settings.put(GLOBALSETTING_ENABLEUNALLOCATEDWARNINGS, enableUnalloc);

    return settings;
  }
}
