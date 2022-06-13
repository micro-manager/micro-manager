///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
//
// COPYRIGHT:    University of California, San Francisco, 2010
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.


package org.micromanager;

import ij.process.ImageProcessor;
import org.micromanager.internal.utils.PropertyItem;

/**
 * This interface is used for a java-based autofocus plugin. Properly
 * compiled jars can be added to the mmplugins directory of Micro-Manager
 * and they will be loaded into the autofocus menu. It also wraps
 * autofocus devices implemented in C++ and exposed by the core, so both
 * java and C++ based autofocus plugins can be used by this common interface.
 */
public interface AutofocusPlugin extends MMPlugin {

   /**
    * Initializes the plugin.  Can be called multiple times, for instance
    * after a change in hardware configuration. Conversely, the plugin
    * can trust that the hardware never changes without a call to this function.
    */
   void initialize();

   /**
    * Pushes setting to the hardware or software autofocus.
    */
   void applySettings();

   /**
    * Stores settings.
    */
   void saveSettings();

   /**
    * Runs a full, one-shot autofocus protocol. Blocks until focusing is
    * finished.
    *
    * @return focus score
    * @throws java.lang.Exception Mainly exceptions coming from the hardware.
    */
   double fullFocus() throws Exception;

   /**
    * Runs a single, incremental focusing step.
    *
    * @return focus score
    * @throws java.lang.Exception Mainly exceptions coming from the hardware.
    */
   double incrementalFocus() throws Exception;

   /**
    * Returns the number of images acquired.
    *
    * @return number of images for autofocussing
    */
   int getNumberOfImages();

   /**
    * Returns a detailed status of the autofocus plugin/device.
    *
    * @return description of the autofocus Status
    */
   String getVerboseStatus();

   /**
    * Returns an array of the properties for this autofocus plugin.
    *
    * @return array with Property descriptors, representing MMCore data
    */
   PropertyItem[] getProperties();

   /**
    * Returns an array of the names of properties for this autofocus plugin.
    *
    * @return array with the names of properties for this autofocus plugin
    */
   String[] getPropertyNames();

   /**
    * Returns the name and value of properties for the autofocus plugin.
    *
    * @param key PropertyName for which we want the value
    * @return value for given property
    * @throws java.lang.Exception thrown by MMCore when key is not found.
    */
   PropertyItem getProperty(String key) throws Exception;

   /**
    * Sets the value of a particular property.
    *
    * @param p Property to be set
    * @throws java.lang.Exception Not sure what this may be
    */
   void setProperty(PropertyItem p) throws Exception;

   /**
    * Gets the value of a named property.
    *
    * @param name named property
    * @return value for the given property
    * @throws java.lang.Exception Not sure what this may indicate
    */
   String getPropertyValue(String name) throws Exception;

   /**
    * Sets the value of a named property.
    *
    * @param name  PropertyName
    * @param value PropertyValue
    * @throws java.lang.Exception by MMCore
    */
   void setPropertyValue(String name, String value) throws Exception;

   /**
    * Returns the current focus "score" (goodness of focus).
    *
    * @return focus score (goodness of focus)
    */
   double getCurrentFocusScore();

   /**
    * Turns on continuous autofocus. Typically used by hardware autofocus
    * devices such as the Nikon Perfect Focus (PFS).
    *
    * @param enable Switch on when true, off otherwise.
    * @throws java.lang.Exception Hardware may throw and exception
    */
   void enableContinuousFocus(boolean enable) throws Exception;

   /**
    * Returns true if continuous autofocus has been enabled. Typically used
    * by hardware autofocus devices such as the Nikon Perfect Focus (PFS).
    *
    * @return true if enabled, false otherwise
    * @throws java.lang.Exception May be thrown by the hardware
    */
   boolean isContinuousFocusEnabled() throws Exception;

   /**
    * Returns true if continuous autofocus is currently locked (successfully
    * following the specimen). Typically used by hardware autofocus devices
    * such as the Nikon Perfect Focus (PFS).
    *
    * @return true if locked
    * @throws java.lang.Exception thrown by MMCore
    */
   boolean isContinuousFocusLocked() throws Exception;

   /**
    * Computes a focus score for the given image.
    *
    * @param impro ImageProcessor to be used in the calculation.
    * @return calculated score
    */
   double computeScore(final ImageProcessor impro);
}
