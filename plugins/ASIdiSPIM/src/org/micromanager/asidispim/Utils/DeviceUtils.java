///////////////////////////////////////////////////////////////////////////////
//FILE:          DeviceUtils.java
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

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JOptionPane;

import mmcorej.CMMCore;
import mmcorej.StrVector;

import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Properties;

/**
 * @author Jon
 */
public class DeviceUtils {
   private final CMMCore core_;
   private final Devices devices_;
   private final Properties props_;
   
   public DeviceUtils(ScriptInterface gui, Devices devices, Properties props) {  // prefs needed?
      core_ = gui.getMMCore();
      devices_ = devices;
      props_ = props;
   }
   
   /**
    * Runs all device checks (for now device library and firmware version checks)
    * @param devKey
    */
   public final void doDeviceChecks(Devices.Keys devKey) {
      checkDeviceLibrary(devKey);
      checkFirmwareVersion(devKey);
   }
   

   /**
    * checks firmware versions and gives any necessary warnings to user
    * @param key
    */
   private void checkFirmwareVersion(Devices.Keys key) {
      // firmware version check only for ASI devices
      if (!devices_.isTigerDevice(key)) {
         return;
      }
      float firmwareVersion = props_.getPropValueFloat(key, Properties.Keys.FIRMWARE_VERSION);
      switch (key) {
         case PIEZOA:
         case PIEZOB:
            if (firmwareVersion == 0f) {
               // firmware version property wasn't found, maybe device hasn't been selected
            } else if (firmwareVersion < (float) 2.829) {
               MyDialogUtils.showError("Device " + devices_.getMMDevice(key)
                       + ": Piezo firmware is old; piezo may not move correctly in sync with sheet."
                       + " Get updated firmware on http://dispim.org/ or contact ASI.");
            }
            break;
         case GALVOA:
         case GALVOB:
            if (firmwareVersion == 0f) {
               // firmware version property wasn't found, maybe device hasn't been selected
            } else if (firmwareVersion < (float) 2.809) {
               MyDialogUtils.showError("Device " + devices_.getMMDevice(key)
                       + ": Micromirror firmware is old; wheel control of some scanner axes may not work."
                       + " Get updated firmware on http://dispim.org/ or contact ASI.");
            } else if (firmwareVersion < (float) 2.829) {
               MyDialogUtils.showError("Device " + devices_.getMMDevice(key)
                       + ": Micromirror firmware is old; imaging piezo not set correctly the first stack."
                       + " Get updated firmware on http://dispim.org/ or contact ASI.");
            } else if (firmwareVersion < (float) 2.859) {
               MyDialogUtils.showError("Device " + devices_.getMMDevice(key)
                       + ": Micromirror firmware is old; not all timing parameters are supported."
                       + " Get updated firmware on http://dispim.org/ or contact ASI.");
            } else if (firmwareVersion < (float) 2.889) {
               MyDialogUtils.showError("Device " + devices_.getMMDevice(key)
                     + ": Micromirror firmware is old; some properties used by plugin aren't present."
                     + " Get updated firmware on http://dispim.org/ or contact ASI.");
            } else if (firmwareVersion < (float) 3.139) {
               MyDialogUtils.showError("Device " + devices_.getMMDevice(key)
                     + ": Micromirror firmware is old, scan settings for acquisition and live mode not handled properly."
                     + " Get updated firmware on http://dispim.org/ or contact ASI.");
            }
            break;
         case PLOGIC:
            if (firmwareVersion < 3.089) {
               MyDialogUtils.showError("Device " + devices_.getMMDevice(key)
                     + ": PLogic firmware is old; some features may not work."
                     + " Get updated firmware on http://dispim.org/ or contact ASI.");
            }
            break;
         default:
            break;
      }
   }

   private void checkPropertyValueEquals(Devices.Keys devKey, Properties.Keys propKey,
         Properties.Values expectedValue) {
      if (! (props_.getPropValueString(devKey, propKey)                  
            .equals(expectedValue.toString()))) {
         if (MyDialogUtils.getConfirmDialogResult(
               "This plugin may not work if property \"" + propKey.toString() +
               "\" of device \"" + devices_.getMMDevice(devKey) + "\" is not set to \"" +
               expectedValue.toString() + "\".  Set it now?" ,
               JOptionPane.YES_NO_OPTION)) {
            props_.setPropValue(devKey, propKey, expectedValue);
         }
      }
   }
   
