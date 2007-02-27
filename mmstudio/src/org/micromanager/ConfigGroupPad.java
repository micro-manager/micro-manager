///////////////////////////////////////////////////////////////////////////////
//FILE:          ConfigGroupPad.java
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
// CVS:          $Id$

package org.micromanager;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.prefs.Preferences;

import javax.swing.AbstractCellEditor;
import javax.swing.CellEditor;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.MMCoreJ;
import mmcorej.StrVector;

import org.micromanager.utils.ChannelSpec;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.DeviceControlGUI;

/**
 * Preset panel.
 * Displays a list of groups and lets the user select a preset.
 */
public class ConfigGroupPad extends JScrollPane{

   private static final long serialVersionUID = 1L;
   private JTable table_;
   private StateTableData data_;
   private DeviceControlGUI parentGUI_;
   private ArrayList channels_;
   private Preferences prefs_;
   private static final String TITLE = "Preset Editing";

   private static final String CONTRAST_SETTINGS_8_MIN = "contrast8_MIN";
   private static final String CONTRAST_SETTINGS_8_MAX = "contrast8_MAX";
   private static final String CONTRAST_SETTINGS_16_MIN = "contrast16_MIN";
   private static final String CONTRAST_SETTINGS_16_MAX = "contrast16_MAX";
   private static final String EXPOSURE = "exposure";
   private static final String CHANNEL_NAME = "name";

   /**
    * Property descriptor, representing MMCore data
    */
   private class StateItem {
      public String group;
      public String config;
      public String allowed[];
      public String descr;
   }


   public ConfigGroupPad() {
      super();
      prefs_ = Preferences.userNodeForPackage(this.getClass());      
      table_ = new JTable();
      table_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      table_.setAutoCreateColumnsFromModel(false);
      setViewportView(table_);
      channels_ = new ArrayList();
   }

   private void handleException (Exception e) {
      String errText = "Exeption occured: " + e.getMessage();
      JOptionPane.showMessageDialog(this, errText);
   }

   public void setCore(CMMCore core){
      data_ = new StateTableData(core);
      table_.removeAll();
      table_.setModel(data_);

      PropertyCellEditor cellEditor = new PropertyCellEditor();
      PropertyCellRenderer renderer = new PropertyCellRenderer();

      for (int k=0; k < data_.getColumnCount(); k++) {
         renderer.setHorizontalAlignment(JLabel.LEFT);
         TableColumn column = new TableColumn(k, 200, renderer, cellEditor);
         table_.addColumn(column);
      }
   }

   public void setParentGUI(DeviceControlGUI parentGUI) {
      parentGUI_ = parentGUI;
   }
   public void refresh() {
      data_.updateStatus();
      data_.fireTableStructureChanged();
      table_.repaint();
   }

   public boolean addGroup() {

      String name = new String("");
      boolean validName = false;
      while (!validName) {
         name = JOptionPane.showInputDialog("Please enter the new group name:");
         if (name == null)
            return false;

         if (name.length() > 0)
            validName = true;
         else
            JOptionPane.showMessageDialog(this, "Empty group name is not allowed.");
      }

      if (data_.addGroup(name))
         return addPreset(name);
      
      return true;
   }

   public boolean addPreset() {
      int idx = table_.getSelectedRow();
      if (idx < 0) {
         handleError("A group to add preset to must be selected first.");
         return false;
      }
      String group = (String)data_.getValueAt(idx, 0);

      return addPreset(group);      
   }
   
   public boolean addPreset(String group) {
      String name = new String("NewPreset");
      return data_.editPreset(name, group);      
   }

