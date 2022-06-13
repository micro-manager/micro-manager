package de.embl.rieslab.emu.utils.settings;

/**
 * A {@link Setting} with String value.
 *
 * @author Joran Deschamps
 */
public class StringSetting extends Setting<String> {

   /**
    * Constructor.
    *
    * @param name        Short name of the setting.
    * @param description Description as it will appear in the help.
    * @param default_val Default value for the setting.
    */
   public StringSetting(String name, String description, String default_val) {
      super(name, description, Setting.SettingType.STRING, default_val);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected String getStringValue(String val) {
      return val;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean isValueCompatible(String val) {
      return val != null;
   }

   @Override
   protected String getTypedValue(String val) {
      return val;
   }

}
