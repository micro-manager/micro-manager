///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Acquisition API
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

package org.micromanager.acquisition;

import java.util.List;
import java.util.Set;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import org.micromanager.PropertyMap;

/**
 * Utility functions for validating and applying ScopeData (PropertyMap from
 * image metadata) to hardware.
 *
 * <p>ScopeData contains device properties in "DeviceLabel-PropertyName" format
 * with String values. This class provides methods to validate that all
 * properties exist in the current hardware configuration and to apply
 * the properties to restore hardware state.</p>
 *
 * <p>Access this utility via {@code Studio.acquisitions().scopeData()}.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ScopeDataUtils utils = studio.acquisitions().scopeData();
 * PropertyMap scopeData = image.getMetadata().getScopeData();
 *
 * // Validate first
 * ScopeDataUtils.ValidationResult validation = utils.validateScopeData(core, scopeData);
 * if (!validation.isFullyValid()) {
 *     ReportingUtils.logMessage("Issues: " + validation.getSummary());
 * }
 *
 * // Apply with default options
 * ScopeDataUtils.ApplyResult result = utils.applyScopeData(scopeData);
 * }</pre>
 */
public interface ScopeDataUtils {

   // =========================================================================
   // Nested Interfaces for Data Classes
   // =========================================================================

   /**
    * Represents a single property entry from ScopeData.
    */
   interface PropertyEntry {
      /**
       * Returns the device label.
       */
      String getDevice();

      /**
       * Returns the property name.
       */
      String getProperty();

      /**
       * Returns the property value.
       */
      String getValue();

      /**
       * Returns the key in "DeviceLabel-PropertyName" format.
       */
      String getKey();
   }

   /**
    * Represents a property change with both previous and new values.
    */
   interface PropertyChange extends PropertyEntry {
      /**
       * Returns the previous value before the change.
       */
      String getPreviousValue();
   }

   /**
    * Represents a property that failed to apply with error information.
    */
   interface PropertyError extends PropertyEntry {
      /**
       * Returns the error message.
       */
      String getErrorMessage();

      /**
       * Returns the exception that caused the error, if available.
       */
      Exception getException();
   }

   /**
    * Result of validating ScopeData against current hardware configuration.
    */
   interface ValidationResult {
      /**
       * Returns true if all properties in ScopeData exist and are writable.
       */
      boolean isFullyValid();

      /**
       * Returns true if at least some properties can be applied.
       */
      boolean hasAnyValid();

      /**
       * Returns list of properties that are valid and writable.
       */
      List<PropertyEntry> getValidProperties();

      /**
       * Returns list of properties where the device does not exist.
       */
      List<PropertyEntry> getMissingDevices();

      /**
       * Returns list of properties where the property name does not exist.
       */
      List<PropertyEntry> getMissingProperties();

      /**
       * Returns list of properties that are read-only.
       */
      List<PropertyEntry> getReadOnlyProperties();

      /**
       * Returns list of properties that are pre-initialization only.
       */
      List<PropertyEntry> getPreInitProperties();

      /**
       * Returns count of all issues.
       */
      int getIssueCount();

      /**
       * Returns a human-readable summary of validation results.
       */
      String getSummary();
   }

   /**
    * Options for controlling how ScopeData is applied to hardware.
    */
   interface ApplyOptions {
      /**
       * Returns default options: skip read-only, skip unchanged, validate first.
       */
      static ApplyOptions defaults() {
         return org.micromanager.acquisition.internal.DefaultScopeDataUtils
                  .createDefaultApplyOptions();
      }

      /**
       * Returns a new builder for ApplyOptions.
       */
      static Builder builder() {
         return org.micromanager.acquisition.internal.DefaultScopeDataUtils
                  .createApplyOptionsBuilder();
      }

      /**
       * If true, stop on first error. If false, continue with remaining properties.
       */
      boolean isStrictMode();

      /**
       * If true, skip read-only properties instead of reporting errors.
       */
      boolean isSkipReadOnly();

