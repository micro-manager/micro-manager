package de.embl.rieslab.emu.micromanager.presetgroups;

import de.embl.rieslab.emu.micromanager.mmproperties.MMPropertiesRegistry;
import de.embl.rieslab.emu.micromanager.mmproperties.MMProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.StrVector;

/**
 * Class holding a HashMap of the {@link MMPresetGroup}s and their name as displayed in the
 * current Micro-manager session.
 *
 * @author Joran Deschamps
 */
public class MMPresetGroupRegistry {

   private final CMMCore core_;
   private final HashMap<String, MMPresetGroup> groups_;

   /**
    * The constructor receives the current Micro-manager core instance and extracts all the
    * preset groups, building a HashMap of the MMPresetGroup indexed by the group name.
    *
    * @param core      Micro-manager CMMCore instance.
    * @param mmpropReg Micro-manager properties registry.
    */
   public MMPresetGroupRegistry(CMMCore core, MMPropertiesRegistry mmpropReg) {
      core_ = core;

      groups_ = new HashMap<String, MMPresetGroup>();

      retrievePresetGroups(mmpropReg);
   }

   private void retrievePresetGroups(MMPropertiesRegistry mmproperties) {
      StrVector groups = core_.getAvailableConfigGroups();

      if (groups != null) {
         for (int i = 0; i < groups.size(); i++) {
            ArrayList<MMProperty> affectedmmprops = new ArrayList<MMProperty>();

            Configuration conf;
            try {
               conf = core_.getConfigGroupState(groups.get(i));
               for (int j = 0; j < conf.size(); j++) {
                  affectedmmprops.add(mmproperties.getProperties()
                        .get(conf.getSetting(j).getDeviceLabel() + "-"
                              + conf.getSetting(j).getPropertyName()));
               }
               groups_.put(groups.get(i),
                     new MMPresetGroup(groups.get(i), core_.getAvailableConfigs(groups.get(i)),
                           affectedmmprops));

            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      }
   }

   /**
    * Returns the {@link MMPresetGroup}s indexed in a map by their name.
    *
    * @return HashMap of the {@link MMPresetGroup}s.
    */
   public HashMap<String, MMPresetGroup> getMMPresetGroups() {
      return groups_;
   }

   /**
    * Returns a HashMap mapping the preset group names (keys) and an array of
    * string representing the names of the different presets within each group.
    *
    * @return HashMap of the channels of each {@link MMPresetGroup} indexed by the name of
    *     the group.
    */
   public HashMap<String, String[]> getMMPresetGroupChannels() {
      HashMap<String, String[]> map = new HashMap<String, String[]>();

      Iterator<String> it = groups_.keySet().iterator();
      String s;
      while (it.hasNext()) {
         s = it.next();
         map.put(s, groups_.get(s).getPresets().toArray());
      }
      return map;
   }

   /**
    * Checks if the preset group {@code mmconfig} exists.
    *
    * @param mmPresetGroup Name of a {@link MMPresetGroup}
    * @return True if {@code mmPresetGroup} exists, false otherwise.
    */
   public boolean hasMMPresetGroup(String mmPresetGroup) {
      return groups_.containsKey(mmPresetGroup);
   }

   /**
    * Returns the current channel of the MM preset group {@code mmPresetGroup}.
    *
    * @param mmPresetGroup MM preset group
    * @return The state of mmPresetGroup.
    */
   public String getCurrentMMPresetGroupChannel(String mmPresetGroup) {
      if (hasMMPresetGroup(mmPresetGroup)) {
         try {
            return core_.getCurrentConfig(mmPresetGroup);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
      return null;
   }
}
  