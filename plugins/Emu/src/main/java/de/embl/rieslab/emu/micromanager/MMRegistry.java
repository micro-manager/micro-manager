package de.embl.rieslab.emu.micromanager;

import de.embl.rieslab.emu.controller.log.Logger;
import de.embl.rieslab.emu.micromanager.mmproperties.MMDevice;
import de.embl.rieslab.emu.micromanager.mmproperties.MMPropertiesRegistry;
import de.embl.rieslab.emu.micromanager.mmproperties.MMProperty;
import de.embl.rieslab.emu.micromanager.mmproperties.PresetGroupAsMMProperty;
import de.embl.rieslab.emu.micromanager.presetgroups.MMPresetGroupRegistry;
import java.util.ArrayList;
import java.util.Iterator;
import org.micromanager.Studio;


/**
 * Class instantiating the {@link MMPropertiesRegistry} and {@link MMPresetGroupRegistry}.
 * In addition, it registers the configuration groups as MMProperties and add them to the
 * MMPropertiesRegistry.
 *
 * @author Joran Deschamps
 */
public class MMRegistry {

   private final MMPropertiesRegistry mmPropRegistry_; // holds the Micro-Manager device properties
   private final MMPresetGroupRegistry mmPresetGroupsRegistry_; // holds the configuration groups
   // from Micro-manager
   private final Studio studio_;
   private final Logger logger_;

   /**
    * Constructor. Instantiate the {@link MMPropertiesRegistry} and {@link MMPresetGroupRegistry}
    * and register the configuration groups as MMProperties.
    *
    * @param studio Micro-Manager studio.
    * @param logger EMU logger.
    */
   public MMRegistry(Studio studio, Logger logger) {

      studio_ = studio;
      logger_ = logger;

      // extracts MM properties
      mmPropRegistry_ = new MMPropertiesRegistry(studio_, logger_);
      mmPresetGroupsRegistry_ = new MMPresetGroupRegistry(studio_.getCMMCore(), mmPropRegistry_);

      // registers mmconfigs as mmproperties (so that they can be linked to UIProperties)
      registerMMConfAsDevice();
   }

   /**
    * Returns the {@link MMPropertiesRegistry}.
    *
    * @return Instance of {@link MMPropertiesRegistry}
    */
   public MMPropertiesRegistry getMMPropertiesRegistry() {
      return mmPropRegistry_;
   }

   /**
    * Returns the {@link MMPresetGroupRegistry}.
    *
    * @return Instance of {@link MMPresetGroupRegistry}
    */
   public MMPresetGroupRegistry getMMPresetGroupRegistry() {
      return mmPresetGroupsRegistry_;
   }

   /**
    * Wraps the configuration groups as MMProperties and adds them to the MMPropertiesRegistry.
    */
   @SuppressWarnings("rawtypes")
   private void registerMMConfAsDevice() {
      MMDevice dev = new MMDevice(PresetGroupAsMMProperty.KEY_MMCONFDEVICE);

      Iterator<String> it = mmPresetGroupsRegistry_.getMMPresetGroups().keySet().iterator();
      while (it.hasNext()) {
         String group = it.next();
         String[] values =
               mmPresetGroupsRegistry_.getMMPresetGroups().get(group).getPresets().toArray();
         ArrayList<MMProperty> affectedmmprops =
               mmPresetGroupsRegistry_.getMMPresetGroups().get(group).getAffectedProperties();

         dev.registerProperty(
               new PresetGroupAsMMProperty(studio_.app(), studio_.core(), logger_, group, values,
                     affectedmmprops));
      }

      mmPropRegistry_.addMMDevice(dev);
   }

}
