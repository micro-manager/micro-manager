///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 29, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
//
// CVS:          $Id: RolesPage.java 7141 2011-05-04 17:01:07Z karlh $
//

package org.micromanager.internal.hcwizard;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import mmcorej.DeviceType;
import mmcorej.MMCoreJ;
import mmcorej.StrVector;
import net.miginfocom.swing.MigLayout;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Wizard page for editing device roles .
 */
public final class RolesPage extends PagePanel {
   private static final long serialVersionUID = 1L;
   private final JComboBox<String> focusComboBox_;
   private final JComboBox<String> shutterComboBox_;
   private final JComboBox<String> cameraComboBox_;
   private final JCheckBox autoshutterCheckBox_;
   private final JPanel focusDirectionPanel_;

   /**
    * Create the panel.
    */
   public RolesPage() {
      super();
      title_ = "Select default devices and choose auto-shutter setting";
      setLayout(new MigLayout("fill"));

      JTextArea help = createHelpText(
            "Select the default device, where available, to use for certain important roles.");
      add(help, "growx, span, wrap");

      add(new JLabel("Default Camera: "), "split");
      cameraComboBox_ = new JComboBox<>();
      cameraComboBox_.setAutoscrolls(true);
      cameraComboBox_.addActionListener(arg0 -> {
         try {
            model_.setDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(),
                  MMCoreJ.getG_Keyword_CoreCamera(), (String) cameraComboBox_.getSelectedItem());
         } catch (MMConfigFileException e) {
            ReportingUtils.showError(e);
         }
      });
      add(cameraComboBox_, "wrap");