   private void checkPropertyExists(Devices.Keys devKey, Properties.Keys propKey) {
      if (! devices_.hasProperty(devKey, propKey) ) {
         MyDialogUtils.showError("Device \"" + devices_.getMMDevice(devKey) + 
               "\" doesn't have required property \"" + propKey.toString() + "\"");
      }
   }
   
   /**
    * checks that the device has correct library, properties that we need,
    *  and correct values set as needed
    * @param key
    */
   private void checkDeviceLibrary(Devices.Keys key) {
      Devices.Libraries deviceLibrary = devices_.getMMDeviceLibrary(key);
      if (deviceLibrary == Devices.Libraries.NODEVICE) {
         return;
      }
      switch (key) {
      case CAMERAA:
      case CAMERAB:
         switch (deviceLibrary) {
         case HAMCAM:
            checkPropertyExists(key, Properties.Keys.TRIGGER_SOURCE);
            checkPropertyExists(key, Properties.Keys.TRIGGER_ACTIVE);
            checkPropertyExists(key, Properties.Keys.SCAN_MODE);
            checkPropertyExists(key, Properties.Keys.SENSOR_MODE);
            checkPropertyValueEquals(key, Properties.Keys.TRIGGER_POLARITY, Properties.Values.POSITIVE);
            break;
         case PCOCAM:
            // trigger polarity not accessible in Micro-Manager, so we have to trust it is correct
            checkPropertyExists(key, Properties.Keys.TRIGGER_MODE_PCO);
            break;
         case ANDORCAM:
            // TODO check trigger polarity
            checkPropertyExists(key, Properties.Keys.TRIGGER_MODE);
            checkPropertyExists(key, Properties.Keys.PIXEL_READOUT_RATE);
            break;
         case DEMOCAM:
            checkPropertyValueEquals(key, Properties.Keys.PIXEL_TYPE, Properties.Values.SIXTEENBIT);
            checkPropertyExists(key, Properties.Keys.CAMERA_SIZE_X);
            checkPropertyExists(key, Properties.Keys.CAMERA_SIZE_Y);
            break;
         case PVCAM:
            checkPropertyExists(key, Properties.Keys.TRIGGER_MODE);
            checkPropertyExists(key, Properties.Keys.CAMERA_X_DIMENSION);
            checkPropertyExists(key, Properties.Keys.CAMERA_Y_DIMENSION);
            checkPropertyExists(key, Properties.Keys.PVCAM_CLEARING_MODE);
            if (props_.getPropValueString(key, Properties.Keys.PVCAM_CHIPNAME).equals(Properties.Values.PRIME_95B_CHIPNAME)) {
               checkPropertyExists(key, Properties.Keys.PVCAM_EXPOSURE_TIME);
               checkPropertyExists(key, Properties.Keys.PVCAM_PRE_TIME);
            }
            break;
         default:
            MyDialogUtils.showError("Plugin doesn't support your camera for SPIM yet;"
                  + " contact the authors for support (camera must have hardware trigger)");
         } // CamA/B case
         break;
      case GALVOA:
      case GALVOB:
         if (deviceLibrary == Devices.Libraries.ASITIGER) {
            checkPropertyValueEquals(key, Properties.Keys.INPUT_MODE, Properties.Values.INTERNAL_INPUT);
            // PLogic use in the plugin assumes "laser + side" output mode
            if (devices_.isValidMMDevice(Devices.Keys.PLOGIC)) {
               checkPropertyValueEquals(key, Properties.Keys.LASER_OUTPUT_MODE, Properties.Values.LASER_SHUTTER_SIDE);
            }
         } else {
            MyDialogUtils.showError("Plugin doesn't support galvo devices other than ASITiger");
         }
         break;
      case PIEZOA:
      case PIEZOB:
         if (deviceLibrary == Devices.Libraries.ASITIGER) {
            checkPropertyValueEquals(key, Properties.Keys.PIEZO_MODE, Properties.Values.INTERNAL_CLOSEDLOOP_INPUT);
         } else {
            MyDialogUtils.showError("Plugin doesn't support piezo devices other than ASITiger");
         }
         break;
      case PLOGIC:
         if (deviceLibrary == Devices.Libraries.ASITIGER) {
            // would like to do below line but we need to change pre-init value and reload config
            // checkPropertyValueEquals(key, Properties.Keys.PLOGIC_MODE, Properties.Values.DISPIM_SHUTTER);
            if (!props_.getPropValueString(key, Properties.Keys.PLOGIC_MODE)
                  .equals(Properties.Values.DISPIM_SHUTTER.toString())) {
               MyDialogUtils.showError("Device " + devices_.getMMDevice(key)
                     + ": need to set pre-initialization property PLogicMode to "
                     + "diSPIM Shutter (use Hardware Config Wizard, then edit device "
                     + devices_.getMMDevice(key) + " on Step 2). Then reload the "
                     + " changed configuration and restart the diSPIM plugin.");
            }
            checkPropertyValueEquals(key, Properties.Keys.PLOGIC_TRIGGER_SOURCE, Properties.Values.PLOGIC_TRIGGER_MMIRROR);
            // PLogic use in the plugin assumes "laser + side" output mode
            for (Devices.Keys galvoKey : Devices.GALVOS) {
               if (devices_.isValidMMDevice(galvoKey)) {
                  checkPropertyValueEquals(galvoKey, Properties.Keys.LASER_OUTPUT_MODE, Properties.Values.LASER_SHUTTER_SIDE);   
               }
            }
         } else {
            MyDialogUtils.showError("Plugin doesn't support shutter devices other than ASITiger");
         }
         break;
      default:
         break;
      }
   }
   
