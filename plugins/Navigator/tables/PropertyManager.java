/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tables;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.prefs.Preferences;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import org.micromanager.MMStudio;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.PropertyItem;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class PropertyManager {
   
   public static final String PROPERTY_LABEL_PREFIX = "Prop_label_map_";
   public static final String PROPERTY_INDEX_PREFIX = "Prop_index_map_";

   
   public static void saveStoredProperties(Preferences prefs, LinkedList<PropertyItem> props, TreeMap<String,String> labels) {
      //remove old ones
      LinkedList<PropertyItem> oldStoredProps = readStoredProperties(prefs);
      for (PropertyItem prop : oldStoredProps) {
         removePropFromPrefs(prefs, prop);
      }
      //add new ones
      for (int i = 0; i < props.size(); i++) {
         addPropToPrefs(prefs, props.get(i), labels.get(props.get(i).device+"-"+props.get(i).name), i);
      }
   }
   
   public static LinkedList<PropertyItem> readStoredProperties(Preferences prefs, ArrayList<PropertyItem> allProps) {
      TreeMap<Integer, PropertyItem> storedProps = new TreeMap<Integer, PropertyItem>();
      for (PropertyItem item : allProps) {
         String label = getPropLabel(prefs, item);
         if (label != null) {
            storedProps.put(getPropIndex(prefs, item), item);
         }
      }
      LinkedList<PropertyItem> list = new LinkedList<PropertyItem>();
      for (int i = 0; i < storedProps.keySet().size(); i++) {
         list.add(storedProps.get(i));
      }
      return list;
   }

   public static LinkedList<PropertyItem> readStoredProperties(Preferences prefs) {
      return readStoredProperties(prefs,readAllProperties());   
   }
   
   private static int getPropIndex(Preferences prefs, PropertyItem prop) {     
      return prefs.getInt(PROPERTY_INDEX_PREFIX + prop.device + "-" + prop.name, -1);
   }

   public static String getPropLabel(Preferences prefs, PropertyItem prop) {
      return prefs.get(PROPERTY_LABEL_PREFIX + prop.device + "-" + prop.name, null);
   }

   private static void addPropToPrefs(Preferences prefs, PropertyItem prop, String label, int index) {
      prefs.put(PROPERTY_LABEL_PREFIX + prop.device + "-" + prop.name, label);
      prefs.putInt(PROPERTY_INDEX_PREFIX + prop.device + "-" + prop.name, index);
   }

   private static void removePropFromPrefs(Preferences prefs, PropertyItem prop) {
      prefs.remove(PROPERTY_LABEL_PREFIX + prop.device + "-" + prop.name);
      prefs.remove(PROPERTY_INDEX_PREFIX + prop.device + "-" + prop.name);
   }

   public static ArrayList<PropertyItem> readAllProperties() {
      ScriptInterface mmapi = MMStudio.getInstance();
      CMMCore core = mmapi.getMMCore();
      ArrayList<PropertyItem> props = new ArrayList<PropertyItem>();
      try {
         StrVector devices = core.getLoadedDevices();
         boolean liveMode = mmapi.isLiveModeOn();
         mmapi.enableLiveMode(false);

         for (int i = 0; i < devices.size(); i++) {

            StrVector properties = core.getDevicePropertyNames(devices.get(i));
            for (int j = 0; j < properties.size(); j++) {
               PropertyItem item = new PropertyItem();
               item.readFromCore(core, devices.get(i), properties.get(j), false);
               if (!item.preInit) {
                  item.confInclude = false;
                  item.setValueFromCoreString(core.getProperty(devices.get(i), properties.get(j)));
                  props.add(item);
                  //count the number of properties selected
               }
            }
         }
         mmapi.enableLiveMode(liveMode);
      } catch (Exception e) {
         ReportingUtils.showError("Problem reading properties from core");
      }
      return props;
   }
   
   
   
}
