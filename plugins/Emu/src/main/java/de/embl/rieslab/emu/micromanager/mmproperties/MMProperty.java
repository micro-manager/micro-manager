package de.embl.rieslab.emu.micromanager.mmproperties;

import de.embl.rieslab.emu.controller.log.Logger;
import de.embl.rieslab.emu.ui.uiproperties.UIProperty;
import java.util.ArrayList;
import mmcorej.CMMCore;

/**
 * Abstract wrapper for a Micro-manager device property. This class allows retrieving and modifying
 * the value of a device property, within its limits, or allowed values, as defined by
 * Micro-manager. Each MMProperty is identified by a hash "deviceLabel-propertyLabel" where
 * deviceLabel is the label of the device holding the property and propertyLabel is the label
 * of the property.
 *
 * @param <T> Type of the Micro-manager property.
 * @author Joran Deschamps
 */
public abstract class MMProperty<T> {

   protected Logger logger_;
   protected T value;
   private final CMMCore core_;
   private final String label_;
   private final String devicelabel_;
   private final String hash_;
   private final MMPropertyType type_;
   private final boolean readOnly;
   private final boolean hasLimits;
   private final boolean hasAllowedValues;
   private T[] allowedValues;
   private T maxValue;
   private T minValue;
   private final ArrayList<UIProperty> listeners_;

   /**
    * Builds a MMproperty without limits or allowed values. The property can be set to be read-only.
    *
    * @param core          Micro-manager core.
    * @param logger        EMU logger.
    * @param type          Micro-Manager device property type.
    * @param deviceLabel   Label of the parent device as defined in Micro-manager.
    * @param propertyLabel Label of the device property as defined in Micro-manager.
    * @param readOnly      True if the device property is read-only, false otherwise.
    */
   protected MMProperty(CMMCore core, Logger logger, MMPropertyType type, String deviceLabel,
                        String propertyLabel, boolean readOnly) {
      this.core_ = core;
      this.logger_ = logger;
      this.devicelabel_ = deviceLabel;
      this.label_ = propertyLabel;

      this.type_ = type;

      this.readOnly = readOnly;
      this.hasLimits = false;
      this.hasAllowedValues = false;

      hash_ = devicelabel_ + "-" + label_;

      listeners_ = new ArrayList<UIProperty>();

      getValue();
   }

   /**
    * Builds a MMProperty with limits.
    *
    * @param core          Micro-manager core.
    * @param logger        EMU logger.
    * @param type          Micro-Manager device property type.
    * @param deviceLabel   Label of the parent device as defined in Micro-manager.
    * @param propertyLabel Label of the device property as defined in Micro-manager.
    * @param upperLimit    Upper limit of the device property value.
    * @param lowerLimit    Lower limit of the device property value.
    */
   protected MMProperty(CMMCore core, Logger logger, MMPropertyType type, String deviceLabel,
                        String propertyLabel, double upperLimit, double lowerLimit) {
      this.core_ = core;
      this.logger_ = logger;
      this.devicelabel_ = deviceLabel;
      this.label_ = propertyLabel;

      this.type_ = type;

      this.readOnly = false;
      this.hasLimits = true;
      this.hasAllowedValues = false;

      // as a safeguard
      if (Double.compare(upperLimit, lowerLimit) < 0) {
         this.maxValue = convertToValue(lowerLimit);
         this.minValue = convertToValue(upperLimit);
      } else {
         this.maxValue = convertToValue(upperLimit);
         this.minValue = convertToValue(lowerLimit);
      }

      hash_ = devicelabel_ + "-" + label_;

      listeners_ = new ArrayList<UIProperty>();

      getValue();
   }

   /**
    * Builds a MMProperty with allowed values.
    *
    * @param core          Micro-manager core.
    * @param logger        EMU logger.
    * @param type          Micro-Manager device property type.
    * @param deviceLabel   Label of the parent device as defined in Micro-manager.
    * @param propertyLabel Label of the device property as defined in Micro-manager.
    * @param allowedValues Array of allowed values.
    */
   protected MMProperty(CMMCore core, Logger logger, MMPropertyType type, String deviceLabel,
                        String propertyLabel, String[] allowedValues) {
      this.core_ = core;
      this.logger_ = logger;
      this.devicelabel_ = deviceLabel;
      this.label_ = propertyLabel;

      this.type_ = type;

      this.allowedValues = arrayFromStrings(allowedValues);

      this.readOnly = false;
      this.hasLimits = false;
      this.hasAllowedValues = true;

      hash_ = devicelabel_ + "-" + label_;

      listeners_ = new ArrayList<UIProperty>();

      getValue();
   }

