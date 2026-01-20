///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API implementation
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

package org.micromanager.data.internal;

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
import org.micromanager.data.ScopeDataUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Implementation of the ScopeDataUtils interface.
 */
public final class DefaultScopeDataUtils implements ScopeDataUtils {

   private final Studio studio_;

   public DefaultScopeDataUtils(Studio studio) {
      studio_ = studio;
   }

   // =========================================================================
   // Static Factory Methods for ApplyOptions
   // =========================================================================

   /**
    * Create default ApplyOptions (used by interface static method).
    */
   public static ApplyOptions createDefaultApplyOptions() {
      return new DefaultApplyOptions.Builder().build();
   }

   /**
    * Create ApplyOptions Builder (used by interface static method).
    */
   public static ApplyOptions.Builder createApplyOptionsBuilder() {
      return new DefaultApplyOptions.Builder();
   }

   // =========================================================================
   // Nested Implementation Classes
   // =========================================================================

   private static class DefaultPropertyEntry implements PropertyEntry {
      private final String device;
      private final String property;
      private final String value;

      DefaultPropertyEntry(String device, String property, String value) {
         this.device = device;
         this.property = property;
         this.value = value;
      }

      @Override
      public String getDevice() {
         return device;
      }

      @Override
      public String getProperty() {
         return property;
      }

      @Override
      public String getValue() {
         return value;
      }

      @Override
      public String getKey() {
         return device + "-" + property;
      }

      @Override
      public String toString() {
         return getKey() + "=" + value;
      }
   }

   private static class DefaultPropertyChange extends DefaultPropertyEntry
         implements PropertyChange {
      private final String previousValue;

      DefaultPropertyChange(String device, String property, String value, String previousValue) {
         super(device, property, value);
         this.previousValue = previousValue;
      }

      @Override
      public String getPreviousValue() {
         return previousValue;
      }

      @Override
      public String toString() {
         return getKey() + ": " + previousValue + " -> " + getValue();
      }
   }

   private static class DefaultPropertyError extends DefaultPropertyEntry implements PropertyError {
      private final String errorMessage;
      private final Exception exception;

      DefaultPropertyError(String device, String property, String value,
                           String errorMessage, Exception exception) {
         super(device, property, value);
         this.errorMessage = errorMessage;
         this.exception = exception;
      }

      @Override
      public String getErrorMessage() {
         return errorMessage;
      }

      @Override
      public Exception getException() {
         return exception;
      }

      @Override
      public String toString() {
         return getKey() + ": " + errorMessage;
      }
   }

   private static class DefaultValidationResult implements ValidationResult {
      private final List<PropertyEntry> validProperties;
      private final List<PropertyEntry> missingDevices;
      private final List<PropertyEntry> missingProperties;
      private final List<PropertyEntry> readOnlyProperties;
      private final List<PropertyEntry> preInitProperties;

