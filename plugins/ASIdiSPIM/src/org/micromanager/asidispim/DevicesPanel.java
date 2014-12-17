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

import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSeparator;

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
   
   private final JComboBox boxXY_;
   private final JComboBox boxLowerZ_;
   private final JComboBox boxUpperZ_;
   private final JComboBox boxPLogic_;
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
      super(MyStrings.PanelNames.DEVICES.toString(), 
            new MigLayout(
              "",
              "[right]15[align center]16[align center]",
              "[]12[]"));
      devices_ = devices;
      
      DeviceUtils du = new DeviceUtils(gui, devices, props);
      
      // turn off listeners while we build the panel
      devices_.enableListeners(false);

      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.XYSTAGE) + ":"));
      boxXY_ = du.makeDeviceSelectionBox(mmcorej.DeviceType.XYStageDevice, Devices.Keys.XYSTAGE); 
      add(boxXY_, "span 2, center, wrap");
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.LOWERZDRIVE) + ":"));
      boxLowerZ_ = du.makeDeviceSelectionBox(mmcorej.DeviceType.StageDevice, Devices.Keys.LOWERZDRIVE);
      add(boxLowerZ_, "span 2, center, wrap");
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.UPPERZDRIVE) + ":"));
      boxUpperZ_ = du.makeDeviceSelectionBox(mmcorej.DeviceType.StageDevice, Devices.Keys.UPPERZDRIVE);
      add(boxUpperZ_, "span 2, center, wrap");
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.PLOGIC) + ":"));
      boxPLogic_ = du.makeDeviceSelectionBox(mmcorej.DeviceType.GenericDevice, Devices.Keys.PLOGIC);
      add(boxPLogic_, "span 2, center, wrap");
      
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.CAMERALOWER) + ":"));
      boxLowerCam_ = du.makeSingleCameraDeviceBox(Devices.Keys.CAMERALOWER);
      add(boxLowerCam_, "span 2, center, wrap");
            
      add(new JLabel(devices_.getDeviceDisplay(Devices.Keys.MULTICAMERA) + ":"));
      boxMultiCam_ = du.makeMultiCameraDeviceBox(Devices.Keys.MULTICAMERA);
      add(boxMultiCam_, "span 2, center, wrap");

      add(new JLabel("Imaging Path A"), "skip 1");
      add(new JLabel("Imaging Path B"), "wrap");
      
      JLabel label = new JLabel(devices_.getDeviceDisplayGeneric(Devices.Keys.GALVOA) + ":");
      label.setToolTipText("Should be the first two axes on the MicroMirror card, usually AB");
      add (label);
      boxScannerA_ = du.makeDeviceSelectionBox(mmcorej.DeviceType.GalvoDevice, Devices.Keys.GALVOA);
      add(boxScannerA_);
      boxScannerB_ = du.makeDeviceSelectionBox(mmcorej.DeviceType.GalvoDevice, Devices.Keys.GALVOB);
      add(boxScannerB_, "wrap");
      
      add(new JLabel(devices_.getDeviceDisplayGeneric(Devices.Keys.PIEZOA) + ":"));
      boxPiezoA_ = du.makeDeviceSelectionBox(mmcorej.DeviceType.StageDevice, Devices.Keys.PIEZOA);
      add(boxPiezoA_);
      
      boxPiezoB_ = du.makeDeviceSelectionBox(mmcorej.DeviceType.StageDevice, Devices.Keys.PIEZOB);
      add(boxPiezoB_, "wrap");

      add(new JLabel("Camera:"));
      boxCameraA_ = du.makeSingleCameraDeviceBox(Devices.Keys.CAMERAA);
      add(boxCameraA_);
      boxCameraB_ = du.makeSingleCameraDeviceBox(Devices.Keys.CAMERAB);
      add(boxCameraB_, "wrap");
      
      add(new JLabel("Note: plugin must be restarted for some changes to take full effect."), "span 3");

      add(new JSeparator(JSeparator.VERTICAL), "growy, cell 3 0 1 11");
      
      JLabel imgLabel = new JLabel(new ImageIcon(getClass().getResource("/org/micromanager/asidispim/icons/diSPIM.png")));
      add(imgLabel, "cell 4 0 1 11, growy");
      
      // turn on listeners again
      devices_.enableListeners(true);
      
   }//constructor
   
   
   
}
