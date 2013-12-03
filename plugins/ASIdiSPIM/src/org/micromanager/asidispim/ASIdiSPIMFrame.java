///////////////////////////////////////////////////////////////////////////////
//FILE:          ASIdiSPIMFrame.java
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

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import net.miginfocom.swing.MigLayout;
import org.micromanager.api.MMListenerInterface;
import org.micromanager.api.ScriptInterface;

/**
 *
 * @author nico
 */
public class ASIdiSPIMFrame extends javax.swing.JFrame  
      implements MMListenerInterface {
   
   private ScriptInterface gui_;
   private CMMCore core_;
   private Devices devices_;
   
   private JComboBox cameraBoxA_;
   private JComboBox cameraBoxB_;
   private JComboBox piezoBoxA_;
   private JComboBox piezoBoxB_;
   private JComboBox microMirrorBoxA_;
   private JComboBox microMirrorBoxB_;
   
   /**
    * Creates the ASIdiSPIM plugin frame
    * @param gui - Micro-Manager script interface
    */
   public ASIdiSPIMFrame(ScriptInterface gui)  {
      gui_ = gui;
      core_ = gui_.getMMCore();
      devices_ = new Devices();
      
      JTabbedPane tabbedPane = new JTabbedPane();
           
      JComponent devicesPanel = new JPanel(new MigLayout(
              "", 
              "[right]8[]8[]",
              "[]8[]"));
      devicesPanel.add(new JLabel(" ", null, JLabel.CENTER));
      devicesPanel.add(new JLabel("Side A"), "align center");
      devicesPanel.add(new JLabel("Side B"),  "align center, wrap");
      
      devicesPanel.add(new JLabel("Camera: ", null, JLabel.RIGHT));
      cameraBoxA_ = makeDeviceBox(mmcorej.DeviceType.CameraDevice);
      cameraBoxA_.addActionListener(new actionListenerImpl(Devices.CAMERAA,
              cameraBoxA_) );
      devicesPanel.add(cameraBoxA_);
      cameraBoxB_ = makeDeviceBox(mmcorej.DeviceType.CameraDevice);
      devicesPanel.add(cameraBoxB_, "wrap");
      
      devicesPanel.add(new JLabel("Imaging Piezo: ", null, JLabel.RIGHT));
      piezoBoxA_ = makeDeviceBox(mmcorej.DeviceType.StageDevice);
      devicesPanel.add(piezoBoxA_);
      piezoBoxB_ = makeDeviceBox(mmcorej.DeviceType.StageDevice);
      devicesPanel.add(piezoBoxB_, "wrap");
      
      devicesPanel.add(new JLabel("Sheet MicroMirror: ", null, JLabel.RIGHT));
      microMirrorBoxA_ = makeDeviceBox(mmcorej.DeviceType.GalvoDevice);
      devicesPanel.add(microMirrorBoxA_);
      microMirrorBoxB_ = makeDeviceBox(mmcorej.DeviceType.GalvoDevice);
      devicesPanel.add(microMirrorBoxB_, "wrap");
      
      devicesPanel.add(new JLabel("Fast axis: "));
      devicesPanel.add(makeXYBox(), "split 2");
      devicesPanel.add(new JCheckBox("Reverse"));
      devicesPanel.add(makeXYBox(), "split 2");
      devicesPanel.add(new JCheckBox("Reverse"), "wrap");
      
      devicesPanel.add(new JLabel("Anti-striping MicroMirror: ", null, JLabel.RIGHT));
      devicesPanel.add(makeDeviceBox(mmcorej.DeviceType.GalvoDevice));
      devicesPanel.add(makeDeviceBox(mmcorej.DeviceType.GalvoDevice), "wrap");
      
      devicesPanel.add(new JLabel("Anti-striping axis: "));
      devicesPanel.add(makeXYBox());
      devicesPanel.add(makeXYBox(), "wrap");
      
      JComponent p2 = makeTextPanel("hello");
      
      tabbedPane.addTab("Devices", devicesPanel);
      tabbedPane.addTab("Allignment", p2);
      
      add(tabbedPane);
      
      addWindowListener(new java.awt.event.WindowAdapter() {
         @Override
         public void windowClosing(java.awt.event.WindowEvent evt) {
            devices_.saveSettings();
         }
      });
      
      pack();
          
   }

   class actionListenerImpl implements ActionListener {
      String device_;
      JComboBox box_;

      public actionListenerImpl(String device, JComboBox box) {
         device_ = device;
         box_ = box;
      }

      public void actionPerformed(ActionEvent ae) {
         devices_.putInfo(device_, (String) box_.getSelectedItem());
      }
   };
   
      
   private JComboBox makeDeviceBox(mmcorej.DeviceType deviceType) {
      StrVector strvDevices = core_.getLoadedDevicesOfType(deviceType);
      ArrayList<String> devices = new ArrayList(Arrays.asList(strvDevices.toArray()));
      devices.add(0, "");
      
      JComboBox deviceBox = new JComboBox(devices.toArray());
      
      return deviceBox;
   }
   
   private JComboBox makeXYBox() {
      String[] xy = {"X", "Y"};
      return new JComboBox(xy);
   }
   
   private JComponent makeTextPanel(String text) {
        JPanel panel = new JPanel(false);
        JLabel filler = new JLabel(text);
        filler.setHorizontalAlignment(JLabel.CENTER);
        panel.setLayout(new GridLayout(1, 1));
        panel.add(filler);
        return panel;
    }
   

   public void propertiesChangedAlert() {
      }

   public void propertyChangedAlert(String device, String property, String value) {
         }

   public void configGroupChangedAlert(String groupName, String newConfig) {
         }

   public void systemConfigurationLoaded() {
         }

   public void pixelSizeChangedAlert(double newPixelSizeUm) {
         }

   public void stagePositionChangedAlert(String deviceName, double pos) {
        }

   public void xyStagePositionChanged(String deviceName, double xPos, double yPos) {
         }

   public void exposureChanged(String cameraName, double newExposureTime) {
         }

}