   public boolean removeGroup() {
      int idx = table_.getSelectedRow();
      if (idx < 0) {
         handleError("A group must be selected first.");
         return false;
      }

      String group = (String)data_.getValueAt(idx, 0);

      int result = JOptionPane.showConfirmDialog(this, "Are you sure you want to remove group " + group + " and all associated presets?",
            TITLE,
            JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
      if (result == JOptionPane.OK_OPTION) {
         data_.removeGroup(group);
         return true;
      } else
         return false;
   }

   public boolean removePreset() {
      int idx = table_.getSelectedRow();
      if (idx < 0) {
         handleError("A preset must be selected first.");
         return false;
      }

      String preset = (String)data_.getValueAt(idx, 1);
      String group = (String)data_.getValueAt(idx, 0);

      CellEditor ce = table_.getCellEditor();
      if (ce != null)
         ce.stopCellEditing();

      int result = JOptionPane.showConfirmDialog(this, "Are you sure you want to remove preset " + preset + "?",
            TITLE,
            JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
      if (result == JOptionPane.OK_OPTION) {
         data_.removePreset(group, preset);
         data_.fireTableStructureChanged();
         return true;
      }
      return false;
   }

   public boolean editPreset() {
      int idx = table_.getSelectedRow();
      if (idx < 0) {
         handleError("A preset must be selected first.");
         return false;
      }

      String preset = (String)data_.getValueAt(idx, 1);
      String group = (String)data_.getValueAt(idx, 0);

      return data_.editPreset(preset, group);
   }

   private void handleError(String txt) {
      JOptionPane.showMessageDialog(this, txt);
   }

   ////////////////////////////////////////////////////////////////////////////
   /**
    * Property table data model, representing state devices
    */
   class StateTableData extends AbstractTableModel {
      final public String columnNames_[] = {
            "Group",
            "Configuration"
      };
      ArrayList groupList_ = new ArrayList();
      private CMMCore core_ = null;
      private boolean configDirty_;

      public StateTableData(CMMCore core) {
         core_ = core;
         updateStatus();
         configDirty_ = false;
      }
      public int getRowCount() {
         return groupList_.size();
      }

      public int getColumnCount() {
         return columnNames_.length;
      }

      public StateItem getPropertyItem(int row) {
         return (StateItem) groupList_.get(row);
      }

      public Object getValueAt(int row, int col) {

         StateItem item = (StateItem) groupList_.get(row);
         if (col == 0)
            return item.group;
         else if (col == 1)
            return item.config;

         return null;
      }

      public void setValueAt(Object value, int row, int col) {
         StateItem item = (StateItem) groupList_.get(row);
         if (col == 1) {
            try {
               if (value != null && value.toString().length() > 0)
               {
                  // apply selected config
                  core_.setConfig(item.group, value.toString());
                  core_.waitForConfig(item.group, value.toString());
                  updateStatus();
                  repaint();
                  if (parentGUI_ != null)
                     parentGUI_.updateGUI();

                  // if the channel was changed, adjust the exposure and contrast settings
                  if (item.group.equals(MMCoreJ.getG_Keyword_Channel())) {
                     ChannelSpec csFound = null;
                     // >>>> this should be a hash table
                     for (int i=0; i<channels_.size(); i++) {
                        ChannelSpec cs = (ChannelSpec) channels_.get(i);
                        if (cs != null) {
                           if (cs.name_.equals(item.config)) {
                              csFound = cs;
                              break;
                           }
                        }
                     }

                     if (csFound == null) {
                        // channel was not found -> add it to the list
                        csFound = new ChannelSpec();
                        csFound.name_ = item.config;
                        csFound.exposure_ = core_.getExposure();
                        if (parentGUI_ != null) {
                           ContrastSettings ctr = parentGUI_.getContrastSettings();
                           if (ctr != null) 
                              if (parentGUI_.is16bit())
                                 csFound.contrast16_ = ctr;
                              else
                                 csFound.contrast8_ = ctr;

                           channels_.add(csFound);
                        }
                     } else {
                        // channel was found - apply settings
                        // >>> DISABLED - not working properly
//                      core_.setExposure(csFound.exposure_);
//                      if (parentGUI_ != null)
//                      parentGUI_.applyContrastSettings(csFound.contrast8_, csFound.contrast16_);
                     }
                  }
               }
            } catch (Exception e) {
               handleException(e);
            }
         }         
      }

      public String getColumnName(int column) {
         return columnNames_[column];
      }

      public boolean isCellEditable(int nRow, int nCol) {
         if (nCol == 0)
            return false;
         return true;
      }

      public void updateStatus(){
         try {
            StrVector groups = core_.getAvailableConfigGroups();
            groupList_.clear();

            for (int i=0; i<groups.size(); i++){
               StateItem item = new StateItem();
               item.group = groups.get(i);
               item.config = core_.getCurrentConfig(groups.get(i));
               StrVector values = core_.getAvailableConfigs(groups.get(i));
               item.allowed = new String[(int)values.size()];
               for (int k=0; k<values.size(); k++){
                  item.allowed[k] = values.get(k);
               }
               if (item.config.length() > 0) {
                  Configuration cfg = core_.getConfigData(groups.get(i), item.config);
                  item.descr = cfg.getVerbose();
               }
               else
                  item.descr = "";
               groupList_.add(item);
            }
         } catch (Exception e) {
            handleException(e);
         }
      }

      public boolean addGroup(String name) {
         try {
            core_.defineConfigGroup(name);
            configDirty_ = true;
            updateStatus();
            return true;
         } catch (Exception e) {
            handleException(e);
            return false;
         }         
      }

      public void removeGroup(String group) {
         try {
            core_.deleteConfigGroup(group);
            configDirty_ = true;
            updateStatus();
         } catch (Exception e) {
            handleException(e);
         }

      }      
      public boolean isConfigDirty() {
         return configDirty_;
      }
      
      public boolean editGroup(String name) {
         GroupEditor dlg = new GroupEditor(name);
         dlg.setCore(core_);
         dlg.setVisible(true);
         if (dlg.isChanged())
            configDirty_ = true;

         return configDirty_;
      }

      public void removePreset(String group, String preset) {
         try {
            core_.deleteConfig(group, preset);
            configDirty_ = true;
            updateStatus();
         } catch (Exception e) {
            handleException(e);
         }
      }

      public boolean editPreset(String preset, String group) {
         PresetEditor dlg = new PresetEditor(preset, group);
         dlg.setCore(core_);
         dlg.setVisible(true);
         if (dlg.isChanged())
            configDirty_ = true;

         return configDirty_;
      }
   }

   ////////////////////////////////////////////////////////////////////////////
   /**
    * Cell editing using either JTextField or JComboBox depending on whether the
    * property enforces a set of allowed values.
    */
   public class PropertyCellEditor extends AbstractCellEditor implements TableCellEditor {
      /**
       * 
       */
      private static final long serialVersionUID = 1L;
      // This is the component that will handle the editing of the cell value
      JTextField text_ = new JTextField();
      JComboBox combo_ = new JComboBox();
      StateItem item_;

      // This method is called when a cell value is edited by the user.
      public Component getTableCellEditorComponent(JTable table, Object value,
            boolean isSelected, int rowIndex, int vColIndex) {

         if (isSelected) {
            // cell (and perhaps other cells) are selected
         }

         StateTableData data = (StateTableData)table.getModel();
         item_ = data.getPropertyItem(rowIndex);
         // Configure the component with the specified value

         if (item_.allowed.length == 0)
         {
            text_.setText((String)value);
            return text_;
         }

         // remove old listeners
         ActionListener[] l = combo_.getActionListeners();
         for (int i=0; i<l.length; i++)
            combo_.removeActionListener(l[i]);
         combo_.removeAllItems();
         for (int i=0; i<item_.allowed.length; i++){
            combo_.addItem(item_.allowed[i]);
         }

         // remove old items
         combo_.removeAllItems();

         // add new items
         for (int i=0; i<item_.allowed.length; i++){
            combo_.addItem(item_.allowed[i]);
         }
         combo_.setSelectedItem(item_.config);

         // end editing on selection change
         combo_.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               fireEditingStopped();
            }
         });

         // Return the configured component
         return combo_;
      }

