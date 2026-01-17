///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:        Nico Stuurman
//
// COPYRIGHT:     University of California, San Francisco, 2025
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
import mmcorej.StrVector;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;

/**
 * Utility functions for validating and applying ScopeData (PropertyMap from
 * image metadata) to hardware.
 *
 * <p>ScopeData contains device properties in "DeviceLabel-PropertyName" format
 * with String values. This class provides methods to validate that all
 * properties exist in the current hardware configuration and to apply
 * the properties to restore hardware state.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * PropertyMap scopeData = image.getMetadata().getScopeData();
 *
 * // Validate first
 * ValidationResult validation = ScopeDataUtils.validateScopeData(core, scopeData);
 * if (!validation.isFullyValid()) {
 *     ReportingUtils.logMessage("Issues: " + validation.getSummary());
 * }
 *
 * // Apply with default options
 * ApplyResult result = ScopeDataUtils.applyScopeData(studio, scopeData);
 * }</pre>
 */
public final class ScopeDataUtils {

   private ScopeDataUtils() {
      // Utility class - prevent instantiation
   }

   // =========================================================================
   // Helper Classes
   // =========================================================================

   /**
    * Represents a single property entry from ScopeData.
    */
   public static class PropertyEntry {
      private final String device;
      private final String property;
      private final String value;

      public PropertyEntry(String device, String property, String value) {
         this.device = device;
         this.property = property;
         this.value = value;
      }

      public String getDevice() {
         return device;
      }

      public String getProperty() {
         return property;
      }

      public String getValue() {
         return value;
      }

      /**
       * Returns the key in "DeviceLabel-PropertyName" format.
       */
      public String getKey() {
         return device + "-" + property;
      }

      @Override
      public String toString() {
         return getKey() + "=" + value;
      }
   }

   /**
    * Represents a property change with both previous and new values.
    */
   public static class PropertyChange extends PropertyEntry {
      private final String previousValue;

      public PropertyChange(String device, String property, String value, String previousValue) {
         super(device, property, value);
         this.previousValue = previousValue;
      }

      public String getPreviousValue() {
         return previousValue;
      }

      @Override
      public String toString() {
         return getKey() + ": " + previousValue + " -> " + getValue();
      }
   }

   /**
    * Represents a property that failed to apply with error information.
    */
   public static class PropertyError extends PropertyEntry {
      private final String errorMessage;
      private final Exception exception;

      public PropertyError(String device, String property, String value,
                           String errorMessage, Exception exception) {
         super(device, property, value);
         this.errorMessage = errorMessage;
         this.exception = exception;
      }

      public String getErrorMessage() {
         return errorMessage;
      }

      public Exception getException() {
         return exception;
      }

      @Override
      public String toString() {
         return getKey() + ": " + errorMessage;
      }
   }

   // =========================================================================
   // ValidationResult
   // =========================================================================

   /**
    * Result of validating ScopeData against current hardware configuration.
    */
   public static class ValidationResult {
      private final List<PropertyEntry> validProperties;
      private final List<PropertyEntry> missingDevices;
      private final List<PropertyEntry> missingProperties;
      private final List<PropertyEntry> readOnlyProperties;
      private final List<PropertyEntry> preInitProperties;

      private ValidationResult(List<PropertyEntry> validProperties,
                               List<PropertyEntry> missingDevices,
                               List<PropertyEntry> missingProperties,
                               List<PropertyEntry> readOnlyProperties,
                               List<PropertyEntry> preInitProperties) {
         this.validProperties = Collections.unmodifiableList(validProperties);
         this.missingDevices = Collections.unmodifiableList(missingDevices);
         this.missingProperties = Collections.unmodifiableList(missingProperties);
         this.readOnlyProperties = Collections.unmodifiableList(readOnlyProperties);
         this.preInitProperties = Collections.unmodifiableList(preInitProperties);
      }

      /**
       * Returns true if all properties in ScopeData exist and are writable.
       */
      public boolean isFullyValid() {
         return missingDevices.isEmpty()
               && missingProperties.isEmpty()
               && readOnlyProperties.isEmpty()
               && preInitProperties.isEmpty();
      }

      /**
       * Returns true if at least some properties can be applied.
       */
      public boolean hasAnyValid() {
         return !validProperties.isEmpty();
      }

      /**
       * Returns list of properties that are valid and writable.
       */
      public List<PropertyEntry> getValidProperties() {
         return validProperties;
      }

