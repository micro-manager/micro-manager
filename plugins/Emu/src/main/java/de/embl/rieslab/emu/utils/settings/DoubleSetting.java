package de.embl.rieslab.emu.utils.settings;

import de.embl.rieslab.emu.utils.EmuUtils;

/**
 * A {@link Setting} with Double value.
 *
 * @author Joran Deschamps
 */
public class DoubleSetting extends Setting<Double> {

   /**
    * Constructor.
    *
    * @param name        Short name of the setting.
    * @param description Description as it will appear in the help.
    * @param default_val Default value for the setting.
    */
   public DoubleSetting(String name, String description, Double default_val) {
      super(name, description, Setting.SettingType.DOUBLE, default_val);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected String getStringValue(Double val) {
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
   protected Double getTypedValue(String val) {
      // the superclass already checks for compatibility
      return new Double(val);
   }

}
