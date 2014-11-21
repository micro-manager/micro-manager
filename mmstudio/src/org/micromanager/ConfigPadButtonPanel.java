///////////////////////////////////////////////////////////////////////////////
//FILE:          ConfigPadButtonPanel.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       
//
// COPYRIGHT:    University of California, San Francisco, 2014
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

package org.micromanager;

import com.swtdesigner.SwingResourceManager;

import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import mmcorej.CMMCore;

import org.micromanager.api.ScriptInterface;
import org.micromanager.dialogs.GroupEditor;
import org.micromanager.dialogs.PresetEditor;
import org.micromanager.utils.ReportingUtils;

public final class ConfigPadButtonPanel extends JPanel {
   /**
    * 
    */
   private static final long serialVersionUID = 6481082898578589473L;
   
   private JButton addGroupButton_;
   private JButton removeGroupButton_;
   private JButton editGroupButton_;
   
   private JButton addPresetButton_;
   private JButton removePresetButton_;
   private JButton editPresetButton_;

   private ConfigGroupPad configPad_;

   private CMMCore core_;

   private ScriptInterface studio_;

   
   
   ConfigPadButtonPanel() {
      initialize();
   }
   
   public void initialize() {
      initializeWidgets();
   }
   
   public void initializeWidgets() {

      createLabel("Group:");
      addGroupButton_ = createButton("","/org/micromanager/icons/plus.png");
      addGroupButton_.setName("Add group");
      addGroupButton_.setToolTipText("Create new group of properties");
      removeGroupButton_ = createButton("","/org/micromanager/icons/minus.png");
      removeGroupButton_.setName("Remove group");
      removeGroupButton_.setToolTipText("Delete currently selected group");
      editGroupButton_ = createButton("Edit","");
      editGroupButton_.setName("Edit group");
      editGroupButton_.setToolTipText("Edit currently selected group");

      createLabel("Preset:");
      addPresetButton_ = createButton("","/org/micromanager/icons/plus.png");
      addPresetButton_.setName("Add preset");
      addPresetButton_.setToolTipText("Create new preset (set of values for each property in group)");
      removePresetButton_ = createButton("","/org/micromanager/icons/minus.png");
      removePresetButton_.setName("Remove preset");
      removePresetButton_.setToolTipText("Delete currently selected preset");
      editPresetButton_ = createButton("Edit","");
      editPresetButton_.setName("Remove preset");
      editPresetButton_.setToolTipText("Edit property values for currently selected preset");

      GridLayout layout = new GridLayout(1,8,2,1);
      setLayout(layout);
   }

   public void setConfigPad(ConfigGroupPad configPad) {
      configPad_ = configPad;
   }
   
   public void setGUI(MMStudio gui) {
      studio_ = gui;
   }
   
   public void setCore(CMMCore core) {
      core_ = core;
   }
   
   public void format(JComponent theComp) {
      theComp.setFont(new Font("Arial",Font.PLAIN,10));
      add(theComp);
   }

   public JLabel createLabel(String labelText) {
      JLabel theLabel = new JLabel(labelText);
      theLabel.setFont(new Font("Arial",Font.BOLD,10));
      theLabel.setHorizontalAlignment(SwingConstants.RIGHT);
      add(theLabel);
      return theLabel;
   }
   
