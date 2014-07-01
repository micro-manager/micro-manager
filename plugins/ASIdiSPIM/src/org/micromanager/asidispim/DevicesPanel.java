///////////////////////////////////////////////////////////////////////////////
//FILE:          DevicesPanel.java
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

package org.micromanager.asidispim;

import org.micromanager.utils.ReportingUtils;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSeparator;

import mmcorej.CMMCore;
import mmcorej.StrVector;

import net.miginfocom.swing.MigLayout;

import org.micromanager.api.ScriptInterface;


/**
 * Draws the Devices tab in the ASI diSPIM GUI
 * @author nico
 * @author Jon
 */
@SuppressWarnings("serial")
public class DevicesPanel extends ListeningJPanel {
   private final Devices devices_;
   private final Properties props_;
   private final CMMCore core_;
   
   private final JComboBox boxXY_;
   private final JComboBox boxLowerZ_;
   private final JComboBox boxUpperZ_;
   private final JComboBox boxLowerCam_;
   private final JComboBox boxMultiCam_;
   private final JComboBox boxScannerA_;
   private final JComboBox boxScannerB_;
   private final JComboBox boxPiezoA_;
   private final JComboBox boxPiezoB_;
   private final JComboBox boxCameraA_;
   private final JComboBox boxCameraB_;
   
   
   /**
    * Constructs the GUI Panel that lets the user specify which device to use
    * @param gui - hook to MMstudioMainFrame script interface
    * @param devices - instance of class that holds information about devices
    */
   public DevicesPanel(ScriptInterface gui, Devices devices, Properties props) {
      super("Devices", 
            new MigLayout(
              "",
              "[right]25[align center]16[align center]",
              "[]16[]"));
      devices_ = devices;
      props_ = props;
      core_ = gui.getMMCore();
      
      // turn off listeners while we build the panel
      devices_.enableListeners(false);

      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.XYSTAGE) + ":", null, JLabel.RIGHT));
      boxXY_ = makeDeviceSelectionBox(mmcorej.DeviceType.XYStageDevice, Devices.Keys.XYSTAGE); 
      add(boxXY_, "span 2, center, wrap");
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.LOWERZDRIVE) + ":", null, JLabel.RIGHT));
      boxLowerZ_ = makeDeviceSelectionBox(mmcorej.DeviceType.StageDevice, Devices.Keys.LOWERZDRIVE);
      add(boxLowerZ_, "span 2, center, wrap");
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.UPPERZDRIVE) + ":", null, JLabel.RIGHT));
      boxUpperZ_ = makeDeviceSelectionBox(mmcorej.DeviceType.StageDevice, Devices.Keys.UPPERZDRIVE);
      add(boxUpperZ_, "span 2, center, wrap");
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.CAMERALOWER) + ":", null, JLabel.RIGHT));
      boxLowerCam_ = makeDeviceSelectionBox(mmcorej.DeviceType.CameraDevice, Devices.Keys.CAMERALOWER);
      add(boxLowerCam_, "span 2, center, wrap");
            
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.MULTICAMERA) + ":", null, JLabel.RIGHT));
      boxMultiCam_ = makeMultiCameraDeviceBox(Devices.Keys.MULTICAMERA);
      add(boxMultiCam_, "span 2, center, wrap");

      add(new JLabel("Imaging Path A"), "skip 1");
      add(new JLabel("Imaging Path B"), "wrap");
      
      JLabel label = new JLabel(devices_.getDeviceDisplayGeneric(Devices.Keys.GALVOA) + ":", null, JLabel.RIGHT);
      label.setToolTipText("Should be the first two axes on the MicroMirror card, usually AB");
      add (label);
      boxScannerA_ = makeDeviceSelectionBox(mmcorej.DeviceType.GalvoDevice, Devices.Keys.GALVOA);
      add(boxScannerA_);
      boxScannerB_ = makeDeviceSelectionBox(mmcorej.DeviceType.GalvoDevice, Devices.Keys.GALVOB);
      add(boxScannerB_, "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayGeneric(Devices.Keys.PIEZOA) + ":", null, JLabel.RIGHT));
      boxPiezoA_ = makeDeviceSelectionBox(mmcorej.DeviceType.StageDevice, Devices.Keys.PIEZOA);
      add(boxPiezoA_);
      boxPiezoB_ = makeDeviceSelectionBox(mmcorej.DeviceType.StageDevice, Devices.Keys.PIEZOB);
      add(boxPiezoB_, "wrap");

      add(new JLabel("Camera:", null, JLabel.RIGHT));
      boxCameraA_ = makeDeviceSelectionBox(mmcorej.DeviceType.CameraDevice, Devices.Keys.CAMERAA);
      add(boxCameraA_);
      boxCameraB_ = makeDeviceSelectionBox(mmcorej.DeviceType.CameraDevice, Devices.Keys.CAMERAB);
      add(boxCameraB_, "wrap");
      
      add(new JLabel("Note: plugin must be restarted for some changes to take full effect."), "span 3");

      add(new JSeparator(JSeparator.VERTICAL), "growy, cell 3 0 1 12");
      
      JLabel imgLabel = new JLabel(new ImageIcon(getClass().getResource("/org/micromanager/asidispim/icons/diSPIM.png")));
      add(imgLabel, "cell 4 0 1 12, growy");
      
      // turn on listeners again
      devices_.enableListeners(true);
      
   }//constructor
   
   
   /**
    * checks firmware versions and gives any necessary warnings to user
    * @param key
    */
   private void checkFirmwareVersion(Devices.Keys key) {
      float firmwareVersion = props_.getPropValueFloat(key, Properties.Keys.FIRMWARE_VERSION);
      switch (key) {
         case PIEZOA:
         case PIEZOB:
            if (firmwareVersion == (float) 0) {
               // firmware version property wasn't found, maybe device hasn't been selected
            } else if (firmwareVersion < (float) 2.829) {
               ReportingUtils.showError("Device " + devices_.getMMDevice(key)
                       + ": Piezo firmware is old; piezo may not move correctly in sync with sheet."
                       + " Contact ASI for updated firmware.");
            }
            break;
         case GALVOA:
         case GALVOB:
            if (firmwareVersion == (float) 0) {
               // firmware version property wasn't found, maybe device hasn't been selected
            } else if (firmwareVersion < (float) 2.809) {
               ReportingUtils.showError("Device " + devices_.getMMDevice(key)
                       + ": Micromirror firmware is old; wheel control of some scanner axes may not work."
                       + " Contact ASI for updated firmware.");
            } else if (firmwareVersion < (float) 2.829) {
               ReportingUtils.showError("Device " + devices_.getMMDevice(key)
                       + ": Micromirror firmware is old; imaging piezo not set correctly the first stack."
                       + " Contact ASI for updated firmware.");
            } else if (firmwareVersion < (float) 2.859) {
               ReportingUtils.showError("Device " + devices_.getMMDevice(key)
                       + ": Micromirror firmware is old; not all timing parameters are supported."
                       + " Contact ASI for updated firmware.");
            }
            break;
         default:
            break;
      }
   }

   /**
    * checks that the device library is supported and that we have correct properties
    * @param key
    */
   private void checkDeviceLibrary(Devices.Keys key) {
      Devices.Libraries deviceLibrary = devices_.getMMDeviceLibrary(key);
      switch (key) {
      case CAMERAA:
      case CAMERAB:
         switch (deviceLibrary) {
         case NODEVICE:
            // do nothing if there isn't a camera
            break;
         case HAMCAM:
            if (! devices_.hasProperty(key, Properties.Keys.TRIGGER_SOURCE) ) {
               ReportingUtils.showError("Device " + devices_.getMMDevice(key) + 
                     ": Hamamatsu device adapter doesn't have external trigger property");
            }
            if (! (props_.getPropValueString(key, Properties.Keys.TRIGGER_POLARITY)                  
                  .equals(Properties.Values.POSITIVE.toString()))) {
               ReportingUtils.showError("Device " + devices_.getMMDevice(key) + 
                     ": set TriggerPolarity property to POSITIVE for desired behavior");
            }
            break;
         case PCOCAM:
            if (! devices_.hasProperty(key, Properties.Keys.TRIGGER_MODE) ) {
               ReportingUtils.showError("Device " + devices_.getMMDevice(key) + 
                     ": PCO device adapter doesn't have external trigger property");
            }
            break;
         case ANDORCAM:
            if (! devices_.hasProperty(key, Properties.Keys.TRIGGER_MODE_ANDOR) ) {
               ReportingUtils.showError("Device " + devices_.getMMDevice(key) + 
                     ": Andor sCMOS device adapter doesn't have external trigger property");
            }
            break;
         default:
            ReportingUtils.showError("Plugin doesn't support your camera for SPIM yet;"
                  + " contact the authors for support (camera must have hardware trigger)");
            break;
         } // CamA/B case
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
         checkDeviceLibrary(key_);
         checkFirmwareVersion(key_);
      }
   };
   
   /**
    * Constructs a JComboBox populated with devices of specified Micro-Manager type
    * Attaches a listener and sets selected item to what is specified in the Devices
    * class
    * 
    * @param deviceType - Micro-Manager device type (mmcorej.DeviceType)
    * @param deviceName - ASi diSPIM device type (see Devices class)
    * @return final JComboBox
    */
   private JComboBox makeDeviceSelectionBox(mmcorej.DeviceType deviceType, Devices.Keys deviceKey) {
      
      // class DeviceBoxListener used to be here as nested class
      JComboBox deviceBox = new JComboBox();
      updateDeviceSelectionBox(deviceBox, deviceType, deviceKey);
      deviceBox.addActionListener(new DeviceBoxListener(deviceKey, deviceBox));
      deviceBox.setSelectedItem(devices_.getMMDevice(deviceKey));  // selects whatever device was read in by prefs
      return deviceBox;
   }
   
   /**
    * Updates the items in a JCombobox with the currently available devices
    * of the given type 
    * @param deviceBox - JCombox that should exist
    * @param deviceType - Micro-Manager device type (mmcorej.DeviceType)
    * @param deviceKey  - ASI disPIM device type (see Devices class)
    */
   private void updateDeviceSelectionBox(JComboBox deviceBox, mmcorej.DeviceType deviceType, Devices.Keys deviceKey) {
      StrVector strvDevices =  core_.getLoadedDevicesOfType(deviceType);
      ArrayList<String> devices = new ArrayList<String>(Arrays.asList(strvDevices.toArray()));
      devices.add(0, "");
      deviceBox.removeAllItems();
      for (String device : devices) {
         deviceBox.addItem(device);
      }
   }
   
   /**
    * Constructs a special JComboBox with all cameras that have more than 1 channel,
    * which we expect to just be the Multicamera device
    * @param deviceName
    * @return
    */
   private JComboBox makeMultiCameraDeviceBox(Devices.Keys deviceName) {
      List<String> multiCameras = new ArrayList<String>();
      multiCameras.add(0, "");
      String originalCamera = props_.getPropValueString(Devices.Keys.CORE, Properties.Keys.CAMERA); 
      try {
         StrVector strvDevices = core_.getLoadedDevicesOfType(mmcorej.DeviceType.CameraDevice);
         for (int i = 0; i < strvDevices.size(); i++) {
            // older method found all cameras with more than one channel
            // instead we look for Multicamera instances now; we expect only one
            String test = strvDevices.get(i);
            if (core_.getDeviceLibrary(test).equals(Devices.Libraries.UTILITIES.toString()) &&
                  core_.getDeviceDescription(test).equals("Combine multiple physical cameras into a single logical camera")) {
               multiCameras.add(strvDevices.get(i));
            }
         }
      } catch (Exception ex) {
         ReportingUtils.showError("Error detecting multiCamera devices");
      } finally {
         props_.setPropValue(Devices.Keys.CORE, Properties.Keys.CAMERA, originalCamera);
      }
      
      JComboBox deviceBox = new JComboBox(multiCameras.toArray());
      deviceBox.addActionListener(new DevicesPanel.DeviceBoxListener(deviceName, deviceBox));
   // if we have one and only one multi-camera then set box to it
      if (multiCameras.size() == 2){  // recall we added empty string as the first entry
         deviceBox.setSelectedIndex(1);
      } else {
         deviceBox.setSelectedItem(devices_.getMMDevice(deviceName));  // selects whatever device was read in by prefs
      }
      return deviceBox;
   }
   
   
}