      /**
       * If true, skip properties for missing devices instead of reporting errors.
       */
      boolean isSkipMissingDevices();

      /**
       * If true, skip properties whose values already match current hardware.
       */
      boolean isSkipUnchanged();

      /**
       * If true, validate before applying.
       */
      boolean isValidateFirst();

      /**
       * Returns set of property keys to exclude (in "DeviceLabel-PropertyName" format).
       */
      Set<String> getExcludedKeys();

      /**
       * Returns set of device labels to exclude entirely.
       */
      Set<String> getExcludedDevices();

      /**
       * Builder for ApplyOptions.
       */
      interface Builder {
         Builder strictMode(boolean strict);

         Builder skipReadOnly(boolean skip);

         Builder skipMissingDevices(boolean skip);

         Builder skipUnchanged(boolean skip);

         Builder validateFirst(boolean validate);

         Builder excludeKey(String key);

         Builder excludeKeys(Set<String> keys);

         Builder excludeDevice(String deviceLabel);

         Builder excludeDevices(Set<String> deviceLabels);

         ApplyOptions build();
      }
   }

   /**
    * Result of applying ScopeData to hardware.
    */
   interface ApplyResult {
      /**
       * Returns true if all properties were applied successfully (no errors).
       */
      boolean isSuccess();

      /**
       * Returns true if at least some properties were applied.
       */
      boolean isPartialSuccess();

      /**
       * Returns list of properties that were successfully applied.
       */
      List<PropertyChange> getAppliedProperties();

      /**
       * Returns list of properties that were skipped.
       */
      List<PropertyEntry> getSkippedProperties();

      /**
       * Returns list of properties that failed to apply.
       */
      List<PropertyError> getErrors();

      /**
       * Returns the validation result if validateFirst was true.
       */
      ValidationResult getValidationResult();

      /**
       * Returns a human-readable summary of apply results.
       */
      String getSummary();
   }

   // =========================================================================
   // Utility Methods
   // =========================================================================

   /**
    * Parses a ScopeData key into device and property name.
    *
    * @param key Key in "DeviceLabel-PropertyName" format
    * @return String array with [device, property], or null if invalid format
    */
   String[] parseKey(String key);

   /**
    * Converts a Configuration (from core.getSystemStateCache()) to a PropertyMap
    * in the standard ScopeData format ("DeviceLabel-PropertyName" keys).
    *
    * @param config Configuration object from CMMCore
    * @return PropertyMap with device properties
    */
   PropertyMap configurationToPropertyMap(Configuration config);

   /**
    * Validates that all properties in ScopeData exist in current hardware configuration.
    *
    * @param core      The CMMCore instance
    * @param scopeData PropertyMap containing device properties
    * @return ValidationResult with categorized properties
    */
   ValidationResult validateScopeData(CMMCore core, PropertyMap scopeData);

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
   PropertyMap filterChangedProperties(CMMCore core, PropertyMap scopeData);

   /**
    * Filters ScopeData to only include properties that exist in the current
    * hardware configuration and have different values, returning detailed
    * information about each property difference.
    *
    * @param core      The CMMCore instance
    * @param scopeData PropertyMap containing device properties
    * @return List of PropertyChange objects describing each difference
    */
   List<PropertyChange> getChangedProperties(CMMCore core, PropertyMap scopeData);

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
   PropertyMap filterReadOnlyProperties(CMMCore core, PropertyMap scopeData);

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
   PropertyMap filterStateProperties(PropertyMap scopeData);

   /**
    * Applies ScopeData to hardware with default options.
    *
    * @param scopeData PropertyMap containing device properties
    * @return ApplyResult with details of what was applied
    */
   ApplyResult applyScopeData(PropertyMap scopeData);

   /**
    * Applies ScopeData to hardware with configurable options.
    *
    * @param scopeData PropertyMap containing device properties
    * @param options   Options controlling apply behavior
    * @return ApplyResult with details of what was applied
    */
   ApplyResult applyScopeData(PropertyMap scopeData, ApplyOptions options);
}