      /**
       * Returns list of properties where the device does not exist.
       */
      public List<PropertyEntry> getMissingDevices() {
         return missingDevices;
      }

      /**
       * Returns list of properties where the property name does not exist.
       */
      public List<PropertyEntry> getMissingProperties() {
         return missingProperties;
      }

      /**
       * Returns list of properties that are read-only.
       */
      public List<PropertyEntry> getReadOnlyProperties() {
         return readOnlyProperties;
      }

      /**
       * Returns list of properties that are pre-initialization only.
       */
      public List<PropertyEntry> getPreInitProperties() {
         return preInitProperties;
      }

      /**
       * Returns count of all issues.
       */
      public int getIssueCount() {
         return missingDevices.size() + missingProperties.size()
               + readOnlyProperties.size() + preInitProperties.size();
      }

      /**
       * Returns a human-readable summary of validation results.
       */
      public String getSummary() {
         if (isFullyValid()) {
            return "All " + validProperties.size() + " properties are valid";
         }

         StringBuilder sb = new StringBuilder();
         sb.append(validProperties.size()).append(" valid");

         if (!missingDevices.isEmpty()) {
            sb.append(", ").append(missingDevices.size()).append(" missing devices");
         }
         if (!missingProperties.isEmpty()) {
            sb.append(", ").append(missingProperties.size()).append(" missing properties");
         }
         if (!readOnlyProperties.isEmpty()) {
            sb.append(", ").append(readOnlyProperties.size()).append(" read-only");
         }
         if (!preInitProperties.isEmpty()) {
            sb.append(", ").append(preInitProperties.size()).append(" pre-init");
         }

         return sb.toString();
      }
   }

   // =========================================================================
   // ApplyOptions
   // =========================================================================

   /**
    * Options for controlling how ScopeData is applied to hardware.
    */
   public static class ApplyOptions {
      private final boolean strictMode;
      private final boolean skipReadOnly;
      private final boolean skipMissingDevices;
      private final boolean skipUnchanged;
      private final boolean validateFirst;
      private final Set<String> excludedKeys;
      private final Set<String> excludedDevices;

      private ApplyOptions(Builder builder) {
         this.strictMode = builder.strictMode;
         this.skipReadOnly = builder.skipReadOnly;
         this.skipMissingDevices = builder.skipMissingDevices;
         this.skipUnchanged = builder.skipUnchanged;
         this.validateFirst = builder.validateFirst;
         this.excludedKeys = Collections.unmodifiableSet(new HashSet<>(builder.excludedKeys));
         this.excludedDevices = Collections.unmodifiableSet(new HashSet<>(builder.excludedDevices));
      }

      /**
       * Returns default options: skip read-only, skip unchanged, validate first.
       */
      public static ApplyOptions defaults() {
         return new Builder().build();
      }

      /**
       * Returns a new builder for ApplyOptions.
       */
      public static Builder builder() {
         return new Builder();
      }

      /**
       * If true, stop on first error. If false, continue with remaining properties.
       */
      public boolean isStrictMode() {
         return strictMode;
      }

      /**
       * If true, skip read-only properties instead of reporting errors.
       */
      public boolean isSkipReadOnly() {
         return skipReadOnly;
      }

      /**
       * If true, skip properties for missing devices instead of reporting errors.
       */
      public boolean isSkipMissingDevices() {
         return skipMissingDevices;
      }

      /**
       * If true, skip properties whose values already match current hardware.
       */
      public boolean isSkipUnchanged() {
         return skipUnchanged;
      }

      /**
       * If true, validate before applying.
       */
      public boolean isValidateFirst() {
         return validateFirst;
      }

      /**
       * Returns set of property keys to exclude (in "DeviceLabel-PropertyName" format).
       */
      public Set<String> getExcludedKeys() {
         return excludedKeys;
      }

      /**
       * Returns set of device labels to exclude entirely.
       */
      public Set<String> getExcludedDevices() {
         return excludedDevices;
      }

      /**
       * Builder for ApplyOptions.
       */
      public static class Builder {
         private boolean strictMode = false;
         private boolean skipReadOnly = true;
         private boolean skipMissingDevices = true;
         private boolean skipUnchanged = true;
         private boolean validateFirst = true;
         private Set<String> excludedKeys = new HashSet<>();
         private Set<String> excludedDevices = new HashSet<>();

