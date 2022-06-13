package de.embl.rieslab.emu.utils.settings;

/**
 * Defines a setting or parameter. This class is used for both plugin and global settings.
 *
 * @param <T> Setting's value type.
 * @author Joran Deschamps
 */
public abstract class Setting<T> {

   private final String description_;
   private final String name_;
   private T value_;
   private final SettingType type_;

   /**
    * Constructor.
    *
    * @param name        Short name of the setting.
    * @param description Description as it will appear in the help.
    * @param type        Type of the Setting.
    * @param defaultVal Default value for the setting.
    */
   public Setting(String name, String description, SettingType type, T defaultVal) {
      name_ = name;
      description_ = description;
      type_ = type;
      value_ = defaultVal;
   }

   /**
    * Returns the setting value as a String. Used to display the setting in the
    * {@link de.embl.rieslab.emu.configuration.ui.tables.SettingsTable SettingsTable}.
    *
    * @return Setting's string value.
    * @see de.embl.rieslab.emu.configuration.ui.tables.SettingsTable
    */
   public String getStringValue() {
      return getStringValue(value_);
   }

   /**
    * Converts the setting's value to a String. Used in {@link #getStringValue() getStringValue}
    *
    * @param val Setting's value.
    * @return Setting's value as a String.
    */
   protected abstract String getStringValue(T val);

   /**
    * Sets the value of the setting to {@code val}. If the value is not compatible,
    * then nothing happens.
    *
    * @param val Value
    */
   public void setStringValue(String val) {
      if (isValueCompatible(val)) {
         value_ = getTypedValue(val);
      }
   }

   /**
    * Returns the setting value.
    *
    * @return Setting's value.
    */
   public T getValue() {
      return value_;
   }

   /**
    * Returns the name of the setting.
    *
    * @return Setting's name.
    */
   public String getName() {
      return name_;
   }

   /**
    * Returns the description of the setting.
    *
    * @return Setting's description.
    */
   public String getDescription() {
      return description_;
   }

   /**
    * Converts a string to the setting's type.
    *
    * @param val Value to convert.
    * @return Value in the setting's type.
    */
   protected abstract T getTypedValue(String val);

   /**
    * Checks if {@code val} is a compatible value.
    *
    * @param val Value to test.
    * @return True if it is, false otherwise.
    */
   public abstract boolean isValueCompatible(String val);

   /**
    * Returns the setting's type.
    *
    * @return Type
    */
   public SettingType getType() {
      return type_;
   }

   /**
    * Returns the value of the Setting as a String.
    */
   @Override
   public String toString() {
      return getStringValue();
   }

   /**
    * Setting type: INTEGER, DOUBLE, STRING or BOOL.
    *
    * @author Joran Deschamps
    */
   public enum SettingType {
      INTEGER("Integer"), DOUBLE("Double"), STRING("String"), BOOL("Boolean");

      private final String value;

      SettingType(String value) {
         this.value = value;
      }

      @Override
      public String toString() {
         return value;
      }
   }

}
