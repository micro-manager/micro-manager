///////////////////////////////////////////////////////////////////////////////
// FILE:          DevicesSelector.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     ASIdiSPIM plugin
// -----------------------------------------------------------------------------
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

import org.micromanager.asidispim.data.Devices;
import org.micromanager.asidispim.data.MyStrings;
import org.micromanager.asidispim.data.Properties;
import org.micromanager.asidispim.utils.DeviceUtils;
import org.micromanager.asidispim.utils.ListeningJPanel;
import org.micromanager.asidispim.utils.MyDialogUtils;

import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSeparator;

import mmcorej.CMMCore;
import mmcorej.StrVector;

import net.miginfocom.swing.MigLayout;

import org.micromanager.Studio;

/**
 * Draws the Devices tab in the ASI diSPIM GUI
 *
 * @author nico
 * @author Jon
 */
@SuppressWarnings("serial")
public class DevicesPanel extends ListeningJPanel {
  private final Devices devices_;
  private final CMMCore core_;

  private static final int MAX_SELECTOR_WIDTH = 110;

  /**
   * Constructs the GUI Panel that lets the user specify which device to use
   *
   * @param gui - hook to MMstudioMainFrame script interface
   * @param devices - instance of class that holds information about devices
   * @param props
   */
  public DevicesPanel(Studio gui, Devices devices, Properties props) {
    super(
        MyStrings.PanelNames.DEVICES.toString(),
        new MigLayout(
            "",
            "[right]15[center, "
                + MAX_SELECTOR_WIDTH
                + "!]16[center, "
                + MAX_SELECTOR_WIDTH
                + "!]8[]8[]",
            "[]12[]"));
    devices_ = devices;
    core_ = gui.core();

    DeviceUtils du = new DeviceUtils(gui, devices, props);

    // turn off listeners while we build the panel
    devices_.enableListeners(false);

    super.add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.XYSTAGE) + ":"));
    final JComboBox boxXY_ =
        du.makeDeviceSelectionBox(
            mmcorej.DeviceType.XYStageDevice, Devices.Keys.XYSTAGE, MAX_SELECTOR_WIDTH * 2);
    super.add(boxXY_, "span 2, center, wrap");

    super.add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.LOWERZDRIVE) + ":"));
    final JComboBox boxLowerZ_ =
        du.makeDeviceSelectionBox(
            mmcorej.DeviceType.StageDevice, Devices.Keys.LOWERZDRIVE, MAX_SELECTOR_WIDTH * 2);
    super.add(boxLowerZ_, "span 2, center, wrap");

    super.add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.UPPERZDRIVE) + ":"));
    final JComboBox boxUpperZ_ =
        du.makeDeviceSelectionBox(
            mmcorej.DeviceType.StageDevice, Devices.Keys.UPPERZDRIVE, MAX_SELECTOR_WIDTH * 2);
    super.add(boxUpperZ_, "span 2, center, wrap");

    if (ASIdiSPIM.OSPIM) {
      super.add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.UPPERHDRIVE) + ":"));
      final JComboBox boxUpperH_ =
          du.makeDeviceSelectionBox(
              mmcorej.DeviceType.StageDevice, Devices.Keys.UPPERHDRIVE, MAX_SELECTOR_WIDTH * 2);
      super.add(boxUpperH_, "span 2, center, wrap");
    }

    super.add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.PLOGIC) + ":"));
    final JComboBox boxPLogic_ =
        du.makeDeviceSelectionBox(
            mmcorej.DeviceType.ShutterDevice, Devices.Keys.PLOGIC, MAX_SELECTOR_WIDTH * 2);
    super.add(boxPLogic_, "span 2, center, wrap");

    super.add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.CAMERALOWER) + ":"));
    final JComboBox boxLowerCam_ =
        du.makeSingleCameraDeviceBox(Devices.Keys.CAMERALOWER, MAX_SELECTOR_WIDTH * 2);
    super.add(boxLowerCam_, "span 2, center, wrap");

    add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.SHUTTERLOWER) + ":"));
    final JComboBox boxLowerShutter_ =
        du.makeDeviceSelectionBox(
            mmcorej.DeviceType.ShutterDevice, Devices.Keys.SHUTTERLOWER, MAX_SELECTOR_WIDTH * 2);
    add(boxLowerShutter_, "span 2, center, wrap");

    super.add(new JLabel("Imaging Path A"), "skip 1");
    super.add(new JLabel("Imaging Path B"), "wrap");

    JLabel label = new JLabel(devices_.getDeviceDisplayGeneric(Devices.Keys.GALVOA) + ":");
    label.setToolTipText("Should be the first two axes on the MicroMirror card, usually AB");
    super.add(label);
    final JComboBox boxScannerA_ =
        du.makeDeviceSelectionBox(
            mmcorej.DeviceType.GalvoDevice, Devices.Keys.GALVOA, MAX_SELECTOR_WIDTH);
    super.add(boxScannerA_);
    final JComboBox boxScannerB_ =
        du.makeDeviceSelectionBox(
            mmcorej.DeviceType.GalvoDevice, Devices.Keys.GALVOB, MAX_SELECTOR_WIDTH);
    if (!ASIdiSPIM.OSPIM) {
      super.add(boxScannerB_, "wrap");
    } else {
      boxScannerB_.setSelectedIndex(0); // clear setting
      super.add(new JLabel(""));
    }

    super.add(new JLabel(devices_.getDeviceDisplayGeneric(Devices.Keys.PIEZOA) + ":"));
    final JComboBox boxPiezoA_ =
        du.makeDeviceSelectionBox(
            mmcorej.DeviceType.StageDevice, Devices.Keys.PIEZOA, MAX_SELECTOR_WIDTH);
    super.add(boxPiezoA_);
    final JComboBox boxPiezoB_ =
        du.makeDeviceSelectionBox(
            mmcorej.DeviceType.StageDevice, Devices.Keys.PIEZOB, MAX_SELECTOR_WIDTH);
    if (!ASIdiSPIM.OSPIM) {
      super.add(boxPiezoB_, "wrap");
    } else {
      boxPiezoB_.setSelectedIndex(0); // clear setting
      super.add(new JLabel(""), "wrap");
    }

    super.add(new JLabel("Camera:"));
    final JComboBox boxCameraA_ =
        du.makeSingleCameraDeviceBox(Devices.Keys.CAMERAA, MAX_SELECTOR_WIDTH);
    super.add(boxCameraA_);
    final JComboBox boxCameraB_ =
        du.makeSingleCameraDeviceBox(Devices.Keys.CAMERAB, MAX_SELECTOR_WIDTH);
    if (!ASIdiSPIM.OSPIM) {
      super.add(boxCameraB_, "wrap");
    } else {
      boxCameraB_.setSelectedIndex(0); // clear setting
      super.add(new JLabel(""));
    }

    super.add(
        new JLabel("Note: plugin must be restarted for some changes to take full effect."),
        "span 3");

    super.add(new JSeparator(JSeparator.VERTICAL), "growy, cell 3 0 1 11");

    JLabel imgLabel =
        new JLabel(
            new ImageIcon(getClass().getResource("/org/micromanager/asidispim/icons/diSPIM.png")));
    super.add(imgLabel, "cell 4 0 1 11, growy");

    // look for devices that we don't have selectors for
    // in this case we also don't try to read device names from preferences

    // look for multi camera device
    devices_.setMMDevice(Devices.Keys.MULTICAMERA, "");
    StrVector strvDevices = core_.getLoadedDevicesOfType(mmcorej.DeviceType.CameraDevice);
    for (int i = 0; i < strvDevices.size(); i++) {
      // find all Multi-camera devices (strange to be more than one; just grab the first)
      String test = strvDevices.get(i);
      try {
        if (core_.getDeviceLibrary(test).equals(Devices.Libraries.UTILITIES.toString())
            && core_
                .getDeviceDescription(test)
                .equals("Combine multiple physical cameras into a single logical camera")) {
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
        if (core_.getDeviceLibrary(test).equals(Devices.Libraries.ASITIGER.toString())
            && core_.getDeviceDescription(test).equals("ASI TigerComm Hub (TG-1000)")) {
          devices_.setMMDevice(Devices.Keys.TIGERCOMM, test);
        }
      } catch (Exception e) {
        MyDialogUtils.showError("Ran into troubles looking for TigerComm.");
      }
    }

    // turn on listeners again
    devices_.enableListeners(true);
  } // constructor
}