   protected CMMCore getCore() {
      return core_;
   }

   /**
    * Returns the current value of the device property. This method calls Micro-Manager CMMCore.
    *
    * @return Current value.
    */
   public T getValue() {
      // ask core for value
      T val;
      try {
         val = convertToValue(core_.getProperty(devicelabel_, label_));
         value = val;
      } catch (Exception e) {
         logger_.logError("Error getting value from the MMProperty [" + hash_ + "].");
         e.printStackTrace();
      }
      return value;
   }

   /**
    * Returns the current value of the device property as a String. This method calls
    * Micro-Manager CMMCore.
    *
    * @return Current String value.
    */
   public String getStringValue() {
      // ask core for value
      String val = "";
      try {
         val = core_.getProperty(devicelabel_, label_);
         value = convertToValue(val);

         logger_.logDebugMessage("Retrieved MMProperty [" + hash_ + "] value: [" + val + "].");
      } catch (Exception e) {
         logger_.logError("Error getting value from the MMProperty [" + hash_ + "].");
         e.printStackTrace();
      }
      return val;
   }

   /**
    * Sets the value of the device property and updates all parent UIProperties. This method is
    * called by a parent UIProperty, and the source is excluded from the notification (using
    * {@link #notifyListeners(UIProperty, String)}). The method calls Micro-Manager CMMCore.
    *
    * @param stringval New value.
    * @param source    UIProperty at the origin of the update.
    * @return True if the value was set, false otherwise.
    */
   public boolean setValue(String stringval, UIProperty source) {
      if (!isReadOnly()) {
         if (stringval == null) {
            return false;
         }

         T val = convertToValue(stringval);
         if (isAllowed(val)) {
            try {
               // set value
               value = val;

               if (core_.hasProperty(devicelabel_, label_)) {
                  core_.setProperty(devicelabel_, label_, stringval);
                  notifyListeners(source, stringval);
                  logger_.logDebugMessage(
                        "Set MMProperty [" + hash_ + "] value: [" + stringval + "].");
                  return true;
               }
            } catch (Exception e) {
               logger_.logError("Error setting value of the MMProperty [" + hash_ + "].");
               e.printStackTrace();
            }
         } else {
            logger_.logDebugMessage(
                  "MMProperty [" + hash_ + "] value cannot be set to [" + stringval
                        + "], forbidden value.");
         }
      }
      return false;
   }

   /**
    * Checks if the MMProperty has limits.
    *
    * @return True if the device property has limits, false otherwise.
    */
   public boolean hasLimits() {
      return hasLimits;
   }

   /**
    * Checks if the Micro-manager property has allowed values.
    *
    * @return True if the device property has allowed values, false otherwise.
    */
   public boolean hasAllowedValues() {
      return hasAllowedValues;
   }

   /**
    * Checks if the Micro-manager property is read-only.
    *
    * @return True if the device property is read only, false otherwise.
    */
   public boolean isReadOnly() {
      return readOnly;
   }


   /**
    * Returns the array of allowed values. This array can be null if the MMProperty has
    * no allowed value.
    *
    * @return Allowed values, null if it has none.
    */
   public T[] getAllowedValues() {
      if (hasAllowedValues()) {
         return allowedValues;
      }
      return null;
   }

   /**
    * Returns the array of allowed values as Strings. In particular, the array is of size 0 if
    * it has no allowed value.
    *
    * @return Allowed values as Strings, array of size 0 if it has none.
    */
   public String[] getStringAllowedValues() {
      if (hasAllowedValues()) {
         String[] s = new String[allowedValues.length];
         for (int i = 0; i < allowedValues.length; i++) {
            s[i] = convertToString(allowedValues[i]);
         }
         return s;
      }
      return new String[0];
   }

   /**
    * Returns the maximum value as a String. If the device property doesn't have limits, then
    * the method returns null.
    *
    * @return Maximum value, or null if the device property doesn't have limits.
    */
   public String getStringMax() {
      if (hasLimits()) {
         return convertToString(maxValue);
      }
      return null;
   }

   /**
    * Returns the minimum value has a String. If the device property doesn't have limits, then
    * the method returns null.
    *
    * @return Minimum value, or null if the device property doesn't have limits.
    */
   public String getStringMin() {
      if (hasLimits()) {
         return convertToString(minValue);
      }
      return null;
   }

