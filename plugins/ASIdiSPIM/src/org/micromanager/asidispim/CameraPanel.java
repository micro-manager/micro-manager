///////////////////////////////////////////////////////////////////////////////
//FILE:          CameraPanel.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Jon Daniels
//
// COPYRIGHT:    Applied Scientific Instrumentation (ASI), 2017
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

import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;

import net.miginfocom.swing.MigLayout;

import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Data.CameraModes;
import org.micromanager.asidispim.Data.Cameras;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;


/**
 *
 * @author jon
 */
@SuppressWarnings("serial")
public class CameraPanel extends ListeningJPanel{
   final private Devices devices_;
   final private Properties props_;
   final private Prefs prefs_;
   final private Cameras cameras_;
   final private JComboBox camModeCB_;
   
   
   public CameraPanel(final ScriptInterface gui, final Devices devices, 
           final Properties props, final Prefs prefs, 
           final Cameras cameras) {
      
      super(MyStrings.PanelNames.CAMERAS.toString(),
              new MigLayout(
              "",
              "[center]8[center]",
              "[]16[]16[]"));
      devices_ = devices;
      props_ = props;
      prefs_ = prefs;
      cameras_ = cameras;
      
      PanelUtils pu = new PanelUtils(prefs_, props_, devices_);

      // start ROI panel
      final JPanel roiPanel = new JPanel(new MigLayout(
            "",
            "[center]8[center]",
            "[]8[]"));
      roiPanel.setBorder(PanelUtils.makeTitledBorder("SPIM Imaging ROI"));
      
      final String roiPrefNode = panelName_;
      final String roiPrefKey = "ROI_Code";
      final JToggleButton roiUnchanged = makeRoiButton(RoiPresets.UNCHANGED, roiPrefNode, roiPrefKey);
      roiUnchanged.setMargin(new Insets(4, 12, 4, 12));
      final Insets inset = new Insets(4, 8, 4, 8);
      final JToggleButton roiFull = makeRoiButton(RoiPresets.FULL, roiPrefNode, roiPrefKey);
      roiFull.setMargin(inset);
      final JToggleButton roiHalf = makeRoiButton(RoiPresets.HALF, roiPrefNode, roiPrefKey);
      roiHalf.setMargin(inset);
      final JToggleButton roiQuarter = makeRoiButton(RoiPresets.QUARTER, roiPrefNode, roiPrefKey);
      roiQuarter.setMargin(inset);
      final JToggleButton roiEighth = makeRoiButton(RoiPresets.EIGHTH, roiPrefNode, roiPrefKey);
      roiEighth.setMargin(inset);
      final JToggleButton roiCustom = makeRoiButton(RoiPresets.CUSTOM, roiPrefNode, roiPrefKey);
      roiCustom.setMargin(new Insets(4, 20, 4, 20));
      
      ButtonGroup roiGroup = new ButtonGroup();
      roiGroup.add(roiUnchanged);
      roiGroup.add(roiFull);
      roiGroup.add(roiHalf);
      roiGroup.add(roiQuarter);
      roiGroup.add(roiEighth);
      roiGroup.add(roiCustom);
      
      roiPanel.add(roiUnchanged, "span 2, center, wrap");
      roiPanel.add(roiFull);
      roiPanel.add(roiHalf, "wrap");
      roiPanel.add(roiQuarter);
      roiPanel.add(roiEighth, "wrap");
      roiPanel.add(roiCustom, "span 2, center, wrap");
      
      final JFormattedTextField offsetX = pu.makeIntEntryField(MyStrings.PanelNames.CAMERAS.toString(), "OffsetX", 0, 3);
      final JFormattedTextField offsetY = pu.makeIntEntryField(MyStrings.PanelNames.CAMERAS.toString(), "OffsetY", 0, 3);
      
      final JFormattedTextField width = pu.makeIntEntryField(MyStrings.PanelNames.CAMERAS.toString(), "Width", 0, 3);
      final JFormattedTextField height = pu.makeIntEntryField(MyStrings.PanelNames.CAMERAS.toString(), "Height", 0, 3);
      
      roiPanel.add(new JLabel("X offset:"), "right");
      roiPanel.add(offsetX, "left, wrap");
      roiPanel.add(new JLabel("Y offset:"), "right");
      roiPanel.add(offsetY, "left, wrap");
      roiPanel.add(new JLabel("Width:"), "right");
      roiPanel.add(width, "left, wrap");
      roiPanel.add(new JLabel("Height:"), "right");
      roiPanel.add(height, "left, wrap");
      
      JButton getCurrentRoi = new JButton("Get Current ROI");
      getCurrentRoi.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            Devices.Keys camKey = ASIdiSPIM.getFrame().getAcquisitionPanel().isFirstSideA() ? Devices.Keys.CAMERAA : Devices.Keys.CAMERAB;
            Rectangle roi = cameras_.getCameraROI(camKey);
            offsetX.setValue(roi.x);
            offsetY.setValue(roi.y);
            height.setValue(roi.height);
            width.setValue(roi.width);
         }
      });
      getCurrentRoi.setMargin(new Insets(4, 4, 4, 4));
      getCurrentRoi.setFocusable(false);
      roiPanel.add(getCurrentRoi, "span 2, center");
      
      // only enable custom fields when custom button is selected
      
      final JComponent[] customRoiComponents = {offsetX, offsetY, height, width};
      PanelUtils.componentsSetEnabled(customRoiComponents, roiCustom.isSelected());
      
      ActionListener disableCustomAL = new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            PanelUtils.componentsSetEnabled(customRoiComponents, false); 
         }
      };
      
      roiUnchanged.addActionListener(disableCustomAL);
      roiFull.addActionListener(disableCustomAL);
      roiHalf.addActionListener(disableCustomAL);
      roiQuarter.addActionListener(disableCustomAL);
      roiEighth.addActionListener(disableCustomAL);

      roiCustom.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            PanelUtils.componentsSetEnabled(customRoiComponents, roiCustom.isSelected()); 
         }
      });
      
      // set the ROI when we first launch the plugin
      final int prefCode = prefs_.getInt(roiPrefNode, roiPrefKey, 0);
      setSPIMCameraROI(getRoiPresetFromCode(prefCode));
      
      // end ROI subpanel
      
      
      // start camera trigger mode subpanel
      
      final JPanel triggerPanel = new JPanel(new MigLayout(
            "",
            "[]16[]",
            "[]"));
      triggerPanel.setBorder(PanelUtils.makeTitledBorder("Acq. Trigger mode"));
      CameraModes camModeObject = new CameraModes(devices_, prefs_);
      // access using pref node MyStrings.PanelNames.SETTINGS.toString() with pref key Properties.Keys.PLUGIN_CAMERA_MODE
      //     (used to be on Settings panel, now moved but pref location kept the same)
      camModeCB_ = camModeObject.getComboBox();
      triggerPanel.add(new JLabel(""));  // add extra space for layout uniformity, can remove without problem
      triggerPanel.add(camModeCB_, "wrap");
      
      // end camera trigger mode subpanel
      
      
      // start light sheet options subpanel
      final JPanel lightSheetPanel = new JPanel(new MigLayout(
            "",
            "[right]10[center]",
            "[]8[]"));
      
      lightSheetPanel.setBorder(PanelUtils.makeTitledBorder("Light Sheet Settings"));
      
      lightSheetPanel.add(new JLabel("Scan reset time [ms]:"));
      JSpinner lsScanReset = pu.makeSpinnerFloat(0, 100, 0.25,
            Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_LS_SCAN_RESET, 3);
      lsScanReset.addChangeListener(PanelUtils.coerceToQuarterIntegers(lsScanReset));
      lightSheetPanel.add(lsScanReset, "wrap");
      
      lightSheetPanel.add(new JLabel("Scan settle time [ms]:"));
      JSpinner lsScanSettle = pu.makeSpinnerFloat(0, 100, 0.25,
            Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_LS_SCAN_SETTLE, 1);
      lsScanSettle.addChangeListener(PanelUtils.coerceToQuarterIntegers(lsScanSettle));
      lightSheetPanel.add(lsScanSettle, "wrap");
      
      lightSheetPanel.add(new JLabel("Shutter width [\u00B5m]:"));
      JSpinner lsShutterWidth = pu.makeSpinnerFloat(0, 100, 1,
            Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_LS_SHUTTER_WIDTH, 5);
      lightSheetPanel.add(lsShutterWidth, "wrap");
      
      lightSheetPanel.add(new JLabel("1 / (shutter speed):"));
      JSpinner lsShutterSpeed = pu.makeSpinnerInteger(1, 10,
            Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_LS_SHUTTER_SPEED, 1);
      lightSheetPanel.add(lsShutterSpeed, "wrap");
      
      // end light sheet subpanel
      

      // disable controls specific to light sheet when light sheet isn't selected
      final JComponent[] lsComponents = {lsScanReset, lsScanSettle, lsShutterWidth, lsShutterSpeed};
      PanelUtils.componentsSetEnabled(lsComponents, getSPIMCameraMode() == CameraModes.Keys.LIGHT_SHEET);
      
      camModeCB_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            PanelUtils.componentsSetEnabled(lsComponents, getSPIMCameraMode() == CameraModes.Keys.LIGHT_SHEET); 
         }
      });
      
      // construct the main panel
      JPanel secondCol = new JPanel(new MigLayout("", "[]0[]", "[]0[]"));
      secondCol.add(triggerPanel, "growx, wrap");
      secondCol.add(lightSheetPanel, "wrap");
      add(roiPanel);
      add(secondCol, "top");
   }//constructor
   
   
   /**
    * @return CameraModes.Keys value from Settings panel
    * (internal, edge, overlap, pseudo-overlap) 
    */
   public CameraModes.Keys getSPIMCameraMode() {
      return (CameraModes.Keys) camModeCB_.getSelectedItem();
   }
   
   private JToggleButton makeRoiButton(RoiPresets roi, String prefNode, String prefKey) {

      class roiListener implements ActionListener {
         private final String prefNode_;
         private final String prefKey_;
         private final int prefCode_;
         
         public roiListener(String prefNode, String prefKey, int prefCode) {
            prefNode_ = prefNode;
            prefKey_ = prefKey;
            prefCode_ = prefCode;
         }
         
         @Override
         public void actionPerformed(ActionEvent e) {
            prefs_.putInt(prefNode_, prefKey_, prefCode_);
            setSPIMCameraROI(getRoiPresetFromCode(prefCode_));
         }
      }

      JToggleButton jtb = new JToggleButton(roi.toString());
      jtb.setMargin(new Insets(4,4,4,4));
      roiListener l = new roiListener(prefNode, prefKey, roi.getPrefCode());
      if (prefs_.getInt(prefNode, prefKey, 0) == roi.getPrefCode()) {
         jtb.setSelected(true);
      }
      jtb.addActionListener(l);
      return jtb;
   }

   private void setSPIMCameraROI(RoiPresets roi) {
      for (Devices.Keys devKey : Devices.SPIM_CAMERAS) {
         cameras_.setCameraROI(devKey, roi);
      }
   }
   
   public static enum RoiPresets {
      UNCHANGED("Unchanged", 0),
      FULL("Full", 1),
      HALF("1/2", 2),
      QUARTER("1/4", 3),
      EIGHTH("1/8", 4),
      CUSTOM("Custom", 5);
      private final String text;
      private final int prefCode;
      RoiPresets(String text, int prefCode) {
         this.text = text;
         this.prefCode = prefCode;
      }
      @Override
      public String toString() {
         return text;
      }
      public int getPrefCode() {
         return prefCode;
      }
   };
   
   private static RoiPresets getRoiPresetFromCode(int prefCode) {
      if (prefCode == RoiPresets.UNCHANGED.getPrefCode()) {
         return RoiPresets.UNCHANGED;
      } else  if (prefCode == RoiPresets.FULL.getPrefCode()) {
         return RoiPresets.FULL;
      } else if (prefCode == RoiPresets.HALF.getPrefCode()) {
         return RoiPresets.HALF;
      } else if (prefCode == RoiPresets.QUARTER.getPrefCode()) {
         return RoiPresets.QUARTER;
      } else if (prefCode == RoiPresets.EIGHTH.getPrefCode()) {
         return RoiPresets.EIGHTH;
      } else {
         return RoiPresets.CUSTOM;
      }
   }
   
}
