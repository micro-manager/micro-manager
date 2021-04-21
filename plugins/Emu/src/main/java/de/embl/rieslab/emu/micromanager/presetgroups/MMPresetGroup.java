package de.embl.rieslab.emu.micromanager.presetgroups;

import java.util.ArrayList;

import de.embl.rieslab.emu.micromanager.mmproperties.MMProperty;
import mmcorej.StrVector;

/**
 * Class wrapper for a Micro-manager preset group.
 *
 * @author Joran Deschamps
 */
public class MMPresetGroup {

  private ArrayList<MMProperty> affectedmmprops_;
  private StrVector presets_;
  private String name_;

  /**
   * Constructor
   *
   * @param name Name of the preset group.
   * @param config StrVector of the preset group entries returned by Micro-manager.
   * @param affectedmmprops List of the MMproperties affected by the configuration group.
   */
  public MMPresetGroup(String name, StrVector config, ArrayList<MMProperty> affectedmmprops) {
    name_ = name;
    presets_ = config;
    affectedmmprops_ = affectedmmprops;
  }

  /**
   * Returns the different presets in the group.
   *
   * @return Vector of the group's presets.
   */
  public StrVector getPresets() {
    return presets_;
  }

  /**
   * Return the size of the preset group.
   *
   * @return Size of the preset group.
   */
  public int getGroupSize() {
    return (int) presets_.size();
  }

  /**
   * Returns the label of the preset indexed by ind.
   *
   * @param ind Index of the preset in the group.
   * @return Name of the preset.
   */
  public String getPreset(int ind) {
    if (ind < presets_.size()) {
      return presets_.get(ind);
    }
    return null;
  }

  /**
   * Tests if the preset s is in the preset group.
   *
   * @param s Name of the preset
   * @return True if the preset is part of the group, false otherwise.
   */
  public boolean hasPreset(String s) {
    for (int i = 0; i < presets_.size(); i++) {
      if (presets_.get(i).equals(s)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets the name of the preset group.
   *
   * @return Name of the preset group.
   */
  public String getName() {
    return name_;
  }

  /**
   * Returns the number of MM properties affected by this preset group.
   *
   * @return Number of affected properties.
   */
  public int getNumberOfMMProperties() {
    return affectedmmprops_.size();
  }

  /**
   * Returns a list of the MMProperties affected by this preset group.
   *
   * @return List of MMproperties.
   */
  public ArrayList<MMProperty> getAffectedProperties() {
    return affectedmmprops_;
  }
}