         public Builder strictMode(boolean strict) {
            this.strictMode = strict;
            return this;
         }

         public Builder skipReadOnly(boolean skip) {
            this.skipReadOnly = skip;
            return this;
         }

         public Builder skipMissingDevices(boolean skip) {
            this.skipMissingDevices = skip;
            return this;
         }

         public Builder skipUnchanged(boolean skip) {
            this.skipUnchanged = skip;
            return this;
         }

         public Builder validateFirst(boolean validate) {
            this.validateFirst = validate;
            return this;
         }

         public Builder excludeKey(String key) {
            this.excludedKeys.add(key);
            return this;
         }

         public Builder excludeKeys(Set<String> keys) {
            this.excludedKeys.addAll(keys);
            return this;
         }

         public Builder excludeDevice(String deviceLabel) {
            this.excludedDevices.add(deviceLabel);
            return this;
         }

         public Builder excludeDevices(Set<String> deviceLabels) {
            this.excludedDevices.addAll(deviceLabels);
            return this;
         }

         public ApplyOptions build() {
            return new ApplyOptions(this);
         }
      }
   }

   // =========================================================================
   // ApplyResult
   // =========================================================================

   /**
    * Result of applying ScopeData to hardware.
    */
   public static class ApplyResult {
      private final List<PropertyChange> appliedProperties;
      private final List<PropertyEntry> skippedProperties;
      private final List<PropertyError> errors;
      private final ValidationResult validationResult;

      private ApplyResult(List<PropertyChange> appliedProperties,
                          List<PropertyEntry> skippedProperties,
                          List<PropertyError> errors,
                          ValidationResult validationResult) {
         this.appliedProperties = Collections.unmodifiableList(appliedProperties);
         this.skippedProperties = Collections.unmodifiableList(skippedProperties);
         this.errors = Collections.unmodifiableList(errors);
         this.validationResult = validationResult;
      }

      /**
       * Returns true if all properties were applied successfully (no errors).
       */
      public boolean isSuccess() {
         return errors.isEmpty();
      }

      /**
       * Returns true if at least some properties were applied.
       */
      public boolean isPartialSuccess() {
         return !appliedProperties.isEmpty();
      }

      /**
       * Returns list of properties that were successfully applied.
       */
      public List<PropertyChange> getAppliedProperties() {
         return appliedProperties;
      }

      /**
       * Returns list of properties that were skipped.
       */
      public List<PropertyEntry> getSkippedProperties() {
         return skippedProperties;
      }

      /**
       * Returns list of properties that failed to apply.
       */
      public List<PropertyError> getErrors() {
         return errors;
      }

      /**
       * Returns the validation result if validateFirst was true.
       */
      public ValidationResult getValidationResult() {
         return validationResult;
      }

      /**
       * Returns a human-readable summary of apply results.
       */
      public String getSummary() {
         StringBuilder sb = new StringBuilder();
         sb.append(appliedProperties.size()).append(" applied");

         if (!skippedProperties.isEmpty()) {
            sb.append(", ").append(skippedProperties.size()).append(" skipped");
         }
         if (!errors.isEmpty()) {
            sb.append(", ").append(errors.size()).append(" errors");
         }

         return sb.toString();
      }
   }

   // =========================================================================
   // Public Methods
   // =========================================================================

   /**
    * Parses a ScopeData key into device and property name.
    *
    * @param key Key in "DeviceLabel-PropertyName" format
    * @return String array with [device, property], or null if invalid format
    */
   public static String[] parseKey(String key) {
      if (key == null) {
         return null;
      }
      int idx = key.indexOf('-');
      if (idx <= 0 || idx >= key.length() - 1) {
         return null;
      }
      return new String[] {key.substring(0, idx), key.substring(idx + 1)};
   }

   /**
    * Converts a Configuration (from core.getSystemStateCache()) to a PropertyMap
    * in the standard ScopeData format ("DeviceLabel-PropertyName" keys).
    *
    * @param config Configuration object from CMMCore
    * @return PropertyMap with device properties
    */
   public static PropertyMap configurationToPropertyMap(Configuration config) {
      if (config == null) {
         return PropertyMaps.emptyPropertyMap();
      }

      PropertyMap.Builder builder = PropertyMaps.builder();
      for (long i = 0; i < config.size(); ++i) {
         try {
            PropertySetting setting = config.getSetting(i);
            builder.putString(
                  setting.getDeviceLabel() + "-" + setting.getPropertyName(),
                  setting.getPropertyValue());
         } catch (Exception e) {
            // Skip settings that can't be read
            continue;
         }
      }
      return builder.build();
   }