   /**
    * Returns the maximum value. If the device property doesn't have limits, then
    * the method returns null.
    *
    * @return Maximum value, or null if the device property doesn't have limits.
    */
   public T getMax() {
      if (hasLimits()) {
         return maxValue;
      }
      return null;
   }

   /**
    * Returns the minimum value. If the device property doesn't have limits, then
    * the method returns null.
    *
    * @return Minimum value, or null if the device property doesn't have limits.
    */
   public T getMin() {
      if (hasLimits()) {
         return minValue;
      }
      return null;
   }

   /**
    * Returns the parent device label.
    *
    * @return Device label.
    */
   public String getDeviceLabel() {
      return devicelabel_;
   }

   /**
    * Returns the device property label.
    *
    * @return Property label.
    */
   public String getMMPropertyLabel() {
      return label_;
   }

   /**
    * Returns the MMProperty hash, which is "{device label}-{property label}" (without brackets).
    *
    * @return Property's hash
    */
   public String getHash() {
      return hash_;
   }

   /**
    * Adds a UIProperty listener to the list of listeners. The UIProperty is then notified when
    * the value of the MMProperty is changed.
    *
    * @param listener Parent UIProperty
    */
   public void addListener(UIProperty listener) {
      if (listener == null) {
         throw new NullPointerException("MMProperty listeners cannot be null");
      }
      listeners_.add(listener);
   }

   /**
    * Clear all listeners from the property. Used upon reloading the UI or changing
    * the configuration.
    */
   public void clearAllListeners() {
      listeners_.clear();
   }

   /**
    * Notifies the UIProperty parents of a value update, excluding the UIProperty source of the
    * update.
    *
    * @param source UIProperty that triggered the update.
    * @param value  Updated value.
    */
   protected void notifyListeners(UIProperty source, String value) {
      for (int i = 0; i < listeners_.size(); i++) {
         if (listeners_.get(i) != source) {
            listeners_.get(i).mmPropertyHasChanged(value);
         }
      }
   }

   /**
    * Updates all listeners with the current MMProperty value.
    */
   public void updateMMProperty() {
      notifyListeners(null, getStringValue());
   }

   /**
    * Convert the String s to the MMProperty type.
    *
    * @param s String to be converted.
    * @return Converted value
    */
   protected abstract T convertToValue(String s);

   /**
    * Convert the integer {@code i} to the MMProperty type.
    *
    * @param i Integer to be converted
    * @return Converted value
    */
   protected abstract T convertToValue(int i);

   /**
    * Convert the double d to the MMProperty type.
    *
    * @param d Double to be converted
    * @return Converted value
    */
   protected abstract T convertToValue(double d);

   /**
    * Converts an array of String to the MMProperty type.
    *
    * @param s Array to be converted
    * @return Converted array
    */
   protected abstract T[] arrayFromStrings(String[] s);

   /**
    * Converts a value from the MMProperty type to String.
    *
    * @param val Value to be converted
    * @return Converted String
    */
   protected abstract String convertToString(T val);

   /**
    * Tests if equal two values are equal.
    *
    * @param val1 First value
    * @param val2 Second value
    * @return True if equal, false otherwise.
    */
   protected abstract boolean areEquals(T val1, T val2);

   /**
    * Tests if {@code val} is allowed. If the property has neither limits nor allowed values,
    * then the method should return true.
    *
    * @param val Value to be checked
    * @return True if allowed, false otherwise.
    */
   protected abstract boolean isAllowed(T val);


   /**
    * Checks if the String {@code val} is allowed.
    *
    * @param val String to check
    * @return True if {@code val} is allowed, false otherwise.
    */
   public boolean isStringAllowed(String val) {
      if (val == null) {
         return false;
      }

      return isAllowed(convertToValue(val));
   }

   /**
    * Returns the property type, "Float", "Integer", "String", "Undef" or "Config".
    *
    * @return property type.
    */
   public MMPropertyType getType() {
      return type_;
   }

   /**
    * MMProperty type.
    *
    * @author Joran Deschamps
    */
   public enum MMPropertyType {
      INTEGER("Integer"), STRING("String"), FLOAT("Float"), CONFIG("Config"), UNDEF("Undef"),
      NONE("None");

      private final String value;

      MMPropertyType(String value) {
         this.value = value;
      }

      @Override
      public String toString() {
         return value;
      }
   }

}