   // was nested class in makeDeviceBox, needed to move back out for makeDualCameraDeviceBox()
   // TODO clean up
   class DeviceBoxListener implements ActionListener {
      Devices.Keys key_;
      JComboBox box_;

      public DeviceBoxListener(Devices.Keys key, JComboBox box) {
         key_ = key;
         box_ = box;
      }
      
      public void actionPerformed(ActionEvent ae) {
         devices_.setMMDevice(key_, (String) box_.getSelectedItem());
         doDeviceChecks(key_);
      }
   };
   
   /**
    * Constructs a JComboBox populated with devices of specified Micro-Manager type
    * Attaches a listener and sets selected item to what is specified in the Devices
    * class.
    * 
    * @param deviceType - Micro-Manager device type (mmcorej.DeviceType)
    * @param deviceKey - ASi diSPIM device key (see Devices class)
    * @param maximumWidth -
    * @return final JComboBox
    */
   public JComboBox makeDeviceSelectionBox(mmcorej.DeviceType deviceType,
         Devices.Keys deviceKey, int maximumWidth) {
      // when editing this method do the same to the one with array argument too
      JComboBox deviceBox = new JComboBox();
      ArrayList<String> devices = new ArrayList<String>();
      StrVector strvDevices = core_.getLoadedDevicesOfType(deviceType);
      devices.addAll(Arrays.asList(strvDevices.toArray()));
      devices.add(0, "");
      deviceBox.removeAllItems();
      for (String device : devices) {
         deviceBox.addItem(device);
      }
      deviceBox.addActionListener(new DeviceBoxListener(deviceKey, deviceBox));
      deviceBox.setSelectedItem(devices_.getMMDevice(deviceKey));  // selects whatever device was read in by prefs
      deviceBox.setMaximumSize(new Dimension(maximumWidth, 30));
      return deviceBox;
   }
   
   /**
    * Constructs a special JComboBox with all cameras that have only 1 channel
    * @param deviceName
    * @return
    */
   public JComboBox makeSingleCameraDeviceBox(Devices.Keys deviceName, int maximumWidth) {
      List<String> singleCameras = new ArrayList<String>();
      singleCameras.add(0, "");
      String originalCamera = core_.getCameraDevice();
      try {
         StrVector strvDevices = core_.getLoadedDevicesOfType(mmcorej.DeviceType.CameraDevice);
         for (int i = 0; i < strvDevices.size(); i++) {
            String test = strvDevices.get(i);
            core_.setCameraDevice(test);
            if (core_.getNumberOfCameraChannels() == 1) {
               singleCameras.add(test);
            }
         }
      } catch (Exception ex) {
         MyDialogUtils.showError("Error detecting single camera devices");
      } finally {
         try {
            core_.setCameraDevice(originalCamera);
         } catch (Exception e) {
            MyDialogUtils.showError(e);
         }
      }
      
      JComboBox deviceBox = new JComboBox(singleCameras.toArray());
      deviceBox.addActionListener(new DeviceBoxListener(deviceName, deviceBox));
      deviceBox.setSelectedItem(devices_.getMMDevice(deviceName));  // selects whatever device was read in by prefs
      deviceBox.setMaximumSize(new Dimension(maximumWidth, 30));
      return deviceBox;
   }
   
}
