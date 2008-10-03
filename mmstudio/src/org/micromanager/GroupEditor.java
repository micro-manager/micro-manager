///////////////////////////////////////////////////////////////////////////////
//FILE:          GroupEditor.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, Dec 1, 2005
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
// NOTE:         This class is obsolete, not used in the GUI.
//               We keep it in the code base for reference purposes, it may
//               be used in some of the future revisions. N.A. 01-24-2007
//
// CVS:          $Id$
//

package org.micromanager;

/**
 * PropertyEditor provides UI for manipulating sets of device properties
 */
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.prefs.Preferences;

import javax.swing.AbstractCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpringLayout;
import javax.swing.WindowConstants;
import javax.swing.border.BevelBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.DeviceType;
import mmcorej.PropertySetting;
import mmcorej.StrVector;

import org.micromanager.api.DeviceControlGUI;
import org.micromanager.utils.MMDialog;
import org.micromanager.utils.ShowFlags;

/**
 * JFrame based component for generic editing of device properties.
 * Represents the entire system state as a list of triplets:
 * device - property - value
 */
public class GroupEditor extends MMDialog {
   private static final long serialVersionUID = -2194251203338277211L;
   private JTextArea textArea;
   private JTextField groupNameField_;
   private String groupName_;
   private SpringLayout springLayout;
   Boolean changed_;
   
   private JTable table_;
   private PropertyTableData data_;
   private DeviceControlGUI parentGUI_;
   private ShowFlags flags_;
   
   private JCheckBox showCamerasCheckBox_;
   private JCheckBox showShuttersCheckBox_;
   private JCheckBox showStagesCheckBox_;
   private JCheckBox showStateDevicesCheckBox_;
   private JCheckBox showOtherCheckBox_;
   private Configuration initialCfg_;
    