   /**
    * Validates that all properties in ScopeData exist in current hardware configuration.
    *
    * @param core      The CMMCore instance
    * @param scopeData PropertyMap containing device properties
    * @return ValidationResult with categorized properties
    */
   public static ValidationResult validateScopeData(CMMCore core, PropertyMap scopeData) {
      List<PropertyEntry> validProperties = new ArrayList<>();
      List<PropertyEntry> missingDevices = new ArrayList<>();
      List<PropertyEntry> missingProperties = new ArrayList<>();
      List<PropertyEntry> readOnlyProperties = new ArrayList<>();
      List<PropertyEntry> preInitProperties = new ArrayList<>();

      if (scopeData == null || scopeData.isEmpty()) {
         return new ValidationResult(validProperties, missingDevices,
               missingProperties, readOnlyProperties, preInitProperties);
      }

      Set<String> loadedDevices = getLoadedDeviceSet(core);

      for (String key : scopeData.keySet()) {
         String[] parts = parseKey(key);
         if (parts == null) {
            // Skip malformed keys
            continue;
         }

         String device = parts[0];
         String property = parts[1];
         String value = scopeData.getString(key, "");
         PropertyEntry entry = new PropertyEntry(device, property, value);

         try {
            if (!loadedDevices.contains(device)) {
               missingDevices.add(entry);
            } else if (!core.hasProperty(device, property)) {
               missingProperties.add(entry);
            } else if (core.isPropertyReadOnly(device, property)) {
               readOnlyProperties.add(entry);
            } else if (core.isPropertyPreInit(device, property)) {
               preInitProperties.add(entry);
            } else {
               validProperties.add(entry);
            }
         } catch (Exception e) {
            // If we can't check the property, treat as missing
            missingProperties.add(entry);
         }
      }

      return new ValidationResult(validProperties, missingDevices,
            missingProperties, readOnlyProperties, preInitProperties);
   }

   /**
    * Filters ScopeData to only include properties that exist in the current
    * hardware configuration and have different values from the current state.
    *
    * <p>This is useful for determining what would actually change if the
    * ScopeData were applied, without actually applying it.</p>
    *
    * @param core      The CMMCore instance
    * @param scopeData PropertyMap containing device properties
    * @return PropertyMap containing only properties that exist and differ from current values
    */
   public static PropertyMap filterChangedProperties(CMMCore core, PropertyMap scopeData) {
      if (scopeData == null || scopeData.isEmpty()) {
         return PropertyMaps.emptyPropertyMap();
      }

      PropertyMap.Builder builder = PropertyMaps.builder();
      Set<String> loadedDevices = getLoadedDeviceSet(core);

      for (String key : scopeData.keySet()) {
         String[] parts = parseKey(key);
         if (parts == null) {
            // Skip malformed keys
            continue;
         }

         String device = parts[0];
         String property = parts[1];
         String targetValue = scopeData.getString(key, "");

         try {
            // Check if device exists
            if (!loadedDevices.contains(device)) {
               continue;
            }

            // Check if property exists
            if (!core.hasProperty(device, property)) {
               continue;
            }

            // Get current value and check if it differs
            String currentValue = core.getProperty(device, property);
            if (!currentValue.equals(targetValue)) {
               builder.putString(key, targetValue);
            }
         } catch (Exception e) {
            // Skip properties we can't read
            continue;
         }
      }

      return builder.build();
   }

   /**
    * Filters ScopeData to only include properties that exist in the current
    * hardware configuration and have different values, returning detailed
    * information about each property difference.
    *
    * @param core      The CMMCore instance
    * @param scopeData PropertyMap containing device properties
    * @return List of PropertyChange objects describing each difference
    */
   public static List<PropertyChange> getChangedProperties(CMMCore core, PropertyMap scopeData) {
      List<PropertyChange> changes = new ArrayList<>();

      if (scopeData == null || scopeData.isEmpty()) {
         return changes;
      }

      Set<String> loadedDevices = getLoadedDeviceSet(core);

      for (String key : scopeData.keySet()) {
         String[] parts = parseKey(key);
         if (parts == null) {
            // Skip malformed keys
            continue;
         }

         String device = parts[0];
         String property = parts[1];
         String targetValue = scopeData.getString(key, "");

         try {
            // Check if device exists
            if (!loadedDevices.contains(device)) {
               continue;
            }

            // Check if property exists
            if (!core.hasProperty(device, property)) {
               continue;
            }

            // Get current value and check if it differs
            String currentValue = core.getProperty(device, property);
            if (!currentValue.equals(targetValue)) {
               changes.add(new PropertyChange(device, property, targetValue, currentValue));
            }
         } catch (Exception e) {
            // Skip properties we can't read
            continue;
         }
      }

      return changes;
   }