      // This method is called when editing is completed.
      // It must return the new value to be stored in the cell.
      public Object getCellEditorValue() {
         if (item_.allowed.length == 0)
            return text_.getText();
         else
            return combo_.getSelectedItem();
      }
   }

   /**
    * Rendering element for the property table. 
    *
    */
   public class PropertyCellRenderer extends JLabel implements TableCellRenderer {
      /**
       * 
       */
      private static final long serialVersionUID = 1L;
      // This method is called each time a cell in a column
      // using this renderer needs to be rendered.
      StateItem item_;
      public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int rowIndex, int colIndex) {

         StateTableData data = (StateTableData)table.getModel();
         item_ = data.getPropertyItem(rowIndex);

         if (isSelected) {
            setBackground(Color.LIGHT_GRAY);
         } else
            setBackground(Color.WHITE);

         if (hasFocus) {
            // this cell is the anchor and the table has the focus
         }

         // Configure the component with the specified value
         if (colIndex == 1) {
            setFont(new Font("Arial", Font.PLAIN, 10));
            setText(item_.config.toString());
         } else {
            setFont(new Font("Arial", Font.BOLD, 11));
            setText((String)value);
         }

         // Set tool tip if desired
         setToolTipText(item_.descr);

         // Since the renderer is a component, return itself
         return this;
      }

      // The following methods override the defaults for performance reasons
      public void validate() {}
      public void revalidate() {}
      protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {}
      public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}
      public PropertyCellRenderer() {
         super();
         setFont(new Font("Arial", Font.BOLD, 10));
         setOpaque(true);
      }
   }
}