   public GroupEditor(String groupName) {
      super();
      setModal(true);
      groupName_ = new String(groupName);
      changed_ = new Boolean(false);
      Preferences root = Preferences.userNodeForPackage(this.getClass());
      setPrefsNode(root.node(root.absolutePath() + "/PresetEditor"));
      initialCfg_ = new Configuration();
      
      flags_ = new ShowFlags();
      flags_.load(getPrefsNode());
      
      springLayout = new SpringLayout();
      getContentPane().setLayout(springLayout);
      setSize(551, 562);
      addWindowListener(new WindowAdapter() {
         public void windowClosing(WindowEvent e) {
            savePosition();
            flags_.save(getPrefsNode());
         }
         public void windowOpened(WindowEvent e) {
            // restore values from the previous session
            data_.updateFlags();
            data_.updateStatus();
            data_.showOriginalSelection();
        }
         public void windowClosed(WindowEvent arg0) {
            savePosition();
            flags_.save(getPrefsNode());
         }
      });
      setTitle("Preset Editor");

      loadPosition(100, 100, 400, 300);
      setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

      final JScrollPane scrollPane = new JScrollPane();
      scrollPane.setFont(new Font("Arial", Font.PLAIN, 10));
      scrollPane.setBorder(new BevelBorder(BevelBorder.LOWERED));
      getContentPane().add(scrollPane);
      springLayout.putConstraint(SpringLayout.SOUTH, scrollPane, -5, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, scrollPane, 160, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, scrollPane, -5, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, scrollPane, 5, SpringLayout.WEST, getContentPane());

      table_ = new JTable();
      table_.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      table_.setAutoCreateColumnsFromModel(false);
      scrollPane.setViewportView(table_);
      
      showCamerasCheckBox_ = new JCheckBox();
      showCamerasCheckBox_.setFont(new Font("", Font.PLAIN, 10));
      showCamerasCheckBox_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            flags_.cameras_ = showCamerasCheckBox_.isSelected();
            data_.updateStatus();
         }
      });
      showCamerasCheckBox_.setText("Show cameras");
      getContentPane().add(showCamerasCheckBox_);
      springLayout.putConstraint(SpringLayout.SOUTH, showCamerasCheckBox_, 28, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, showCamerasCheckBox_, 10, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, showCamerasCheckBox_, 111, SpringLayout.WEST, getContentPane());

      showShuttersCheckBox_ = new JCheckBox();
      showShuttersCheckBox_.setFont(new Font("", Font.PLAIN, 10));
      showShuttersCheckBox_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            flags_.shutters_ = showShuttersCheckBox_.isSelected();
            data_.updateStatus();
         }
      });
      showShuttersCheckBox_.setText("Show shutters");
      getContentPane().add(showShuttersCheckBox_);
      springLayout.putConstraint(SpringLayout.EAST, showShuttersCheckBox_, 111, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, showShuttersCheckBox_, 10, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, showShuttersCheckBox_, 50, SpringLayout.NORTH, getContentPane());

      showStagesCheckBox_ = new JCheckBox();
      showStagesCheckBox_.setFont(new Font("", Font.PLAIN, 10));
      showStagesCheckBox_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            flags_.stages_ = showStagesCheckBox_.isSelected();
            data_.updateStatus();
         }
      });
      showStagesCheckBox_.setText("Show stages");
      getContentPane().add(showStagesCheckBox_);
      springLayout.putConstraint(SpringLayout.EAST, showStagesCheckBox_, 111, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, showStagesCheckBox_, 10, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, showStagesCheckBox_, 73, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, showStagesCheckBox_, 50, SpringLayout.NORTH, getContentPane());

      showStateDevicesCheckBox_ = new JCheckBox();
      showStateDevicesCheckBox_.setFont(new Font("", Font.PLAIN, 10));
      showStateDevicesCheckBox_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            flags_.state_ = showStateDevicesCheckBox_.isSelected();
            data_.updateStatus();
         }
      });
      showStateDevicesCheckBox_.setText("Show wheels, turrets,etc.");
      getContentPane().add(showStateDevicesCheckBox_);
      springLayout.putConstraint(SpringLayout.EAST, showStateDevicesCheckBox_, 165, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, showStateDevicesCheckBox_, 10, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, showStateDevicesCheckBox_, 95, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, showStateDevicesCheckBox_, 72, SpringLayout.NORTH, getContentPane());

      showOtherCheckBox_ = new JCheckBox();
      showOtherCheckBox_.setFont(new Font("", Font.PLAIN, 10));
      showOtherCheckBox_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            flags_.other_ = showOtherCheckBox_.isSelected();
            data_.updateStatus();
         }
      });
      showOtherCheckBox_.setText("Show other devices");
      getContentPane().add(showOtherCheckBox_);
      springLayout.putConstraint(SpringLayout.EAST, showOtherCheckBox_, 155, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, showOtherCheckBox_, 10, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, showOtherCheckBox_, 95, SpringLayout.NORTH, getContentPane());

      groupNameField_ = new JTextField(groupName);
      getContentPane().add(groupNameField_);
      springLayout.putConstraint(SpringLayout.EAST, groupNameField_, 340, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, groupNameField_, 190, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, groupNameField_, 85, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, groupNameField_, 66, SpringLayout.NORTH, getContentPane());

      final JLabel presetNameLabel = new JLabel();
      presetNameLabel.setFont(new Font("", Font.PLAIN, 10));
      presetNameLabel.setText("Group name:");
      getContentPane().add(presetNameLabel);
      springLayout.putConstraint(SpringLayout.SOUTH, presetNameLabel, 65, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, presetNameLabel, 51, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, presetNameLabel, 340, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, presetNameLabel, 190, SpringLayout.WEST, getContentPane());

      final JButton okButton = new JButton();
      okButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            applySettings();
            changed_ = Boolean.TRUE;
            dispose();
         }
      });
      okButton.setText("OK");
      getContentPane().add(okButton);
      springLayout.putConstraint(SpringLayout.SOUTH, okButton, 49, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, okButton, 26, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, okButton, 475, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, okButton, 385, SpringLayout.WEST, getContentPane());

      final JButton cancelButton = new JButton();
      cancelButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            dispose();
         }
      });
      cancelButton.setText("Cancel");
      getContentPane().add(cancelButton);
      springLayout.putConstraint(SpringLayout.EAST, cancelButton, 475, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, cancelButton, 385, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, cancelButton, 75, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, cancelButton, 52, SpringLayout.NORTH, getContentPane());

      textArea = new JTextArea();
      textArea.setFont(new Font("Arial", Font.PLAIN, 10));
      textArea.setWrapStyleWord(true);
      textArea.setText("Select all properties you want to use in this group and press OK to save changes.\n" +
                       "All presets in this group will be referring to the selected set of properties.");
      textArea.setEditable(false);
      textArea.setOpaque(false);
      getContentPane().add(textArea);
      springLayout.putConstraint(SpringLayout.EAST, textArea, -5, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, textArea, 10, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, textArea, 155, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, textArea, 122, SpringLayout.NORTH, getContentPane());

      final JButton revertToOriginalButton = new JButton();
      revertToOriginalButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            data_.updateFlags();
            data_.updateStatus();
            data_.showOriginalSelection();
            groupNameField_.setText(groupName_);
         }
      });
      revertToOriginalButton.setText("Revert to original");
      getContentPane().add(revertToOriginalButton);
      springLayout.putConstraint(SpringLayout.EAST, revertToOriginalButton, 340, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, revertToOriginalButton, 0, SpringLayout.WEST, groupNameField_);
      springLayout.putConstraint(SpringLayout.SOUTH, revertToOriginalButton, 113, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, revertToOriginalButton, 90, SpringLayout.NORTH, getContentPane());
   }
   

   protected void applySettings() {
      data_.applySettings(groupNameField_.getText(), groupName_);
   }
   
   public void setCore(CMMCore core){
      data_ = new PropertyTableData(core, flags_);
      table_.removeAll();
      table_.setModel(data_);
      
      PropertyCellEditor cellEditor = new PropertyCellEditor();
      PropertyCellRenderer renderer = new PropertyCellRenderer();
     
      for (int k=0; k < data_.getColumnCount(); k++) {
         TableColumn column = new TableColumn(k, 200, renderer, cellEditor);
         table_.addColumn(column);
      }
            
      showCamerasCheckBox_.setSelected(flags_.cameras_);
      showStagesCheckBox_.setSelected(flags_.stages_);
      showShuttersCheckBox_.setSelected(flags_.shutters_);
      showStateDevicesCheckBox_.setSelected(flags_.state_);
      showOtherCheckBox_.setSelected(flags_.other_);
      
      data_.updateFlags();
      data_.updateStatus();
  }
   
   public void setParentGUI(DeviceControlGUI parent) {
      parentGUI_ = parent;
   }
   
   private void handleException (Exception e) {
      String errText = "Exeption occured: " + e.getMessage();
      JOptionPane.showMessageDialog(this, errText);
   }
      
   
   /**
    * Property descriptor, representing MMCore data
    */
   private class PropertyItem {
      public String device;  // device name (label)
      public String name;    // property name
      public String value;   // property value
      public boolean readOnly = false;    // is it read-only ?
      public String allowed[];            // the list of allowed values
      public boolean show = true; // is it included in the current configuration ?
      public boolean orgSel = false;
   }
   

   /**
    * Property table data model, representing MMCore data
    */
   class PropertyTableData extends AbstractTableModel {
      private static final long serialVersionUID = 1028139082784306040L;

      final public String columnNames_[] = {
            "Property"
      };
      
      ArrayList<PropertyItem> propList_ = new ArrayList<PropertyItem>();
      private CMMCore core_ = null;
      
      public PropertyTableData(CMMCore core, ShowFlags flags) {
         core_ = core;
         flags_ = flags;
         updateStatus();
      }
      public void applySettings(String newGroupName, String oldGroupName) {
         Configuration cfgs[] = new Configuration[0];
         StrVector cfgNames = new StrVector(0);
         try {
            // first check if the group and remove it if it does
            if (core_.isGroupDefined(oldGroupName)) {
               // save group data before removing
               cfgNames = core_.getAvailableConfigs(oldGroupName);
               cfgs = new Configuration[(int)cfgNames.size()];
               for (int i=0; i<cfgs.length; i++)
                  cfgs[i] = core_.getConfigData(oldGroupName, cfgNames.get(i));
               
               // remove old group
               core_.deleteConfigGroup(oldGroupName);
            }
            
            // define a new group
            int selection[] = table_.getSelectedRows();
            
            // restore all presets which have a corresponding signatur
            if (cfgs.length > 0) {
               for (int i=0; i<cfgs.length; i++) {
                  for (int j=0; j<cfgs[i].size(); j++) {
                     PropertySetting ps = cfgs[i].getSetting(j);
                     for (int k=0; k<selection.length; k++) {
                        PropertyItem item = propList_.get(selection[k]);
                        if (item.device.compareTo(ps.getDeviceLabel()) == 0 && item.name.compareTo(ps.getPropertyName()) == 0)
                           core_.defineConfig(newGroupName, cfgNames.get(i), ps.getDeviceLabel(), ps.getPropertyName(), ps.getPropertyValue());
                     }
                  }
               }
            } else {
               core_.defineConfigGroup(newGroupName);
               for (int k=0; k<selection.length; k++) {
                  PropertyItem item = propList_.get(selection[k]);
                  core_.defineConfig(newGroupName, "EditThisPreset", item.device, item.name, item.value);
               }
            }
            
         } catch (Exception e) {
            handleException(e);
         }
      }
      
      public void deleteConfig(String group, String config) {
         try {
            core_.deleteConfig(group, config);
         } catch (Exception e) {
            handleException(e);
         }
      }
      public StrVector getAvailableConfigGroups() {
         return core_.getAvailableConfigGroups();
      }
      public void setConfig(String group, String config) {
         try {
            core_.setConfig(group, config);
            core_.waitForConfig(group, config);
         } catch (Exception e) {
            handleException(e);
         }                  
      }
                 
      public int getRowCount() {
         return propList_.size();
      }
      
      public int getColumnCount() {
         return columnNames_.length;
      }
      
      public PropertyItem getPropertyItem(int row) {
         return propList_.get(row);
      }
      
      public Object getValueAt(int row, int col) {
         
         PropertyItem item = propList_.get(row);
         if (col == 0)
            return item.device + "-" + item.name;
         else if (col == 1)
            return item.value;
         
         return null;
      }
      
      public void setValueAt(Object value, int row, int col) {
         PropertyItem item = propList_.get(row);
         if (col == 1) {
            try {
               core_.setProperty(item.device, item.name, value.toString());
               //item.m_value = core_.getProperty(item.m_device, item.m_name);
               core_.waitForDevice(item.device);
               refresh();
               //item.m_value = value.toString();
               if (parentGUI_ != null)
                  parentGUI_.updateGUI(true);              
               fireTableCellUpdated(row, col);
            } catch (Exception e) {
               handleException(e);
            }
         }
      }
      
      public String getColumnName(int column) {
         return columnNames_[column];
      }
      
      public boolean isCellEditable(int nRow, int nCol) {
         return false;
      }
      
      String getConfig(String group) {
         String config = "";
         try {
            config = core_.getCurrentConfig(group);
         } catch (Exception e) {
            handleException(e);
         }
         return config;
      }
      
      StrVector getAvailableConfigs(String group) {
          return core_.getAvailableConfigs(group);
      }
      
      public void refresh(){
         try {            
            for (int i=0; i<propList_.size(); i++){
               PropertyItem item = propList_.get(i);
               item.value = core_.getProperty(item.device, item.name);
            }
            this.fireTableDataChanged();
         } catch (Exception e) {
            handleException(e);
         }
      }

      public void updateStatus(){
         try {
            
            StrVector devices = core_.getLoadedDevices();
            propList_.clear();
            
            for (int i=0; i<devices.size(); i++){               
               // select which devices to display
               DeviceType dtype = core_.getDeviceType(devices.get(i));
               boolean showDevice = false;
               if (dtype == DeviceType.CameraDevice)
                  showDevice = flags_.cameras_;
               else if (dtype == DeviceType.ShutterDevice)
                  showDevice = flags_.shutters_;
               else if (dtype == DeviceType.StageDevice)
                  showDevice = flags_.stages_;
               else if (dtype == DeviceType.XYStageDevice)
                  showDevice = flags_.stages_;
               else if (dtype == DeviceType.StateDevice)
                  showDevice = flags_.state_;
               else
                  showDevice = flags_.other_;
               
               if (showDevice) {
                  StrVector properties = core_.getDevicePropertyNames(devices.get(i));
                  
                  for (int j=0; j<properties.size(); j++){
                     PropertyItem item = new PropertyItem();
                     item.device = devices.get(i);
                     item.name = properties.get(j);
                     item.value = core_.getProperty(devices.get(i), properties.get(j));
                     item.readOnly = core_.isPropertyReadOnly(devices.get(i), properties.get(j));
                     StrVector values = core_.getAllowedPropertyValues(devices.get(i), properties.get(j));
                     item.allowed = new String[(int)values.size()];
                     for (int k=0; k<values.size(); k++){
                        item.allowed[k] = values.get(k);
                     }
                     
                     if (!item.readOnly) {
                        if(initialCfg_.isPropertyIncluded(item.device, item.name)){
                           item.orgSel  = true;
                        }
                        propList_.add(item);
                     }
                  }
               }
            }
         } catch (Exception e) {
            handleException(e);
         }
         this.fireTableStructureChanged();
      }
      
      /**
       * Make sure that flags show all devices contained in the original selection.
       *
       */
      private void updateFlags() {
         try {
            // get properties contained in the current config
            StrVector presets = core_.getAvailableConfigs(groupName_);
            if (presets.size() == 0)
               return;
            
            initialCfg_ = core_.getConfigData(groupName_, presets.get(0));
            
            // change 'show' flags to always show contained devices
            for (int i=0; i< initialCfg_.size(); i++) {
               DeviceType dtype = core_.getDeviceType(initialCfg_.getSetting(i).getDeviceLabel());
               if (dtype == DeviceType.CameraDevice) {
                  flags_.cameras_ = true;
                  showCamerasCheckBox_.setSelected(true);
               } else if (dtype == DeviceType.ShutterDevice) {
                  flags_.shutters_ = true;
                  showShuttersCheckBox_.setSelected(true);
               } else if (dtype == DeviceType.StageDevice) {
                  flags_.stages_ = true;
                  showStagesCheckBox_.setSelected(true);
               } else if (dtype == DeviceType.XYStageDevice) {
                  flags_.stages_ = true;
                  showStagesCheckBox_.setSelected(true);
               } else if (dtype == DeviceType.StateDevice) {
                  flags_.state_ = true;
                  showStateDevicesCheckBox_.setSelected(true);
               } else {
                  showOtherCheckBox_.setSelected(true);
                  flags_.other_ = true;;
               }
            }
         } catch (Exception e) {
            handleException(e);
         }
      }
      
      /**
       * Restore selection to refelect the original configuration.
       *
       */
      public void showOriginalSelection() {
         // set appropriate selection
         table_.clearSelection();
         for (int i=0; i<propList_.size(); i++) {
            PropertyItem item = propList_.get(i);
            if(initialCfg_.isPropertyIncluded(item.device, item.name)) {
               if (table_.getRowCount() > i) {
                  table_.addRowSelectionInterval(i, i);
                  table_.scrollRectToVisible(table_.getCellRect(i, 0, true));
               }
            }
         }
      }
   }
   
   /**
    * Cell editing using either JTextField or JComboBox depending on whether the
    * property enforces a set of allowed values.
    */
   public class PropertyCellEditor extends AbstractCellEditor implements TableCellEditor {
      private static final long serialVersionUID = -6783405605353002954L;
      // This is the component that will handle the editing of the cell value
      JTextField text_ = new JTextField();
      JComboBox combo_ = new JComboBox();
      JCheckBox check_ = new JCheckBox();
      int editingCol_;
      PropertyItem item_;
      
      public PropertyCellEditor() {
         super();
         check_.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               fireEditingStopped();
            }
         });
      }
      
      // This method is called when a cell value is edited by the user.
      public Component getTableCellEditorComponent(JTable table, Object value,
            boolean isSelected, int rowIndex, int colIndex) {
         
         if (isSelected) {
            // cell (and perhaps other cells) are selected
         }
         
         editingCol_ = colIndex;
                  
         PropertyTableData data = (PropertyTableData)table.getModel();
         item_ = data.getPropertyItem(rowIndex);
         // Configure the component with the specified value
         
         if (colIndex == 1) {
            if (item_.allowed.length == 0) {
               text_.setText((String)value);
               return text_;
            }
         
            ActionListener[] l = combo_.getActionListeners();
            for (int i=0; i<l.length; i++)
               combo_.removeActionListener(l[i]);
            combo_.removeAllItems();
            for (int i=0; i<item_.allowed.length; i++){
               combo_.addItem(item_.allowed[i]);
            }
            combo_.setSelectedItem(item_.value);
            
            // end editing on selection change
            combo_.addActionListener(new ActionListener() {
               public void actionPerformed(ActionEvent e) {
                  fireEditingStopped();
               }
            });
                       
            return combo_;
         } else if (colIndex == 2) {
            return check_;
         }
         return null;
      }
      
      // This method is called when editing is completed.
      // It must return the new value to be stored in the cell.
      public Object getCellEditorValue() {
         if (editingCol_ == 1) {
            if (item_.allowed.length == 0)
               return text_.getText();
            else
               return combo_.getSelectedItem();
         } else if (editingCol_ == 2)
            return check_;
         
         return null;
      }
   }
   
   public class PropertyCellRenderer implements TableCellRenderer {
      // This method is called each time a cell in a column
      // using this renderer needs to be rendered.
      PropertyItem m_item;
      
      public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int rowIndex, int colIndex) {
         
         PropertyTableData data = (PropertyTableData)table.getModel();
         m_item = data.getPropertyItem(rowIndex);
         
         JLabel lab = new JLabel();
         lab.setOpaque(true);
         lab.setHorizontalAlignment(JLabel.LEFT);
         if (isSelected) {
            lab.setBackground(Color.LIGHT_GRAY);
         } else {
            lab.setBackground(Color.WHITE);
         }
         
         if (hasFocus) {
            // this cell is the anchor and the table has the focus
         }
         
         Component comp;
         
         if (colIndex == 0) {
            lab.setText((String)value + (m_item.orgSel ? "*" : ""));
            comp = lab;
         } else if (colIndex == 1) {
            lab.setText(m_item.value.toString());
         } else {
            lab.setText("Undefined");
         }
         comp = lab;
         return comp;
      }
      
      // The following methods override the defaults for performance reasons
      public void validate() {}
      public void revalidate() {}
      protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {}
      public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}
      public PropertyCellRenderer() {
         super();
         //setFont(new Font("Arial", Font.PLAIN, 10));
      }
   }

   public boolean isChanged() {
      return changed_.booleanValue();
   }
}