   /**
    * Filters ScopeData to remove read-only properties.
    *
    * <p>Returns a new PropertyMap containing only properties that are writable.
    * Properties for devices that don't exist in the current configuration are
    * also removed.</p>
    *
    * @param core      The CMMCore instance
    * @param scopeData PropertyMap containing device properties
    * @return PropertyMap containing only writable properties
    */
   public static PropertyMap filterReadOnlyProperties(CMMCore core, PropertyMap scopeData) {
      if (scopeData == null || scopeData.isEmpty()) {
         return PropertyMaps.emptyPropertyMap();
      }

      PropertyMap.Builder builder = PropertyMaps.builder();
      Set<String> loadedDevices = getLoadedDeviceSet(core);

      for (String key : scopeData.keySet()) {
         String[] parts = parseKey(key);
         if (parts == null) {
            // Skip malformed keys
            continue;
         }

         String device = parts[0];
         String property = parts[1];
         String value = scopeData.getString(key, "");

         try {
            // Check if device exists
            if (!loadedDevices.contains(device)) {
               continue;
            }

            // Check if property exists
            if (!core.hasProperty(device, property)) {
               continue;
            }

            // Check if property is read-only
            if (core.isPropertyReadOnly(device, property)) {
               continue;
            }

            // Property is writable, include it
            builder.putString(key, value);
         } catch (Exception e) {
            // Skip properties we can't check
            continue;
         }
      }

      return builder.build();
   }

   /**
    * Filters ScopeData to remove "State" properties when the device also has
    * a "Label" property.
    *
    * <p>State devices (like filter wheels, objectives, etc.) typically have both
    * a numeric "State" property and a human-readable "Label" property. When both
    * are present in the ScopeData, it's preferable to use "Label" since it's
    * more meaningful and less error-prone if the state device configuration
    * has changed.</p>
    *
    * @param scopeData PropertyMap containing device properties
    * @return PropertyMap with redundant State properties removed
    */
   public static PropertyMap filterStateProperties(PropertyMap scopeData) {
      if (scopeData == null || scopeData.isEmpty()) {
         return PropertyMaps.emptyPropertyMap();
      }

      // First, find all devices that have a Label property
      Set<String> devicesWithLabel = new HashSet<>();
      for (String key : scopeData.keySet()) {
         String[] parts = parseKey(key);
         if (parts == null) {
            continue;
         }
         if ("Label".equals(parts[1])) {
            devicesWithLabel.add(parts[0]);
         }
      }

      // Now build result, excluding State properties for devices that have Label
      PropertyMap.Builder builder = PropertyMaps.builder();
      for (String key : scopeData.keySet()) {
         String[] parts = parseKey(key);
         if (parts == null) {
            // Keep malformed keys as-is
            builder.putString(key, scopeData.getString(key, ""));
            continue;
         }

         String device = parts[0];
         String property = parts[1];

         // Skip State property if this device has a Label property
         if ("State".equals(property) && devicesWithLabel.contains(device)) {
            continue;
         }

         builder.putString(key, scopeData.getString(key, ""));
      }

      return builder.build();
   }

   /**
    * Applies ScopeData to hardware with default options.
    *
    * @param studio    The Studio instance
    * @param scopeData PropertyMap containing device properties
    * @return ApplyResult with details of what was applied
    */
   public static ApplyResult applyScopeData(Studio studio, PropertyMap scopeData) {
      return applyScopeData(studio, scopeData, ApplyOptions.defaults());
   }

