package de.embl.rieslab.emu.utils.settings;

import de.embl.rieslab.emu.utils.EmuUtils;

/**
 * A {@link Setting} with Integer value.
 *
 * @author Joran Deschamps
 */
public class IntSetting extends Setting<Integer> {

   /**
    * Constructor.
    *
    * @param name        Short name of the setting.
    * @param description Description as it will appear in the help.
    * @param defaultVal Default value for the setting.
    */
   public IntSetting(String name, String description, Integer defaultVal) {
      super(name, description, Setting.SettingType.INTEGER, defaultVal);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected String getStringValue(Integer val) {
      return val.toString();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean isValueCompatible(String val) {
      return EmuUtils.isInteger(val);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected Integer getTypedValue(String val) {
      // the superclass already checks for compatibility
      return new Integer(val);
   }

}
