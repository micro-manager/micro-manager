///////////////////////////////////////////////////////////////////////////////
//FILE:          PresetsPage.java
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
// CVS:          $Id$
//
package org.micromanager.conf;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.Preferences;
import javax.swing.JButton;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.border.LineBorder;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/**
 * Wizard page to edit configuration presets and groups.
 *
 */
public class PresetsPage extends PagePanel {

   private JTable propTable_;
   private JTree groupTree_;
   /**
    * Create the panel
    */
   public PresetsPage(Preferences prefs) {
      super();
      title_ = "Define or edit configuration presets";
      helpText_ = "Presets are groups of multiple commands that can be executed with a single action in the GUI. " +
      "Presets are organized in groups (top level in the configuration tree). Each group can have one or more presets and each preset can contain one or more commands.\n\n" +

      "To add a configuration preset first select a configuration group, then select the command in the left-hand list and press -Add- button.";
      setHelpFileName("conf_presets_page.html");
      prefs_ = prefs;
      setLayout(null);

      final JLabel groupLabel = new JLabel();
      groupLabel.setText("Configuration presets:");
      groupLabel.setBounds(10, 3, 161, 21);
      add(groupLabel);

      groupTree_ = new JTree();
      //groupTree_.setEditable(true);
      groupTree_.setRootVisible(false);
      // The following method is available only in java > 1.4:
      //groupTree_.setInheritsPopupMenu(true);
      groupTree_.setBorder(new LineBorder(Color.black, 1, false));
      groupTree_.setAutoscrolls(true);
      //groupTree_.setBounds(10, 27, 187, 217);
      
      final JScrollPane treeScrollPane = new JScrollPane();
      treeScrollPane.setBounds(10, 27, 250, 200);
      add(treeScrollPane);
      treeScrollPane.setViewportView(groupTree_);

      final JScrollPane tableScrollPane = new JScrollPane();
      tableScrollPane.setBounds(278, 26, 283, 200);
      add(tableScrollPane);

      propTable_ = new JTable();
      propTable_.setSelectionForeground(Color.BLACK);
      propTable_.setSelectionBackground(Color.GRAY);
      propTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      propTable_.setAutoCreateColumnsFromModel(false);
      tableScrollPane.setViewportView(propTable_);

      final JButton removeButton = new JButton();
      removeButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            removeSetting();
         }
      });
      removeButton.setText("Remove");
      removeButton.setBounds(167, 231, 93, 23);
      add(removeButton);

      final JButton newGroupButton = new JButton();
      newGroupButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            addNewGroup();
         }
      });
      newGroupButton.setText("New Group");
      newGroupButton.setBounds(10, 231, 93, 23);
      add(newGroupButton);

      final JButton addButton = new JButton();
      addButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            addSetting();
         }
      });
      addButton.setText("<< Add");
      addButton.setBounds(278, 231, 93, 23);
      add(addButton);

      final JLabel availablePropertiesLabel = new JLabel();
      availablePropertiesLabel.setText("Available properties:");
      availablePropertiesLabel.setBounds(278, 6, 283, 14);
      add(availablePropertiesLabel);

      final JButton newPresetButton = new JButton();
      newPresetButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            addNewPreset();
         }
      });
      newPresetButton.setText("New Preset");
      newPresetButton.setBounds(10, 257, 93, 23);
      add(newPresetButton);

      final JButton editButton = new JButton();
      editButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            editNode();
         }
      });
      editButton.setText("Edit");
      editButton.setBounds(167, 257, 93, 23);
      add(editButton);
      //
   }

   public boolean enterPage(boolean next) {
      buildTree();
      buildTable();
      return true;
  }

   public boolean exitPage(boolean next) {
      // define configs in hardware
      try {
         model_.applySetupConfigsToHardware(core_);
      } catch (Exception e) {
         handleError(e.getMessage());
         return false;
      }
      return true;
   }
   
   public void refresh() {
   }

   public void loadSettings() {
   }

   public void saveSettings() {
   }

   ////////////////////////////////////////////////////////////////////////////
   // Private methods
   //
   private void buildTree() {
      String[] groups = model_.getConfigGroupList();
      Object[] nodes = new Object[groups.length];
      DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
      for (int i=0; i<groups.length; i++) {
         ConfigGroup grp = model_.findConfigGroup(groups[i]);
         if (grp == null)
            return;
         DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(grp);
         ConfigPreset[] cps = grp.getConfigPresets();
         for (int j=0; j<cps.length; j++) {
            DefaultMutableTreeNode presetNode = new DefaultMutableTreeNode(cps[j]);
            for (int k=0; k<cps[j].getNumberOfSettings(); k++) {
               Setting s = cps[j].getSetting(k);
               DefaultMutableTreeNode settingNode = new DefaultMutableTreeNode(s);
               presetNode.add(settingNode);
            }
            groupNode.add(presetNode);
         }
         root.add(groupNode);
         nodes[i] = groupNode;
      }
      
      DefaultTreeModel treeModel = new DefaultTreeModel(root);
      groupTree_.setModel(treeModel);
   }
   
   DefaultMutableTreeNode makeNode(ConfigPreset preset) {
      DefaultMutableTreeNode presetNode = new DefaultMutableTreeNode(preset);
      return presetNode;
   }
   private void buildTable() {
      PropertyTableModel tm = new PropertyTableModel(this, model_, PropertyTableModel.ALL);
      propTable_.setModel(tm);
      PropertyCellEditor cellEditor = new PropertyCellEditor();
      PropertyCellRenderer renderer = new PropertyCellRenderer();
     
      if (propTable_.getColumnCount() == 0) {
         for (int k=0; k < tm.getColumnCount(); k++) {
            TableColumn column = new TableColumn(k, 200, renderer, cellEditor);
            propTable_.addColumn(column);
         }
      }

      tm.fireTableStructureChanged();
      propTable_.repaint();
   }

   /**
    * Add/change setting in the tree from the property table.
    */
   private void addSetting() {
      TreePath path = groupTree_.getSelectionPath();
      if (path == null) {
         handleError("A preset must be selected in the configuration tree to add setting to.");
         return;
      }
      
      int propRow = propTable_.getSelectedRow();
      if (propRow < 0) {
         handleError("You must select a setting in the property table.");
         return;
      }
      
      Object nodes[] = path.getPath();
      if (nodes.length == 2) {
         handleError("Group is currently selected, but you need to select a specific preset to add setting to.");
         return;
      } else {
         
      }
      
      Setting s = ((PropertyTableModel)propTable_.getModel()).getSetting(propRow);
      
      //ConfigGroup grp = (ConfigGroup)((DefaultMutableTreeNode)nodes[1]).getUserObject();
      DefaultTreeModel tm = (DefaultTreeModel)groupTree_.getModel();
      ConfigPreset prs = (ConfigPreset)((DefaultMutableTreeNode)nodes[2]).getUserObject();
      DefaultMutableTreeNode presetNode = (DefaultMutableTreeNode)nodes[2];
      prs.addSetting(s);
      presetNode.removeAllChildren();
      for (int k=0; k<prs.getNumberOfSettings(); k++) {
         Setting st = prs.getSetting(k);
         DefaultMutableTreeNode settingNode = new DefaultMutableTreeNode(st);
         presetNode.add(settingNode);
      }
      tm.nodeStructureChanged(presetNode);
   }

   private void removeSetting() {
      TreePath path = groupTree_.getSelectionPath();
      if (path == null) {
         handleError("Tree element must be selected for removal.");
         return;
      }
      DefaultTreeModel tm = (DefaultTreeModel)groupTree_.getModel();
      Object nodes[] = path.getPath();
      if (nodes.length == 2) {
         // remove group
         ConfigGroup grp = (ConfigGroup)((DefaultMutableTreeNode)nodes[1]).getUserObject();
         model_.removeGroup(grp.getName());
         ((DefaultMutableTreeNode)nodes[0]).remove((DefaultMutableTreeNode)nodes[1]);
         tm.nodeStructureChanged((DefaultMutableTreeNode)nodes[0]);         
      } else if (nodes.length == 3) {
         // remove preset
         ConfigGroup grp = (ConfigGroup)((DefaultMutableTreeNode)nodes[1]).getUserObject();
         ConfigPreset prs = (ConfigPreset)((DefaultMutableTreeNode)nodes[2]).getUserObject();
         grp.removePreset(prs.getName());
         ((DefaultMutableTreeNode)nodes[1]).remove((DefaultMutableTreeNode)nodes[2]);
         tm.nodeStructureChanged((DefaultMutableTreeNode)nodes[1]);         
      } else if (nodes.length == 4) {
         // remove setting
         ConfigPreset prs = (ConfigPreset)((DefaultMutableTreeNode)nodes[2]).getUserObject();
         Setting s = (Setting)((DefaultMutableTreeNode)nodes[3]).getUserObject();
         prs.removeSetting(s);
         ((DefaultMutableTreeNode)nodes[2]).remove((DefaultMutableTreeNode)nodes[3]);
         tm.nodeStructureChanged((DefaultMutableTreeNode)nodes[2]);         
      } else {
         System.out.println("Unexpected path depth " + nodes.length);
         return;
      }
   }
   
   protected void addNewGroup() {
      String name = new String("");
      boolean validName = false;
      while (!validName) {
         name = JOptionPane.showInputDialog("Please type in the new group name:");
         if (name == null)
            return;
         
         if (name.length() == 0) {
            JOptionPane.showMessageDialog(this, "Empty names are not allowed!");
            continue;
         }
         ConfigGroup grp = model_.findConfigGroup(name);
         if (grp == null) {
            model_.addConfigGroup(name);
            validName = true;
            buildTree();
         } else
            JOptionPane.showMessageDialog(this, "Group with that name already exists!");
      }
   }

   protected void addNewPreset() {
      TreePath path = groupTree_.getSelectionPath();
      DefaultTreeModel tm = (DefaultTreeModel)groupTree_.getModel();
      if (path == null || path.getPath().length < 2) {
         handleError("A group must be selected for this operation.");
         return;
      }

      Object nodes[] = path.getPath();
      DefaultMutableTreeNode grpNode = (DefaultMutableTreeNode)nodes[1];
      ConfigGroup grp = (ConfigGroup)grpNode.getUserObject();
      
      String name = new String("");
      boolean validName = false;
      while (!validName) {
         name = JOptionPane.showInputDialog("Please type in the new preset name:");
         if (name == null)
            return;
         
         if (name.length() == 0) {
            JOptionPane.showMessageDialog(this, "Empty names are not allowed!");
            continue;
         }
         
         ConfigPreset preset = grp.findConfigPreset(name);
         if (preset == null) {
            ConfigPreset newPreset = new ConfigPreset(name);
            grp.addConfigPreset(newPreset);
            validName = true;
            
            grpNode.add(new DefaultMutableTreeNode(newPreset));
            tm.nodeStructureChanged(grpNode); 
         } else
            JOptionPane.showMessageDialog(this, "Preset with that name already exists!");
      }
      
   }
   
   protected void editNode() {
      TreePath path = groupTree_.getSelectionPath();
      DefaultTreeModel treeModel = (DefaultTreeModel)groupTree_.getModel();
      PropertyTableModel tableModel = (PropertyTableModel)propTable_.getModel();
      Object nodes[] = path.getPath();
      if (nodes.length == 2) {
         // edit group
         ConfigGroup grp = (ConfigGroup)((DefaultMutableTreeNode)nodes[1]).getUserObject();
         String name = new String(grp.getName());
         boolean validName = false;
         while (!validName) {
            name = JOptionPane.showInputDialog(this, "Please type in the group name:", grp.getName());
            if (name == null)
               return;
            
            if (name.length() == 0) {
               JOptionPane.showMessageDialog(this, "Empty names are not allowed!");
               continue;
            }
            
            if (name.compareTo(grp.getName()) == 0)
               return;
            
            ConfigGroup existingGroup = model_.findConfigGroup(name);
            if (existingGroup == null) {
               model_.renameGroup(grp, name);
               validName = true;
            } else
               JOptionPane.showMessageDialog(this, "Group with that name already exists!");
         }
         treeModel.nodeStructureChanged((DefaultMutableTreeNode)nodes[0]);         
      } else if (nodes.length == 3) {
         // edit preset
         ConfigGroup grp = (ConfigGroup)((DefaultMutableTreeNode)nodes[1]).getUserObject();
         ConfigPreset prs = (ConfigPreset)((DefaultMutableTreeNode)nodes[2]).getUserObject();
         String name;
         boolean validName = false;
         while (!validName) {
            name = JOptionPane.showInputDialog(this, "Please type in the preset name:", prs.getName());
            if (name == null)
               return;
            
            if (name.length() == 0) {
               JOptionPane.showMessageDialog(this, "Empty names are not allowed!");
               continue;
            }
            
            if (name.compareTo(prs.getName()) == 0)
               return;
            
            ConfigPreset existingPreset = grp.findConfigPreset(name);
            if (existingPreset == null) {
               grp.renamePreset(prs, name);
               validName = true;
            } else
               JOptionPane.showMessageDialog(this, "Preset with that name already exists!");
         }
         treeModel.nodeStructureChanged((DefaultMutableTreeNode)nodes[1]);         
      } else if (nodes.length == 4) {
         // edit setting
         ConfigPreset prs = (ConfigPreset)((DefaultMutableTreeNode)nodes[2]).getUserObject();
         Setting s = (Setting)((DefaultMutableTreeNode)nodes[3]).getUserObject();
         
         // obtain available values for the property
         Device dev = model_.findDevice(s.deviceName_);
         if (dev == null) {
            JOptionPane.showMessageDialog(this, "Invalid setting: device does not exist!");
            return;
         }
         Property prop = dev.findProperty(s.propertyName_);
         if (prop == null) {
            JOptionPane.showMessageDialog(this, "Invalid setting: property does not exist in device " + s.deviceName_);
            return;
         }
                 
         String value = new String(s.propertyValue_);
         String newValue;
         boolean validName = false;
         while (!validName) {
            
            if (prop.allowedValues_.length > 0) {
               // allowed values defined
               newValue = (String)JOptionPane.showInputDialog(this,
                     "Device=" + s.deviceName_ + ", Property=" + s.propertyName_ + ", select the new value:",
                     "Edit setting",
                     JOptionPane.INFORMATION_MESSAGE,
                     null,
                     prop.allowedValues_,
                     value);
            } else {
               // allowed values not defined
               newValue = (String)JOptionPane.showInputDialog(this,
                     "Device=" + s.deviceName_ + ", Property=" + s.propertyName_ + ", type in the new value:",
                     value);              
            }
            if (newValue == null)
               return;
            s.propertyValue_ = newValue;
            ((DefaultMutableTreeNode)nodes[3]).setUserObject(s);
            validName = true;
         }
         
         treeModel.nodeStructureChanged((DefaultMutableTreeNode)nodes[2]);         
      } else {
         System.out.println("Unexpected path depth " + nodes.length);
         return;
      }
   }
}
