///////////////////////////////////////////////////////////////////////////////
//FILE:          MMListenerInterface.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu, August 23, 2010
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
//

package org.micromanager.api;

/*
 * Provides a way to submit callbacks for various hardware events in
 * Micro-Manager.
 */
public interface MMListenerInterface{
   /*
    * Implement this callback when any property has changed.
    */
   public void propertiesChangedAlert();

   /*
    * Implement this callback to be alerted when each property changes.
    */
   public void propertyChangedAlert(String device, String property, String value);

   /*
    * Implement this callback to be informed of a new value in a
    * configuration group.
    */
   public void configGroupChangedAlert(String groupName, String newConfig);

   /**
    * This function will be called when a configuration file is loaded
    * It can be used to stay informed of changes in the hardware configuration
    * Note that devices can be loaded an unloaded individually, in which case
    * this function will not be called
    */
   public void systemConfigurationLoaded();
   
   /*
    * Implement this callback to be informed when the current pixel size
    * changes.
    */
   public void pixelSizeChangedAlert(double newPixelSizeUm);

   /*
    * Implement this callback to be informed when a one-axis
    * drive (e.g. focus drive) has moved.
    */
   public void stagePositionChangedAlert(String deviceName, double pos);

   /*
    * Implement this callback to be informed when an XY stage has moved.
    */
   public void xyStagePositionChanged(String deviceName, double xPos, double yPos);
   
   /**
    * Implement to be notified that the exposure time given camera has changed
    * @param cameraName - label of camera whose exposure changed
    * @param newExposureTime - new exposure time
    */
   public void exposureChanged(String cameraName, double newExposureTime);

   /**
    * Implement to be notified that the exposure time given camera has changed
    * @param slmName - label of SLM/Galvo whose exposure changed
    * @param newExposureTime - new exposure time
    */
   public void slmExposureChanged(String slmName, double newExposureTime);
}

