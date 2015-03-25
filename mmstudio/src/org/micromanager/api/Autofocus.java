///////////////////////////////////////////////////////////////////////////////
//FILE:          Autofocus.java
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


package org.micromanager.api;

import ij.process.ImageProcessor;
import org.micromanager.utils.MMException;
import org.micromanager.utils.PropertyItem;

/*
 * This interface is used for a java-based autofocus plugin. Properly
 * compiled jars can be added to the mmautofocus directory of Micro-Manager
 * and they will be loaded into the autofocus menu. It also wraps
 * autofocus devices implemented in C++ and exposed by the core, so both
 * java and C++ based autofocus plugins can be used by this common interface.
 */
public interface Autofocus {

   public void applySettings();
   public void saveSettings();

   /*
    * Provides the autofocus plugin with a reference to the Micro-Manager GUI.
    */
   public void setApp(ScriptInterface app);

   /*
    * Run a full, one-shot autofocus protocol. Blocks until focusing is
    * finished.
    */
   public double fullFocus() throws MMException;

   /*
    * Run a single, incremental focusing step.
    */
   public double incrementalFocus() throws MMException;
   
   /*
    * Returns the number of images acquired
    */
   public int getNumberOfImages();

   /*
    * Returns a detailed status of the autofocus plugin/device.
    */
   public String getVerboseStatus();

   /*
    * Returns an array of the properties for this autofocus plugin.
    */
   public PropertyItem[] getProperties();

   /*
    * Returns an array of the names of properties for this autofocus plugin.
    */
   public String[] getPropertyNames();

   /*
    * Returns the name and value of properties for the autofocus plugin.
    */
   public PropertyItem getProperty(String key) throws MMException;

   /*
    * Sets the value of a particular property.
    */
   public void setProperty(PropertyItem p) throws MMException;

   /*
    * Gets the value of a named property.
    */
   public String getPropertyValue(String name) throws MMException;

   /*
    * Sets the value of a named property.
    */
   public void setPropertyValue(String name, String value) throws MMException;

   /*
    * Returns the current focus "score" (goodness of focus).
    */
   public double getCurrentFocusScore();

   /*
    * Returns the name of the autofocus device.
    */
   public String getDeviceName();

   /*
    * Turns on continuous autofocus. Typically used by hardware autofocus
    * devices such as the Nikon Perfect Focus (PFS).
    */
   public void enableContinuousFocus(boolean enable) throws MMException;

   /*
    * Returns true if continuous autofocus has been enabled. Typically used
    * by hardware autofocus devices such as the Nikon Perfect Focus (PFS).
    */
   public boolean isContinuousFocusEnabled() throws MMException;

   /*
    * Returns true if continuous autofocus is currently locked (successfully
    * following the specimen). Typically used by hardware autofocus devices
    * such as the Nikon Perfect Focus (PFS).
    */
   public boolean isContinuousFocusLocked() throws MMException;
   
   /**
    * Computes a focus score for the given image
    * @param impro
    * @return calculated score
    */
   public double computeScore(final ImageProcessor impro);

   /**
    * OBSOLETE - do not use this method.
    */
   public void focus(double coarseStep, int numCoarse, double fineStep, int numFine) throws MMException;
   
}
