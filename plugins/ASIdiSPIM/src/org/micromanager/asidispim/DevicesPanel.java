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

import edu.valelab.GaussianFit.utils.ReportingUtils;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import net.miginfocom.swing.MigLayout;
import org.micromanager.api.ScriptInterface;

/**
 * Draws the Devices tab in the ASI diSPIM GUI
 * @author nico
 */
public class DevicesPanel extends JPanel {
   Devices devices_;
   ScriptInterface gui_;
   
   /**
    * Constructs the GUI Panel that lets the user specify which device to use
    * @param gui - hook to MMstudioMainFrame script interface
    * @param devices - instance of class that holds information about devices
    */
   public DevicesPanel(ScriptInterface gui, Devices devices) {
      super(new MigLayout(
              "",
              "[right]8[align center]8[align center]",
              "[]8[]"));
      devices_ = devices;
      gui_ = gui;

      add(new JLabel("Side A"), "skip 1");
      add(new JLabel("Side B"), "wrap");

      add(new JLabel("Camera:", null, JLabel.RIGHT));
      add(makeDeviceBox(
              mmcorej.DeviceType.CameraDevice, Devices.CAMERAA));
      add(makeDeviceBox(
              mmcorej.DeviceType.CameraDevice, Devices.CAMERAB),
              "wrap");

      add(new JLabel("Dual Camera:", null, JLabel.RIGHT));
      add(makeDualCameraDeviceBox(Devices.DUALCAMERA), "span 2, center, wrap");

      add(new JLabel("Imaging Piezo:", null, JLabel.RIGHT));
      add(makeDeviceBox(
              mmcorej.DeviceType.StageDevice, Devices.PIEZOA));
      add(makeDeviceBox(
              mmcorej.DeviceType.StageDevice, Devices.PIEZOB), "wrap");

      add(new JLabel("Sheet MicroMirror:", null, JLabel.RIGHT));
      add(makeDeviceBox(
              mmcorej.DeviceType.GalvoDevice, Devices.GALVOA));
      add(makeDeviceBox(
              mmcorej.DeviceType.GalvoDevice, Devices.GALVOB),
              "wrap");

      add(new JLabel("Fast axis:"));
      add(makeXYBox(Devices.FASTAXISADIR), "split 2");
      add(makeReverseCheckBox(Devices.FASTAXISAREV));
      add(makeXYBox(Devices.FASTAXISBDIR), "split 2");
      add(makeReverseCheckBox(Devices.FASTAXISBREV), "wrap");

      add(new JLabel("Anti-striping MicroMirror:", null, JLabel.RIGHT));
      add(makeDeviceBox(mmcorej.DeviceType.GalvoDevice, Devices.GALVOC));
      add(makeDeviceBox(mmcorej.DeviceType.GalvoDevice, Devices.GALVOD), "wrap");

      add(new JLabel("Anti-striping axis:"));
      add(makeXYBox(Devices.FASTAXISCDIR));
      add(makeXYBox(Devices.FASTAXISDDIR), "wrap");

   }
   
   /**
    * Listener for the device JComboBox
    * Updates class Devices with any GUI changes
    * 
    */
   class DeviceBoxListener implements ActionListener {
      String device_;
      JComboBox box_;

      public DeviceBoxListener(String device, JComboBox box) {
         device_ = device;
         box_ = box;
      }