      add(new JLabel("Default Shutter: "), "split");
      shutterComboBox_ = new JComboBox<>();
      shutterComboBox_.addActionListener(arg0 -> {
         try {
            model_.setDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(),
                  MMCoreJ.getG_Keyword_CoreShutter(), (String) shutterComboBox_.getSelectedItem());
         } catch (MMConfigFileException e) {
            handleError(e.getMessage());
         }
      });
      add(shutterComboBox_, "wrap");

      add(new JLabel("Default Focus Stage: "), "split");
      focusComboBox_ = new JComboBox<>();
      focusComboBox_.addActionListener(arg0 -> {
         try {
            model_.setDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(),
                  MMCoreJ.getG_Keyword_CoreFocus(), (String) focusComboBox_.getSelectedItem());
         } catch (MMConfigFileException e) {
            handleError(e.getMessage());
         }
      });
      add(focusComboBox_, "wrap");

      autoshutterCheckBox_ = new JCheckBox("Use Autoshutter By Default");
      autoshutterCheckBox_.addActionListener(arg0 -> {
         try {
            String as;
            if (autoshutterCheckBox_.isSelected()) {
               as = "1";
            }
            else {
               as = "0";
            }
            model_.setDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(),
                  MMCoreJ.getG_Keyword_CoreAutoShutter(), as);
         } catch (MMConfigFileException e) {
            ReportingUtils.showError(e);
         }
      });
      add(autoshutterCheckBox_, "wrap");

      focusDirectionPanel_ = new JPanel(new MigLayout("fill"));
      JScrollPane scrollPane = new JScrollPane(focusDirectionPanel_);
      add(scrollPane, "push, spanx, wrap");
   }

   public boolean enterPage(boolean next) {
      // find all relevant devices
      StrVector cameras;
      StrVector shutters;
      StrVector stages;
      try {
         cameras = core_.getLoadedDevicesOfType(DeviceType.CameraDevice);
         shutters = core_.getLoadedDevicesOfType(DeviceType.ShutterDevice);
         stages = core_.getLoadedDevicesOfType(DeviceType.StageDevice);
      } catch (Exception e) {
         ReportingUtils.showError(e);
         return false;
      }

      if (cameras != null) {
         String[] items = new String[(int) cameras.size() + 1];
         items[0] = "";
         for (int i = 0; i < cameras.size(); i++) {
            items[i + 1] = cameras.get(i);
         }

         if (1 == cameras.size()) {
            try {
               model_.setDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(),
                     MMCoreJ.getG_Keyword_CoreCamera(), cameras.get(0));
            } catch (Exception e) {
            }
         }
         GUIUtils.replaceComboContents(cameraComboBox_, items);
      }

      if (shutters != null) {
         String[] items = new String[(int) shutters.size() + 1];
         items[0] = "";
         for (int i = 0; i < shutters.size(); i++) {
            items[i + 1] = shutters.get(i);
         }
         if (1 == shutters.size()) {
            try {
               model_.setDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(),
                     MMCoreJ.getG_Keyword_CoreShutter(), shutters.get(0));
            } catch (Exception e) {
            }
         }
         GUIUtils.replaceComboContents(shutterComboBox_, items);
      }

      if (stages != null) {
         String[] items = new String[(int) stages.size() + 1];
         items[0] = "";
         for (int i = 0; i < stages.size(); i++) {
            items[i + 1] = stages.get(i);
         }

         if (1 == stages.size()) {
            try {
               model_.setDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(),
                     MMCoreJ.getG_Keyword_CoreFocus(), stages.get(0));
            } catch (Exception e) {

            }
         }

         GUIUtils.replaceComboContents(focusComboBox_, items);

      }

      try {
         String camera = model_.getDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(),
               MMCoreJ.getG_Keyword_CoreCamera());
         if (model_.findDevice(camera) != null) {
            cameraComboBox_.setSelectedItem(camera);
         }
         else {
            cameraComboBox_.setSelectedItem("");
         }

         String shutter = model_.getDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(),
               MMCoreJ.getG_Keyword_CoreShutter());
         if (model_.findDevice(shutter) != null) {
            shutterComboBox_.setSelectedItem(shutter);
         }
         else {
            shutterComboBox_.setSelectedItem("");
         }

         String focus = model_.getDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(),
               MMCoreJ.getG_Keyword_CoreFocus());
         if (model_.findDevice(focus) != null) {
            focusComboBox_.setSelectedItem(focus);
         }
         else {
            focusComboBox_.setSelectedItem("");
         }

         String as = model_.getDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(),
               MMCoreJ.getG_Keyword_CoreAutoShutter());
         autoshutterCheckBox_.setSelected(as.compareTo("1") == 0);
      } catch (MMConfigFileException e) {
         ReportingUtils.showError(e);
      }

      // Remove anything left in the focus direction panel, and reconstruct it
      // Note that the panel is constructed with a MigLayout
      focusDirectionPanel_.removeAll();
      if (stages != null && stages.size() > 0) {
         JLabel focusDirectionLabel = new JLabel("Stage focus directions (advanced)");
         focusDirectionPanel_.add(focusDirectionLabel, "wrap");

         try {
            model_.loadFocusDirectionsFromHardware(core_);
         } catch (Exception e) {
            ReportingUtils.logError(e);
         }
         for (final String stageLabel : stages.toArray()) {
            final Device stage = model_.findDevice(stageLabel);
            if (stage == null) {
               continue;
            }
            int direction = stage.getFocusDirection();
            final JComboBox<String> comboBox = new JComboBox<>(new String[] {
                  "Unknown",
                  "Positive Toward Sample",
                  "Positive Away From Sample",
            });
            comboBox.setSelectedIndex(direction < 0 ? 2 : direction);
            comboBox.addActionListener(e -> {
               int i = comboBox.getSelectedIndex();
               if (i == 2) {
                  i = -1;
               }
               stage.setFocusDirection(i);
               core_.setFocusDirection(stageLabel, i);
            });
            focusDirectionPanel_.add(new JLabel(stageLabel + ":"),
                  "split, span");
            focusDirectionPanel_.add(comboBox, "wrap");
         }
      }

      return true;
   }

   public boolean exitPage(boolean next) {
      // TODO Auto-generated method stub
      return true;
   }

   public void refresh() {
   }

   public void loadSettings() {
      // TODO Auto-generated method stub

   }

   public void saveSettings() {
      // TODO Auto-generated method stub

   }
}
