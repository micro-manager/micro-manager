///////////////////////////////////////////////////////////////////////////////
//FILE:          DevicesSelector.java
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

import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.DeviceUtils;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.MyDialogUtils;

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
   private final CMMCore core_;

   private final static int maxSelectorWidth = 110;
   
   /**
    * Constructs the GUI Panel that lets the user specify which device to use
    * @param gui - hook to MMstudioMainFrame script interface
    * @param devices - instance of class that holds information about devices
    * @param props
    */
   public DevicesPanel(ScriptInterface gui, Devices devices, Properties props) {
      super(MyStrings.PanelNames.DEVICES.toString(), 
            new MigLayout(
              "",
              "[right]15[center, " + maxSelectorWidth + "!]16[center, "
              + maxSelectorWidth + "!]8[]8[]",
              "[]12[]"));
      devices_ = devices;
      core_ = gui.getMMCore();
      
      DeviceUtils du = new DeviceUtils(gui, devices, props);
      
      // turn off listeners while we build the panel
      devices_.enableListeners(false);

      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.XYSTAGE) + ":"));
      final JComboBox boxXY_ = du.makeDeviceSelectionBox(mmcorej.DeviceType.XYStageDevice,
            Devices.Keys.XYSTAGE, maxSelectorWidth*2); 
      add(boxXY_, "span 2, center, wrap");
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.LOWERZDRIVE) + ":"));
      final JComboBox boxLowerZ_ = du.makeDeviceSelectionBox(mmcorej.DeviceType.StageDevice,
            Devices.Keys.LOWERZDRIVE, maxSelectorWidth*2);
      add(boxLowerZ_, "span 2, center, wrap");
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.UPPERZDRIVE) + ":"));
      final JComboBox boxUpperZ_ = du.makeDeviceSelectionBox(mmcorej.DeviceType.StageDevice,
            Devices.Keys.UPPERZDRIVE, maxSelectorWidth*2);
      add(boxUpperZ_, "span 2, center, wrap");
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.PLOGIC) + ":"));
      final JComboBox boxPLogic_ = du.makeDeviceSelectionBox(mmcorej.DeviceType.ShutterDevice,
            Devices.Keys.PLOGIC, maxSelectorWidth*2);
      add(boxPLogic_, "span 2, center, wrap");
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.CAMERALOWER) + ":"));
      final JComboBox boxLowerCam_ = du.makeSingleCameraDeviceBox(Devices.Keys.CAMERALOWER,
            maxSelectorWidth*2);
      add(boxLowerCam_, "span 2, center, wrap");
      
      add(new JLabel("Imaging Path A"), "skip 1");
      add(new JLabel("Imaging Path B"), "wrap");
      
      JLabel label = new JLabel(devices_.getDeviceDisplayGeneric(Devices.Keys.GALVOA) + ":");
      label.setToolTipText("Should be the first two axes on the MicroMirror card, usually AB");
      add (label);
      final JComboBox boxScannerA_ = du.makeDeviceSelectionBox(mmcorej.DeviceType.GalvoDevice,
            Devices.Keys.GALVOA, maxSelectorWidth);
      add(boxScannerA_);
      final JComboBox boxScannerB_ = du.makeDeviceSelectionBox(mmcorej.DeviceType.GalvoDevice,
            Devices.Keys.GALVOB, maxSelectorWidth);
      add(boxScannerB_, "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayGeneric(Devices.Keys.PIEZOA) + ":"));
      final JComboBox boxPiezoA_ = du.makeDeviceSelectionBox(mmcorej.DeviceType.StageDevice,
            Devices.Keys.PIEZOA, maxSelectorWidth);
      
      add(boxPiezoA_);
      final JComboBox boxPiezoB_ = du.makeDeviceSelectionBox(mmcorej.DeviceType.StageDevice,
            Devices.Keys.PIEZOB, maxSelectorWidth);
      add(boxPiezoB_, "wrap");

      add(new JLabel("Camera:"));
      final JComboBox boxCameraA_ = du.makeSingleCameraDeviceBox(Devices.Keys.CAMERAA, maxSelectorWidth);
      add(boxCameraA_);
      final JComboBox boxCameraB_ = du.makeSingleCameraDeviceBox(Devices.Keys.CAMERAB, maxSelectorWidth);
      add(boxCameraB_, "wrap");
      
      add(new JLabel("Note: plugin must be restarted for some changes to take full effect."), "span 3");

      add(new JSeparator(JSeparator.VERTICAL), "growy, cell 3 0 1 11");
      
      JLabel imgLabel = new JLabel(new ImageIcon(getClass().getResource("/org/micromanager/asidispim/icons/diSPIM.png")));
      add(imgLabel, "cell 4 0 1 11, growy");
      
      // look for devices that we don't have selectors for
      // in this case we also don't try to read device names from preferences
      
      // look for multi camera device
      devices_.setMMDevice(Devices.Keys.MULTICAMERA, "");
      StrVector strvDevices = core_.getLoadedDevicesOfType(mmcorej.DeviceType.CameraDevice);
      for (int i = 0; i < strvDevices.size(); i++) {
         // find all Multi-camera devices (strange to be more than one; just grab the first)
         String test = strvDevices.get(i);
         try {
            if (core_.getDeviceLibrary(test).equals(Devices.Libraries.UTILITIES.toString()) &&
                  core_.getDeviceDescription(test).equals("Combine multiple physical cameras into a single logical camera")) {
               devices_.setMMDevice(Devices.Keys.MULTICAMERA, test);
            }
         } catch (Exception e) {
            MyDialogUtils.showError("Ran into troubles looking for multi camera.");
         }
      }
      
      // look for TigerComm device
      devices_.setMMDevice(Devices.Keys.TIGERCOMM, "");
      strvDevices = core_.getLoadedDevicesOfType(mmcorej.DeviceType.HubDevice);
      for (int i = 0; i < strvDevices.size(); i++) {
         // find all TigerComm devices (strange to be more than one, just grab the first)
         String test = strvDevices.get(i);
         try {
            if (core_.getDeviceLibrary(test).equals(Devices.Libraries.ASITIGER.toString()) &&
                  core_.getDeviceDescription(test).equals("ASI TigerComm Hub (TG-1000)")) {
               devices_.setMMDevice(Devices.Keys.TIGERCOMM, test);
            }
         } catch (Exception e) {
            MyDialogUtils.showError("Ran into troubles looking for TigerComm.");
         }
      }
      
      
      // turn on listeners again
      devices_.enableListeners(true);
      
   }//constructor
   
   
   
}