      @Override
      public void actionPerformed(ActionEvent ae) {
         devices_.putDeviceInfo(device_, (String) box_.getSelectedItem());
      }
   };
   
   
   /**
    * Constructs a JCombobox populated with devices of specified Micro-Manager type
    * Attaches a listener and sets selected item to what is specified in the Devices
    * class
    * 
    * @param deviceType - Micro-Manager device type
    * @param deviceName - ASi diSPIM device type (see Devices class)
    * @return final JCOmboBox
    */
   private JComboBox makeDeviceBox(mmcorej.DeviceType deviceType, String deviceName) {
      StrVector strvDevices = gui_.getMMCore().getLoadedDevicesOfType(deviceType);
      ArrayList<String> devices = new ArrayList<String>(Arrays.asList(strvDevices.toArray()));
      devices.add(0, "");
      
      JComboBox deviceBox = new JComboBox(devices.toArray());
      deviceBox.setSelectedItem(devices_.getDeviceInfo(deviceName));
      deviceBox.addActionListener(new DevicesPanel.DeviceBoxListener(deviceName, deviceBox));

      return deviceBox;
   }

   private JComboBox makeDualCameraDeviceBox(String deviceName) {
      List<String> multiCameras = new ArrayList<String>();
      multiCameras.add("");
      try {
         CMMCore core = gui_.getMMCore();
         StrVector strvDevices = core.getLoadedDevicesOfType(
                 mmcorej.DeviceType.CameraDevice);

         String originalCamera = core.getProperty("Core", "Camera");
         
         for (int i = 0; i < strvDevices.size(); i++) {
            String test = strvDevices.get(i);
            core.setProperty("Core", "Camera", test);
            if (core.getNumberOfCameraChannels() > 1) {
               multiCameras.add(test);
            }
         }
         core.setProperty("Core", "Camera", originalCamera);

      } catch (Exception ex) {
         ReportingUtils.showError("Error detecting multiCamera devices");
      }

      JComboBox deviceBox = new JComboBox(multiCameras.toArray());
      deviceBox.setSelectedItem(devices_.getDeviceInfo(deviceName));
      deviceBox.addActionListener(new DevicesPanel.DeviceBoxListener(deviceName, deviceBox));
      return deviceBox;

   }

   /**
    * Listener for the Axis directions combox boxes
    * Updates the model in the Devices class with any GUI changes
    */
   class AxisDirBoxListener implements ActionListener {
      String axis_;
      JComboBox box_;

      public AxisDirBoxListener(String axis, JComboBox box) {
         axis_ = axis;
         box_ = box;
      }

      @Override
      public void actionPerformed(ActionEvent ae) {
         devices_.putAxisDirInfo(axis_, (String) box_.getSelectedItem());
      }
   }
   
   /**
    * Constructs a DropDown box containing X/Y.
    * Sets selection based on info in the Devices class and attaches
    * a Listener
    * 
    * @param axis - Name under which this axis is known in the Device class
    * @return constructed JComboBox
    */
   private JComboBox makeXYBox(String axis) {
      String[] xy = {"X", "Y"};
      JComboBox jcb = new JComboBox(xy);
      jcb.setSelectedItem(devices_.getAxisDirInfo(axis));
      jcb.addActionListener(new DevicesPanel.AxisDirBoxListener(axis, jcb));
 
      return jcb;
   }
   
   /**
    * Listener for the Checkbox indicating whether this axis should be reversed
    * Updates the Devices model with GUI selections
    */
   class ReverseCheckBoxListener implements ActionListener {
      String axis_;
      JCheckBox box_;

      public ReverseCheckBoxListener(String axis, JCheckBox box) {
         axis_ = axis;
         box_ = box;
      }

      @Override
      public void actionPerformed(ActionEvent ae) {
         devices_.putFastAxisRevInfo(axis_, box_.isSelected());
      }
   };
   
   /**
    * Constructs the JCheckBox through which the user can set the direction of
    * the sheet
    * @param fastAxisDir name under which this axis is known in the Devices class
    * @return constructed JCheckBox
    */
   private JCheckBox makeReverseCheckBox(String fastAxisDir) {
      JCheckBox jc = new JCheckBox("Reverse");
      jc.setSelected(devices_.getFastAxisRevInfo(fastAxisDir));
      jc.addActionListener(new DevicesPanel.ReverseCheckBoxListener(fastAxisDir, jc));
      
      return jc;
   }

   
}