   public JButton createButton() {
      JButton theButton = new JButton();
      theButton.setIconTextGap(0);
      theButton.setMargin(new Insets(-50,-50,-50,-50));
      format(theButton);
      theButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            handleButtonPress(e);
         }
      });
      return theButton;
   }

   public JButton createButton(String buttonText, String iconPath) {
      JButton theButton = createButton();
      theButton.setText(buttonText);
      if (iconPath.length()>0)
         theButton.setIcon(SwingResourceManager.getIcon(MMStudio.class,iconPath));
      return theButton;
   }


   protected void handleButtonPress(ActionEvent e) {
      if (e.getSource() == addGroupButton_)
         addGroup();
      if (e.getSource() == removeGroupButton_)
         removeGroup();
      if (e.getSource() == editGroupButton_)
         editGroup();
      if (e.getSource() == addPresetButton_)
         addPreset();
      if (e.getSource() == removePresetButton_)
         removePreset();
      if (e.getSource() == editPresetButton_)
         editPreset();
      studio_.refreshGUI();
   }

   @SuppressWarnings("ResultOfObjectAllocationIgnored")
   public void addGroup() {
      new GroupEditor("", "", studio_, core_, true);
   }
   
   
   public void removeGroup() {
      String groupName = configPad_.getSelectedGroup();
      if (groupName.length()>0) {
         int result = JOptionPane.showConfirmDialog(this, "Are you sure you want to remove group " + groupName + " and all associated presets?",
               "Remove the " + groupName + " group?",
               JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
         if (result == JOptionPane.OK_OPTION) {
            try {
               core_.deleteConfigGroup(groupName);
               studio_.setConfigChanged(true);
            } catch (Exception e) {
               handleException(e);
            }
         }
      } else {
         JOptionPane.showMessageDialog(this, "If you want to remove a group, select it on the Configurations panel first.");  
      }
   }
   
   @SuppressWarnings("ResultOfObjectAllocationIgnored")
   public void editGroup() {
      String groupName = configPad_.getSelectedGroup();
      if (groupName.length() ==0)
         JOptionPane.showMessageDialog(this, "To edit a group, please select it first, then press the edit button.");         
      else
         new GroupEditor(groupName, configPad_.getPresetForSelectedGroup(), studio_, core_, false);
   }
   
   
   @SuppressWarnings("ResultOfObjectAllocationIgnored")
   public void addPreset() {
      String groupName = configPad_.getSelectedGroup();
      if (groupName.length()==0)
         JOptionPane.showMessageDialog(this, "To add a preset to a group, please select the group first, then press the edit button.");
      else
         new PresetEditor(groupName, "", studio_, core_, true);
   }
   
   public void removePreset() {
      String groupName = configPad_.getSelectedGroup();
      String presetName = configPad_.getPresetForSelectedGroup();
      if (groupName.length()>0) {
         int result;
         if (core_.getAvailableConfigs(groupName).size()==1) {
            result = JOptionPane.showConfirmDialog(this, "\"" + presetName + "\" is the last preset for the \"" + groupName +"\" group.\nDelete both preset and group?",
                  "Remove last preset in group",
                  JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);   
            if (result == JOptionPane.OK_OPTION) {
               try {
                  core_.deleteConfig(groupName, presetName);
                  core_.deleteConfigGroup(groupName);
               } catch (Exception e) {
                  handleException(e);
               } 
            }
         } else {
            result = JOptionPane.showConfirmDialog(this, "Are you sure you want to remove preset " + presetName + " from the " + groupName + " group?",
                  "Remove preset",
                  JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (result == JOptionPane.OK_OPTION) {
               try {
                  core_.deleteConfig(groupName, presetName);
               } catch (Exception e) {
                  handleException(e);
               } 
            }
         }
         
      }
   }

   public String choosePreset(String groupName) {
      final String [] presets = core_.getAvailableConfigs(groupName).toArray();
      return (String) JOptionPane.showInputDialog(
                    null,
                    "Please choose a preset from the " + groupName + " group for editing.",
                    "Preset not selected.",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    presets,
                    presets[0]);
   }

   @SuppressWarnings("ResultOfObjectAllocationIgnored")
   public void editPreset() {
      final String presetName = configPad_.getPresetForSelectedGroup();
      final String groupName = configPad_.getSelectedGroup();
      if (groupName.length() ==0) {
         JOptionPane.showMessageDialog(this, "To edit a preset, please select the preset first, then press the edit button.");
      } else if (presetName.length() == 0) {
         final String newPresetName = choosePreset(groupName);
         if (newPresetName != null) {
            try {
               core_.setConfig(groupName, newPresetName);
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
            }
            new PresetEditor(groupName, newPresetName, studio_, core_, false);

         }
      } else {
         new PresetEditor(groupName, presetName, studio_, core_, false);
      }
   }

   
   public void handleException(Exception e) {
      ReportingUtils.logError(e);
   }
   
}