   /**
    * Applies ScopeData to hardware with configurable options.
    *
    * @param studio    The Studio instance
    * @param scopeData PropertyMap containing device properties
    * @param options   Options controlling apply behavior
    * @return ApplyResult with details of what was applied
    */
   public static ApplyResult applyScopeData(Studio studio, PropertyMap scopeData,
                                            ApplyOptions options) {
      CMMCore core = studio.core();

      List<PropertyChange> applied = new ArrayList<>();
      List<PropertyEntry> skipped = new ArrayList<>();
      List<PropertyError> errors = new ArrayList<>();
      ValidationResult validationResult = null;

      if (scopeData == null || scopeData.isEmpty()) {
         return new ApplyResult(applied, skipped, errors, null);
      }

      // Suspend live mode
      studio.live().setSuspended(true);

      try {
         // Validate first if requested
         if (options.isValidateFirst()) {
            validationResult = validateScopeData(core, scopeData);
            if (options.isStrictMode() && !validationResult.isFullyValid()) {
               // Return early with validation result
               return new ApplyResult(applied, skipped, errors, validationResult);
            }
         }

         Set<String> loadedDevices = getLoadedDeviceSet(core);

         // Apply each property
         for (String key : scopeData.keySet()) {
            try {
               applyProperty(core, key, scopeData, options, loadedDevices,
                     applied, skipped, errors);
            } catch (Exception e) {
               String[] parts = parseKey(key);
               if (parts != null) {
                  errors.add(new PropertyError(parts[0], parts[1],
                        scopeData.getString(key, ""), e.getMessage(), e));
               }
               if (options.isStrictMode()) {
                  break;
               }
            }
         }

         // Update system state cache
         try {
            core.updateSystemStateCache();
         } catch (Exception e) {
            ReportingUtils.logError(e, "Failed to update system state cache");
         }

         // Refresh GUI
         try {
            studio.app().refreshGUIFromCache();
         } catch (Exception e) {
            ReportingUtils.logError(e, "Failed to refresh GUI");
         }

      } finally {
         // Resume live mode
         studio.live().setSuspended(false);
      }

      return new ApplyResult(applied, skipped, errors, validationResult);
   }

   // =========================================================================
   // Private Helper Methods
   // =========================================================================

   /**
    * Gets the set of currently loaded device labels.
    */
   private static Set<String> getLoadedDeviceSet(CMMCore core) {
      Set<String> devices = new HashSet<>();
      try {
         StrVector deviceVector = core.getLoadedDevices();
         for (int i = 0; i < deviceVector.size(); i++) {
            devices.add(deviceVector.get(i));
         }
      } catch (Exception e) {
         ReportingUtils.logError(e, "Failed to get loaded devices");
      }
      return devices;
   }

   /**
    * Applies a single property from ScopeData to hardware.
    */
   private static void applyProperty(CMMCore core, String key, PropertyMap scopeData,
                                     ApplyOptions options, Set<String> loadedDevices,
                                     List<PropertyChange> applied,
                                     List<PropertyEntry> skipped,
                                     List<PropertyError> errors) throws Exception {
      String[] parts = parseKey(key);
      if (parts == null) {
         // Skip malformed keys
         return;
      }

      String device = parts[0];
      String property = parts[1];
      String targetValue = scopeData.getString(key, "");
      PropertyEntry entry = new PropertyEntry(device, property, targetValue);

      // Check exclusions
      if (options.getExcludedDevices().contains(device)
            || options.getExcludedKeys().contains(key)) {
         skipped.add(entry);
         return;
      }

      // Check if device exists
      if (!loadedDevices.contains(device)) {
         if (options.isSkipMissingDevices()) {
            skipped.add(entry);
            return;
         }
         throw new Exception("Device not found: " + device);
      }

      // Check if property exists
      if (!core.hasProperty(device, property)) {
         throw new Exception("Property not found: " + key);
      }

      // Check read-only
      if (core.isPropertyReadOnly(device, property)) {
         if (options.isSkipReadOnly()) {
            skipped.add(entry);
            return;
         }
         throw new Exception("Property is read-only: " + key);
      }

      // Check pre-init (always skip - cannot be changed at runtime)
      if (core.isPropertyPreInit(device, property)) {
         skipped.add(entry);
         return;
      }

      // Get current value and check if change is needed
      String currentValue = core.getProperty(device, property);
      if (options.isSkipUnchanged() && currentValue.equals(targetValue)) {
         skipped.add(entry);
         return;
      }

      // Apply the value
      core.setProperty(device, property, targetValue);
      core.waitForDevice(device);

      applied.add(new PropertyChange(device, property, targetValue, currentValue));
   }
}
