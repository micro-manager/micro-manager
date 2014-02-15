///////////////////////////////////////////////////////////////////////////////
//FILE:          Cameras.java
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

package org.micromanager.asidispim.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ReportingUtils;

/**
 * Holds utility functions for cameras
 * 
 * @author Jon
 */
public class Cameras {
   
   private final Devices devices_;   // object holding information about selected/available devices
   private final Properties props_;  // object handling all property read/writes
   private final ScriptInterface gui_;
   private Devices.Keys currentCameraKey_;
   
   public static enum TriggerModes {
      EXTERNAL,
      INTERNAL;
   }
   
   public Cameras(Devices devices, Properties props) {
      devices_ = devices;
      props_ = props;
      gui_ = MMStudioMainFrame.getInstance();
   }
   
   /**
    * associative class to store information for camera combo boxes.
    * Contains string shown in combo box, key of corresponding device
    */
   public static class CameraData {
      public String displayString;
      public Devices.Keys deviceKey;
      public Devices.Sides side;
      
      /**
       * @param displayString string used in camera drop-down
       * @param deviceKey enum from Devices.Keys for the device
       */
      public CameraData(String displayString, Devices.Keys deviceKey, Devices.Sides side) {
         this.displayString = displayString;
         this.deviceKey = deviceKey;
         this.side = side;
      }
      
      public boolean equals(CameraData a) {
         return (this.displayString.equals(a.displayString) && this.deviceKey==a.deviceKey 
               && this.side==a.side);
      }
      
      @Override
      public int hashCode() {
         return (this.displayString.hashCode() + this.deviceKey.toString().hashCode() + this.side.hashCode());
      }

      @Override
      public boolean equals(Object obj) {
         if (obj == null) {
            return false;
         }
         if (getClass() != obj.getClass()) {
            return false;
         }
         final CameraData other = (CameraData) obj;
         if ((this.displayString == null) ? (other.displayString != null) : !this.displayString.equals(other.displayString)) {
            return false;
         }
         if (this.deviceKey != other.deviceKey) {
            return false;
         }
         if (this.side != other.side) {
            return false;
         }
         return true;
      }
   }
   
   /**
    * used to generate selection list for cameras
    * @return array with with CameraData structures
    */
   public CameraData[] getCameraData() {
      List<CameraData> list = new ArrayList<CameraData>();
      list.add(new CameraData("", Devices.Keys.NONE, Devices.Sides.NONE));  // adds blank to top of list
      for (Devices.Keys devKey : Devices.CAMERAS) {
         if (devices_.getMMDevice(devKey)!=null) {
            String dispKey = devices_.getMMDevice(devKey); // getDeviceDisplay(devKey); 
            list.add(new CameraData(dispKey, devKey, Devices.getSideFromKey(devKey)));
         }
      }
      List<CameraData> noduplicates = new ArrayList<CameraData>(new LinkedHashSet<CameraData>(list));
      return noduplicates.toArray(new CameraData[0]);
   }
   
   /** 
    * Switches the active camera to the desired one. Takes care of possible side effects 
    */ 
   public void setCamera(Devices.Keys key) {
      if (Devices.CAMERAS.contains(key)) {
         String mmDevice = devices_.getMMDevice(key);
         if (mmDevice != null) {
            try {
               boolean liveEnabled = gui_.isLiveModeOn(); 
               if (liveEnabled) { 
                  enableLiveMode(false); 
               } 
               currentCameraKey_ = key;
               props_.setPropValue(Devices.Keys.CORE, Properties.Keys.CAMERA, mmDevice);
               gui_.refreshGUIFromCache(); 
               if (liveEnabled) { 
                  enableLiveMode(true);
               } 
            } catch (Exception ex) { 
               ReportingUtils.showError("Failed to set Core Camera property"); 
            }
         }
      }
   }
   
   
   public Devices.Keys getCurrentCamera() {
      return currentCameraKey_;
   }
   
   public boolean isCurrentCameraValid() {
      return !((currentCameraKey_ == null) || (currentCameraKey_ == Devices.Keys.NONE));
   }
   
   public void enableLiveMode(boolean enable) {
      if (enable) {
         setCameraTriggerExternal(false);
      }
      gui_.enableLiveMode(enable);
   }
   
   public void setCameraTriggerExternal(boolean external) {
      // set mode to external
      // have to handle any device adapters, currently HamamatsuHam and PCO only
      Devices.Libraries camLibraryA, camLibraryB;
      try {
         camLibraryA = devices_.getMMDeviceLibrary(Devices.Keys.CAMERAA);
         if (camLibraryA == Devices.Libraries.HAMCAM) {
            if (external) {
               props_.setPropValue(Devices.Keys.CAMERAA, Properties.Keys.TRIGGER_SOURCE, Properties.Values.EXTERNAL, true);
            } else {
               props_.setPropValue(Devices.Keys.CAMERAA, Properties.Keys.TRIGGER_SOURCE, Properties.Values.INTERNAL, true);
            }
         } else if (camLibraryA == Devices.Libraries.PCOCAM) {
            if (external) {
               props_.setPropValue(Devices.Keys.CAMERAA, Properties.Keys.TRIGGER_MODE, Properties.Values.EXTERNAL_LC, true);
            } else {
               props_.setPropValue(Devices.Keys.CAMERAA, Properties.Keys.TRIGGER_MODE, Properties.Values.INTERNAL_LC, true);
            }
         }
         camLibraryB = devices_.getMMDeviceLibrary(Devices.Keys.CAMERAB);
         if (camLibraryB == Devices.Libraries.HAMCAM) {
            if (external) {
               props_.setPropValue(Devices.Keys.CAMERAB, Properties.Keys.TRIGGER_SOURCE, Properties.Values.EXTERNAL, true);
            } else {
               props_.setPropValue(Devices.Keys.CAMERAB, Properties.Keys.TRIGGER_SOURCE, Properties.Values.INTERNAL, true);
            }
         } else if (camLibraryB == Devices.Libraries.PCOCAM) {
            if (external) {
               props_.setPropValue(Devices.Keys.CAMERAB, Properties.Keys.TRIGGER_MODE, Properties.Values.EXTERNAL_LC, true);
            } else {
               props_.setPropValue(Devices.Keys.CAMERAB, Properties.Keys.TRIGGER_MODE, Properties.Values.INTERNAL_LC, true);
            }
         }
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }
   
   
}
