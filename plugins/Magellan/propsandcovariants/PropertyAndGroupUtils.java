/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package propsandcovariants;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.prefs.Preferences;
import misc.Log;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import org.micromanager.MMStudio;
import org.micromanager.api.ScriptInterface;

/**
 *
 * @author Henry
 */
public class PropertyAndGroupUtils {
   
   public static final String PROPERTY_LABEL_PREFIX = "Prop_label_map_";
   public static final String PROPERTY_INDEX_PREFIX = "Prop_index_map_";

   
   public static void saveStoredProperties(Preferences prefs, LinkedList<SinglePropertyOrGroup> props, TreeMap<String,String> labels) {
      //remove old ones
      LinkedList<SinglePropertyOrGroup> oldStoredProps = readStoredGroupsAndProperties(prefs);
      for (SinglePropertyOrGroup prop : oldStoredProps) {
         removePropFromPrefs(prefs, prop);
      }
      //add new ones
      for (int i = 0; i < props.size(); i++) {
          SinglePropertyOrGroup prop = props.get(i);
         addPropToPrefs(prefs, prop, labels.get(prop.toString()), i);
      }
   }
   
   public static LinkedList<SinglePropertyOrGroup> readStoredGroupsAndProperties(Preferences prefs) {
      ArrayList<SinglePropertyOrGroup> all = readConfigGroupsAndProperties(true);
      TreeMap<Integer, SinglePropertyOrGroup> stored = new TreeMap<Integer, SinglePropertyOrGroup>();
      for (SinglePropertyOrGroup item : all) {
         String label = getPropNickname(prefs, item);
         if (label != null) {
             int index = getPropIndex(prefs, item);
             if (index != -1) {
                 stored.put(index, item);
             }
         }
      }
      LinkedList<SinglePropertyOrGroup> list = new LinkedList<SinglePropertyOrGroup>();
      for (int i : stored.keySet()) {
          SinglePropertyOrGroup singlePropOrGroup = stored.get(i);      
         list.add(singlePropOrGroup);
      }
      return list;
   }


   
   private static int getPropIndex(Preferences prefs, SinglePropertyOrGroup prop) {     
      return prefs.getInt(PROPERTY_INDEX_PREFIX + prop.toString(), -1);
   }

   public static String getPropNickname(Preferences prefs, SinglePropertyOrGroup prop) {
      if (prop == null) {        
          return null;
      }
       return prefs.get(PROPERTY_LABEL_PREFIX + prop.toString(), null);
   }

   private static void addPropToPrefs(Preferences prefs, SinglePropertyOrGroup prop, String label, int index) {
      prefs.put(PROPERTY_LABEL_PREFIX + prop.toString(), label);
      prefs.putInt(PROPERTY_INDEX_PREFIX + prop.toString(), index);
   }

   private static void removePropFromPrefs(Preferences prefs, SinglePropertyOrGroup prop) {
      prefs.remove(PROPERTY_LABEL_PREFIX + prop.toString());
      prefs.remove(PROPERTY_INDEX_PREFIX + prop.toString());
   }
   
   public static ArrayList<SinglePropertyOrGroup> readConfigGroupsAndProperties(boolean includeReadOnly) {
      ArrayList<SinglePropertyOrGroup> list = readConfigGroups();
      list.addAll(PropertyAndGroupUtils.readAllProperties(includeReadOnly));
      return list;
   }
   
   private static ArrayList<SinglePropertyOrGroup> readConfigGroups() {
      ArrayList<SinglePropertyOrGroup> groups = new ArrayList<SinglePropertyOrGroup>();
      ScriptInterface mmapi = MMStudio.getInstance();
      CMMCore core = mmapi.getMMCore();
      boolean liveMode = mmapi.isLiveModeOn();
         mmapi.enableLiveMode(false);
                  
         StrVector groupNames = core.getAvailableConfigGroups();   
      for (String group : groupNames) {
         SinglePropertyOrGroup pg = new SinglePropertyOrGroup();
         pg.readGroupValuesFromConfig(group);
         groups.add(pg);
      }
      mmapi.enableLiveMode(liveMode);
      return groups;
   }

   private static ArrayList<SinglePropertyOrGroup> readAllProperties(boolean includeReadOnly) {
      ScriptInterface mmapi = MMStudio.getInstance();
      CMMCore core = mmapi.getMMCore();
      ArrayList<SinglePropertyOrGroup> props = new ArrayList<SinglePropertyOrGroup>();
      try {
         StrVector devices = core.getLoadedDevices();
         boolean liveMode = mmapi.isLiveModeOn();
         mmapi.enableLiveMode(false);

         for (int i = 0; i < devices.size(); i++) {
            StrVector properties = core.getDevicePropertyNames(devices.get(i));
            for (int j = 0; j < properties.size(); j++) {
               SinglePropertyOrGroup item = new SinglePropertyOrGroup();
               item.readFromCore(devices.get(i), properties.get(j), false);
               if (!item.preInit && (!item.readOnly || includeReadOnly) ) {
                  props.add(item);
               }
            }
         }
         mmapi.enableLiveMode(liveMode);
      } catch (Exception e) {
         Log.log("Problem reading properties from core");
      }
      return props;
   }

   
   
}
