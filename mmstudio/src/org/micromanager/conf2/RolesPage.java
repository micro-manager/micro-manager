///////////////////////////////////////////////////////////////////////////////
//FILE:          RolesPage.java
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
package org.micromanager.conf2;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import mmcorej.DeviceType;
import mmcorej.MMCoreJ;
import mmcorej.StrVector;
import net.miginfocom.swing.MigLayout;

import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * Wizard page for editing device roles 
 */
public class RolesPage extends PagePanel {
   private static final long serialVersionUID = 1L;
   private JComboBox focusComboBox_;
   private JComboBox shutterComboBox_;
   private JComboBox cameraComboBox_;
   private JCheckBox autoshutterCheckBox_;
   private final JPanel focusDirectionPanel_;
   private static final String HELP_FILE_NAME = "conf_roles_page.html";
   
   /**
    * Create the panel
    */
   public RolesPage(Preferences prefs) {
      super();
      title_ = "Select default devices and choose auto-shutter setting";
      helpText_ = "Default device roles must be defined so that GUI can send adequate commands to them.\n" +
      "This is especially important for systems with multiple cameras, shutters or stages." +
      " The GUI needs to know which ones are going to be treated as default.\n\n" +
      "These roles can be changed on-the-fly thorugh configuration presets (in one of the subsequent steps).";
      setHelpFileName(HELP_FILE_NAME);
      prefs_ = prefs;
      setLayout(null);
      
      final JLabel cameraLabel = new JLabel();
      cameraLabel.setText("Default camera");
      cameraLabel.setBounds(21, 11, 120, 24);
      add(cameraLabel);

      cameraComboBox_ = new JComboBox();
      cameraComboBox_.setAutoscrolls(true);
      cameraComboBox_.setBounds(20, 35, 120, 22);
      cameraComboBox_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            try {
               model_.setDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(), MMCoreJ.getG_Keyword_CoreCamera(), (String)cameraComboBox_.getSelectedItem());
            } catch (MMConfigFileException e) {
               ReportingUtils.showError(e);
            }
         }
      });
      add(cameraComboBox_);
            
      final JLabel cameraLabel_1 = new JLabel();
      cameraLabel_1.setText("Default shutter");
      cameraLabel_1.setBounds(22, 69, 120, 24);
      add(cameraLabel_1);

      shutterComboBox_ = new JComboBox();
      shutterComboBox_.setBounds(21, 93, 120, 22);
      shutterComboBox_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            try {
               model_.setDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(), MMCoreJ.getG_Keyword_CoreShutter(), (String)shutterComboBox_.getSelectedItem());
            } catch (MMConfigFileException e) {
               handleError(e.getMessage());;
            }
         }
      });
      add(shutterComboBox_);

      final JLabel cameraLabel_2 = new JLabel();
      cameraLabel_2.setText("Default focus stage");
      cameraLabel_2.setBounds(23, 128, 150, 24);
      add(cameraLabel_2);

      focusComboBox_ = new JComboBox();
      focusComboBox_.setBounds(22, 152, 120, 22);
      focusComboBox_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            try {
               model_.setDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(), MMCoreJ.getG_Keyword_CoreFocus(), (String)focusComboBox_.getSelectedItem());
            } catch (MMConfigFileException e) {
               handleError(e.getMessage());;
            }
         }
      });
      add(focusComboBox_);

      autoshutterCheckBox_ = new JCheckBox();
      autoshutterCheckBox_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            try {
               String as = new String();
               if (autoshutterCheckBox_.isSelected())
                  as = "1";
               else
                  as = "0";
               model_.setDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(), MMCoreJ.getG_Keyword_CoreAutoShutter(), as);
            } catch (MMConfigFileException e) {
               ReportingUtils.showError(e);
            }
         }
      });
      autoshutterCheckBox_.setText("Auto-shutter");
      autoshutterCheckBox_.setBounds(21, 192, 141, 23);
      add(autoshutterCheckBox_);

      focusDirectionPanel_ = new JPanel(new MigLayout());
      JScrollPane scrollPane = new JScrollPane(focusDirectionPanel_);
      scrollPane.setBounds(21, 232, 512, 300);
      add(scrollPane);
   }

   public boolean enterPage(boolean next) {
      // find all relevant devices
      StrVector cameras = null;
      StrVector shutters = null;
      StrVector stages = null;
      try {
         cameras = core_.getLoadedDevicesOfType(DeviceType.CameraDevice);
         shutters = core_.getLoadedDevicesOfType(DeviceType.ShutterDevice);
         stages = core_.getLoadedDevicesOfType(DeviceType.StageDevice);
      } catch (Exception e) {
         ReportingUtils.showError(e);
         return false;
      }
      
      if (cameras != null) {
         String items[] = new String[(int)cameras.size()+1];
         items[0] = "";
         for (int i=0; i<cameras.size(); i++)
            items[i+1] = cameras.get(i);

         if(1 == cameras.size()) try{
               model_.setDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(), MMCoreJ.getG_Keyword_CoreCamera(),cameras.get(0));
               }
               catch( Exception e){
            }
         GUIUtils.replaceComboContents(cameraComboBox_, items);
      }
       
      if (shutters != null) {
         String items[] = new String[(int)shutters.size()+1];
         items[0] = "";
         for (int i=0; i<shutters.size(); i++)
            items[i+1] = shutters.get(i);
         if( 1 == shutters.size()) try{
            model_.setDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(), MMCoreJ.getG_Keyword_CoreShutter(), shutters.get(0));
         }
         catch(Exception e){
         }
         GUIUtils.replaceComboContents(shutterComboBox_, items);
      }
      
       if (stages != null) {
         String items[] = new String[(int)stages.size()+1];
         items[0] = "";
         for (int i=0; i<stages.size(); i++)
            items[i+1] = stages.get(i);

         if( 1 == stages.size()) try{
            model_.setDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(), MMCoreJ.getG_Keyword_CoreFocus(), stages.get(0));
         }
         catch( Exception e){

         }
         
         GUIUtils.replaceComboContents(focusComboBox_, items);

      }
   
      try {
         String camera = model_.getDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(), MMCoreJ.getG_Keyword_CoreCamera());
         if (model_.findDevice(camera) != null)
            cameraComboBox_.setSelectedItem(camera);
         else
            cameraComboBox_.setSelectedItem("");
         
         String shutter = model_.getDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(), MMCoreJ.getG_Keyword_CoreShutter());
         if (model_.findDevice(shutter) != null)
            shutterComboBox_.setSelectedItem(shutter);
         else
            shutterComboBox_.setSelectedItem("");
         
         String focus = model_.getDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(), MMCoreJ.getG_Keyword_CoreFocus());
         if (model_.findDevice(focus) != null)
            focusComboBox_.setSelectedItem(focus);
         else
            focusComboBox_.setSelectedItem("");
         
         String as = model_.getDeviceSetupProperty(MMCoreJ.getG_Keyword_CoreDevice(), MMCoreJ.getG_Keyword_CoreAutoShutter());
         if (as.compareTo("1") == 0)
            autoshutterCheckBox_.setSelected(true);
         else
            autoshutterCheckBox_.setSelected(false);
     } catch (MMConfigFileException e) {
         ReportingUtils.showError(e);
      }

      // Remove anything left in the focus direction panel, and reconstruct it
      // Note that the panel is constructed with a MigLayout
      focusDirectionPanel_.removeAll();
      JLabel focusDirectionLabel = new JLabel("Stage focus directions");
      focusDirectionPanel_.add(focusDirectionLabel, "wrap");
      if (stages != null) {
         try {
            //model_.loadFocusDirectionsFromHardware(core_);
         }
         catch (Exception e) {
            ReportingUtils.logError(e);
         }
         for (final String stageLabel : Arrays.asList(stages.toArray())) {
            final Device stage = model_.findDevice(stageLabel);
            if (stage == null) {
               continue;
            }
            int direction = stage.getFocusDirection();
            final JComboBox comboBox = new JComboBox(new String[] {
               "Unknown",
               "Positive Toward Sample",
               "Positive Away From Sample",
            });
            comboBox.setSelectedIndex(direction < 0 ? 2 : direction);
            comboBox.addActionListener(new ActionListener() {
               @Override public void actionPerformed(ActionEvent e) {
                  int i = comboBox.getSelectedIndex();
                  if (i == 2) {
                     i = -1;
                  }
                  stage.setFocusDirection(i);
                  core_.setFocusDirection(stageLabel, i);
               }
            });
            focusDirectionPanel_.add(new JLabel(stageLabel + ":"));
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
