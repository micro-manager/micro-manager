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

import org.micromanager.api.ScriptInterface;

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
      EXTERNAL_START,
      EXTERNAL_BULB,
      INTERNAL;
   }
   
   public Cameras(ScriptInterface gui, Devices devices, Properties props) {
      devices_ = devices;
      props_ = props;
      gui_ = gui;
   }//constructor
   
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
      list.add(new CameraData(devices_.getDeviceDisplay(Devices.Keys.CAMERAPREVIOUS), 
            Devices.Keys.CAMERAPREVIOUS, Devices.Sides.NONE));
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
    * Switches the active camera to the desired one. Takes care of possible side effects.
    */ 
   public void setCamera(Devices.Keys key) {
      if (Devices.CAMERAS.contains(key)) {
         if (key == Devices.Keys.CAMERAPREVIOUS) {
            return;
         }
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
               gui_.showError("Failed to set Core Camera property"); 
            }
         }
      }
   }
   
   /**
    * @return device key, e.g. CAMERAA or CAMERALOWER
    */
   public Devices.Keys getCurrentCamera() {
      return currentCameraKey_;
   }
   
   /**
    * @return false if and only if a camera is not set (checks this class, not Core-Camera)
    */
   public boolean isCurrentCameraValid() {
      return !((currentCameraKey_ == null) || (currentCameraKey_ == Devices.Keys.NONE));
   }
   
   /**
    * Turns live mode on or off via core call
    */
   public void enableLiveMode(boolean enable) {
      if (enable) {
         setSPIMCameraTriggerMode(TriggerModes.INTERNAL);
      }
      gui_.enableLiveMode(enable);
   }

   /**
    * Internal use only, take care of low-level property setting to make
    *  internal or external depending on camera type (via the DeviceLibrary)
    * currently HamamatsuHam, PCO_Camera, and Andor sCMOS are supported
    * @param devKey
    * @param mode enum from this class
    */
   private void setCameraTriggerMode(Devices.Keys devKey, TriggerModes mode) {
      Devices.Libraries camLibrary = devices_.getMMDeviceLibrary(devKey);
      switch (camLibrary) {
      case HAMCAM:
         props_.setPropValue(devKey, Properties.Keys.TRIGGER_SOURCE, 
               ((mode == TriggerModes.EXTERNAL_START) ? 
                     Properties.Values.EXTERNAL : 
                     Properties.Values.INTERNAL),
               true);
//         // this mode useful for maximum speed: exposure is ended by start of next frame => requires one extra trigger pulse? 
//         props_.setPropValue(devKey, Properties.Keys.TRIGGER_ACTIVE, Properties.Values.SYNCREADOUT);
         break;
      case PCOCAM:
         props_.setPropValue(devKey, Properties.Keys.TRIGGER_MODE, 
               ((mode == TriggerModes.EXTERNAL_START) ? 
                     Properties.Values.EXTERNAL_LC : 
                     Properties.Values.INTERNAL_LC),
               true);
         break;
      case ANDORCAM:
         props_.setPropValue(devKey, Properties.Keys.TRIGGER_MODE_ANDOR, 
               ((mode == TriggerModes.EXTERNAL_START) ? 
                     Properties.Values.EXTERNAL_LC :
                     Properties.Values.INTERNAL_ANDOR),
               true);
         break;
      default: break;
      }
   }
   
   /**
    * Sets cameras A and B to external or internal mode
    * @param external
    */
   public void setSPIMCameraTriggerMode(TriggerModes mode) {
      setCameraTriggerMode(Devices.Keys.CAMERAA, mode);
      setCameraTriggerMode(Devices.Keys.CAMERAB, mode);
   }
   
   
}