      DefaultValidationResult(List<PropertyEntry> validProperties,
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

      @Override
      public boolean isFullyValid() {
         return missingDevices.isEmpty()
               && missingProperties.isEmpty()
               && readOnlyProperties.isEmpty()
               && preInitProperties.isEmpty();
      }

      @Override
      public boolean hasAnyValid() {
         return !validProperties.isEmpty();
      }

      @Override
      public List<PropertyEntry> getValidProperties() {
         return validProperties;
      }

      @Override
      public List<PropertyEntry> getMissingDevices() {
         return missingDevices;
      }

      @Override
      public List<PropertyEntry> getMissingProperties() {
         return missingProperties;
      }

      @Override
      public List<PropertyEntry> getReadOnlyProperties() {
         return readOnlyProperties;
      }

      @Override
      public List<PropertyEntry> getPreInitProperties() {
         return preInitProperties;
      }

      @Override
      public int getIssueCount() {
         return missingDevices.size() + missingProperties.size()
               + readOnlyProperties.size() + preInitProperties.size();
      }

      @Override
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

   private static class DefaultApplyOptions implements ApplyOptions {
      private final boolean strictMode;
      private final boolean skipReadOnly;
      private final boolean skipMissingDevices;
      private final boolean skipUnchanged;
      private final boolean validateFirst;
      private final Set<String> excludedKeys;
      private final Set<String> excludedDevices;

      private DefaultApplyOptions(Builder builder) {
         this.strictMode = builder.strictMode;
         this.skipReadOnly = builder.skipReadOnly;
         this.skipMissingDevices = builder.skipMissingDevices;
         this.skipUnchanged = builder.skipUnchanged;
         this.validateFirst = builder.validateFirst;
         this.excludedKeys = Collections.unmodifiableSet(new HashSet<>(builder.excludedKeys));
         this.excludedDevices = Collections.unmodifiableSet(
               new HashSet<>(builder.excludedDevices));
      }

      @Override
      public boolean isStrictMode() {
         return strictMode;
      }

      @Override
      public boolean isSkipReadOnly() {
         return skipReadOnly;
      }

      @Override
      public boolean isSkipMissingDevices() {
         return skipMissingDevices;
      }

      @Override
      public boolean isSkipUnchanged() {
         return skipUnchanged;
      }

      @Override
      public boolean isValidateFirst() {
         return validateFirst;
      }

      @Override
      public Set<String> getExcludedKeys() {
         return excludedKeys;
      }

      @Override
      public Set<String> getExcludedDevices() {
         return excludedDevices;
      }

      static class Builder implements ApplyOptions.Builder {
         private boolean strictMode = false;
         private boolean skipReadOnly = true;
         private boolean skipMissingDevices = true;
         private boolean skipUnchanged = true;
         private boolean validateFirst = true;
         private Set<String> excludedKeys = new HashSet<>();
         private Set<String> excludedDevices = new HashSet<>();

         @Override
         public Builder strictMode(boolean strict) {
            this.strictMode = strict;
            return this;
         }

         @Override
         public Builder skipReadOnly(boolean skip) {
            this.skipReadOnly = skip;
            return this;
         }

         @Override
         public Builder skipMissingDevices(boolean skip) {
            this.skipMissingDevices = skip;
            return this;
         }

         @Override
         public Builder skipUnchanged(boolean skip) {
            this.skipUnchanged = skip;
            return this;
         }

         @Override
         public Builder validateFirst(boolean validate) {
            this.validateFirst = validate;
            return this;
         }

         @Override
         public Builder excludeKey(String key) {
            this.excludedKeys.add(key);
            return this;
         }

         @Override
         public Builder excludeKeys(Set<String> keys) {
            this.excludedKeys.addAll(keys);
            return this;
         }

         @Override
         public Builder excludeDevice(String deviceLabel) {
            this.excludedDevices.add(deviceLabel);
            return this;
         }

         @Override
         public Builder excludeDevices(Set<String> deviceLabels) {
            this.excludedDevices.addAll(deviceLabels);
            return this;
         }

         @Override
         public ApplyOptions build() {
            return new DefaultApplyOptions(this);
         }
      }
   }

   private static class DefaultApplyResult implements ApplyResult {
      private final List<PropertyChange> appliedProperties;
      private final List<PropertyEntry> skippedProperties;
      private final List<PropertyError> errors;
      private final ValidationResult validationResult;

      DefaultApplyResult(List<PropertyChange> appliedProperties,
                         List<PropertyEntry> skippedProperties,
                         List<PropertyError> errors,
                         ValidationResult validationResult) {
         this.appliedProperties = Collections.unmodifiableList(appliedProperties);
         this.skippedProperties = Collections.unmodifiableList(skippedProperties);
         this.errors = Collections.unmodifiableList(errors);
         this.validationResult = validationResult;
      }

      @Override
      public boolean isSuccess() {
         return errors.isEmpty();
      }

      @Override
      public boolean isPartialSuccess() {
         return !appliedProperties.isEmpty();
      }

      @Override
      public List<PropertyChange> getAppliedProperties() {
         return appliedProperties;
      }

      @Override
      public List<PropertyEntry> getSkippedProperties() {
         return skippedProperties;
      }

      @Override
      public List<PropertyError> getErrors() {
         return errors;
      }

      @Override
      public ValidationResult getValidationResult() {
         return validationResult;
      }

      @Override
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
   // Public API Methods
   // =========================================================================

   @Override
   public String[] parseKey(String key) {
      if (key == null) {
         return null;
      }
      int idx = key.indexOf('-');
      if (idx <= 0 || idx >= key.length() - 1) {
         return null;
      }
      return new String[] {key.substring(0, idx), key.substring(idx + 1)};
   }

   @Override
   public PropertyMap configurationToPropertyMap(Configuration config) {
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

   @Override
   public ValidationResult validateScopeData(CMMCore core, PropertyMap scopeData) {
      List<PropertyEntry> validProperties = new ArrayList<>();
      List<PropertyEntry> missingDevices = new ArrayList<>();
      List<PropertyEntry> missingProperties = new ArrayList<>();
      List<PropertyEntry> readOnlyProperties = new ArrayList<>();
      List<PropertyEntry> preInitProperties = new ArrayList<>();

      if (scopeData == null || scopeData.isEmpty()) {
         return new DefaultValidationResult(validProperties, missingDevices,
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
         PropertyEntry entry = new DefaultPropertyEntry(device, property, value);

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

      return new DefaultValidationResult(validProperties, missingDevices,
            missingProperties, readOnlyProperties, preInitProperties);
   }

   @Override
   public PropertyMap filterChangedProperties(CMMCore core, PropertyMap scopeData) {
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

   @Override
   public List<PropertyChange> getChangedProperties(CMMCore core, PropertyMap scopeData) {
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
               changes.add(new DefaultPropertyChange(device, property, targetValue, currentValue));
            }
         } catch (Exception e) {
            // Skip properties we can't read
            continue;
         }
      }

      return changes;
   }

   @Override
   public PropertyMap filterReadOnlyProperties(CMMCore core, PropertyMap scopeData) {
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

   @Override
   public PropertyMap filterStateProperties(PropertyMap scopeData) {
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

   @Override
   public ApplyResult applyScopeData(PropertyMap scopeData) {
      return applyScopeData(scopeData, ApplyOptions.defaults());
   }

   @Override
   public ApplyResult applyScopeData(PropertyMap scopeData, ApplyOptions options) {
      CMMCore core = studio_.core();

      List<PropertyChange> applied = new ArrayList<>();
      List<PropertyEntry> skipped = new ArrayList<>();
      List<PropertyError> errors = new ArrayList<>();
      ValidationResult validationResult = null;

      if (scopeData == null || scopeData.isEmpty()) {
         return new DefaultApplyResult(applied, skipped, errors, null);
      }

      // Suspend live mode
      studio_.live().setSuspended(true);

      try {
         // Validate first if requested
         if (options.isValidateFirst()) {
            validationResult = validateScopeData(core, scopeData);
            if (options.isStrictMode() && !validationResult.isFullyValid()) {
               // Return early with validation result
               return new DefaultApplyResult(applied, skipped, errors, validationResult);
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
                  errors.add(new DefaultPropertyError(parts[0], parts[1],
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
            studio_.app().refreshGUIFromCache();
         } catch (Exception e) {
            ReportingUtils.logError(e, "Failed to refresh GUI");
         }

      } finally {
         // Resume live mode
         studio_.live().setSuspended(false);
      }

      return new DefaultApplyResult(applied, skipped, errors, validationResult);
   }

   // =========================================================================
   // Private Helper Methods
   // =========================================================================

   /**
    * Gets the set of currently loaded device labels.
    */
   private Set<String> getLoadedDeviceSet(CMMCore core) {
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
   private void applyProperty(CMMCore core, String key, PropertyMap scopeData,
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
      PropertyEntry entry = new DefaultPropertyEntry(device, property, targetValue);

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

      applied.add(new DefaultPropertyChange(device, property, targetValue, currentValue));
   }
}
