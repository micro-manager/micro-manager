///////////////////////////////////////////////////////////////////////////////
//FILE:          DirectionalDevice.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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

package org.micromanager.asidispim.Utils;

import org.micromanager.asidispim.Data.Devices;

/**
 * Simple class that holds information about the name of the device
 * (as one of the static Strings defined in the Devices class, and the 
 * desired directionality (X or Y)
 * @author Nico
 */
public class DirectionalDevice {
   public static enum NumberOfAxis {ZERO, ONE, TWO};
   
   String deviceName_;
   Labels.Directions dir_;
   NumberOfAxis nrAxis_;
   
   /**
    * 
    * @param deviceName name under which the device is known in 
    * Devices.TIGERDEVICES
    * @param dir 
    */
   public DirectionalDevice(String deviceName, Labels.Directions dir) {     
      // What to do when deviceName is illegal?  deviceName really should be an enum
      deviceName_ = deviceName;
      dir_ = dir;
      if (inArray (Devices.ONEAXISTIGERDEVICES, deviceName_)) {
         nrAxis_ = NumberOfAxis.ONE;
      } else if (inArray (Devices.TWOAXISTIGERDEVICES, deviceName_)) {
            nrAxis_ = NumberOfAxis.TWO;
      } else {
         nrAxis_ = NumberOfAxis.ZERO;
      }
   }
   
   public String getDeviceName() {
      return deviceName_;
   }
   
   /**
    * Appends "-X" or "-Y" to the devicename
    * @return 
    */
   public String getFancyName() {
      return deviceName_ + "-" + dir_.toString();
   }
   
   public Labels.Directions getDir() {
      return dir_;
   }
   
   public NumberOfAxis getNrOfAxis() {
      return nrAxis_;
   }
   
   private boolean inArray (String[] array, String val) {
      for (String test : array) {
         if (val.equals(test)) {
            return true;
         }
      }
      return false;
   }
   
}
