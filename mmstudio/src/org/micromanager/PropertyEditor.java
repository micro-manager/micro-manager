///////////////////////////////////////////////////////////////////////////////
//FILE:          PropertyEditor.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, March 20, 2006
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

package org.micromanager;

/**
 * @author Nenad Amodaj
 * PropertyEditor provides UI for manipulating sets of device properties
 */

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.prefs.Preferences;

import javax.swing.AbstractCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpringLayout;
import javax.swing.border.BevelBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.PropertyType;
import mmcorej.StrVector;

import org.micromanager.api.DeviceControlGUI;
import org.micromanager.utils.MMFrame;
import org.micromanager.utils.ShowFlags;

import com.swtdesigner.SwingResourceManager;

/**
 * JFrame based component for generic manipulation of device properties.
 * Represents the entire system state as a list of triplets:
 * device - property - value
 */
public class PropertyEditor extends MMFrame {
   private SpringLayout springLayout;
   private static final long serialVersionUID = 1507097881635431043L;
   
   private JTable table_;
   private PropertyTableData data_;
   private DeviceControlGUI parentGUI_;
   private JCheckBox showReadonlyCheckBox_;
   private ShowFlags flags_;
   
   private static final String PREF_SHOW_READONLY = "show_readonly";
   private JCheckBox showCamerasCheckBox_;
   private JCheckBox showShuttersCheckBox_;
   private JCheckBox showStagesCheckBox_;
   private JCheckBox showStateDevicesCheckBox_;
   private JCheckBox showOtherCheckBox_;
   private JScrollPane scrollPane_;
    
   public PropertyEditor() {
      super();
      Preferences root = Preferences.userNodeForPackage(this.getClass());
      setPrefsNode(root.node(root.absolutePath() + "/PropertyEditor"));
      
      flags_ = new ShowFlags();
      flags_.load(getPrefsNode());
      
      setIconImage(SwingResourceManager.getImage(PropertyEditor.class, "icons/microscope.gif"));
      springLayout = new SpringLayout();
      getContentPane().setLayout(springLayout);
      setSize(551, 514);
      addWindowListener(new WindowAdapter() {
         public void windowClosing(WindowEvent e) {
            savePosition();
            Preferences prefs = getPrefsNode();
            prefs.putBoolean(PREF_SHOW_READONLY, showReadonlyCheckBox_.isSelected());
            flags_.save(getPrefsNode());
         }
         public void windowOpened(WindowEvent e) {
            // restore values from the previous session
            Preferences prefs = getPrefsNode();
            showReadonlyCheckBox_.setSelected(prefs.getBoolean(PREF_SHOW_READONLY, true));
            data_.updateStatus();
            data_.fireTableStructureChanged();
        }
      });
      setTitle("Property Browser");

      loadPosition(100, 100, 400, 300);
      setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

      scrollPane_ = new JScrollPane();
      scrollPane_.setFont(new Font("Arial", Font.PLAIN, 10));
      scrollPane_.setBorder(new BevelBorder(BevelBorder.LOWERED));
      getContentPane().add(scrollPane_);
      springLayout.putConstraint(SpringLayout.EAST, scrollPane_, -5, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, scrollPane_, 5, SpringLayout.WEST, getContentPane());
      
      table_ = new JTable();
      table_.setAutoCreateColumnsFromModel(false);
      
      final JButton refreshButton = new JButton();
      refreshButton.setIcon(SwingResourceManager.getIcon(PropertyEditor.class, "/org/micromanager/icons/arrow_refresh.png"));
      refreshButton.setFont(new Font("Arial", Font.PLAIN, 10));
      getContentPane().add(refreshButton);
      springLayout.putConstraint(SpringLayout.EAST, refreshButton, 285, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, refreshButton, 185, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, refreshButton, 32, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, refreshButton, 9, SpringLayout.NORTH, getContentPane());
      refreshButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            refresh();
         }
      });
      refreshButton.setText("Refresh! ");

      showReadonlyCheckBox_ = new JCheckBox();
      showReadonlyCheckBox_.setFont(new Font("Arial", Font.PLAIN, 10));
      showReadonlyCheckBox_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // show/hide read-only properties
            data_.setShowReadOnly(showReadonlyCheckBox_.isSelected());
            data_.updateStatus();
            data_.fireTableStructureChanged();
          }
      });
      showReadonlyCheckBox_.setText("Show read-only properties");
      getContentPane().add(showReadonlyCheckBox_);
      springLayout.putConstraint(SpringLayout.EAST, showReadonlyCheckBox_, 358, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, showReadonlyCheckBox_, 185, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, showReadonlyCheckBox_, 63, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, showReadonlyCheckBox_, 40, SpringLayout.NORTH, getContentPane());
      
      // restore values from the previous session
      Preferences prefs = getPrefsNode();
      showReadonlyCheckBox_.setSelected(prefs.getBoolean(PREF_SHOW_READONLY, true));

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
      springLayout.putConstraint(SpringLayout.EAST, showCamerasCheckBox_, 105, SpringLayout.WEST, getContentPane());

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
      showStateDevicesCheckBox_.setText("Show discrete changers");
      getContentPane().add(showStateDevicesCheckBox_);
      springLayout.putConstraint(SpringLayout.EAST, showStateDevicesCheckBox_, 200, SpringLayout.WEST, getContentPane());
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
      springLayout.putConstraint(SpringLayout.SOUTH, scrollPane_, -5, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, scrollPane_, 5, SpringLayout.SOUTH, showOtherCheckBox_);
   }
   
   protected void refresh() {
      data_.refresh();
   }

   public void updateStatus() {
      if (data_ != null)
         data_.updateStatus();
   }

   public void setCore(CMMCore core){
      data_ = new PropertyTableData(core, flags_);
      table_ = new JTable();
      table_.setAutoCreateColumnsFromModel(false);
      table_.setModel(data_);
      scrollPane_.setViewportView(table_);
     
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
      
      data_.setShowReadOnly(showReadonlyCheckBox_.isSelected());
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
      public boolean hasRange = false; // is there a range for values
      public double lowerLimit = 0.0;
      public double upperLimit = 0.0;
      public boolean isInt = false; // is this an integer property
   }
   

   /**
    * Property table data model, representing MMCore data
    */
   class PropertyTableData extends AbstractTableModel {
      private static final long serialVersionUID = 1L;

      final public String columnNames_[] = {
            "Property",
            "Value",
      };
      
      ArrayList<PropertyItem> propList_ = new ArrayList<PropertyItem>();
      private CMMCore core_ = null;
      private boolean showReadOnly_ = true;
      
      public PropertyTableData(CMMCore core, ShowFlags flags) {
         core_ = core;
         flags_ = flags;
         updateStatus();
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
         if(nCol == 1)
            return !propList_.get(nRow).readOnly;
         else
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
                     item.hasRange = core_.hasPropertyLimits(devices.get(i), properties.get(j));
                     item.lowerLimit = core_.getPropertyLowerLimit(devices.get(i), properties.get(j));
                     item.upperLimit = core_.getPropertyUpperLimit(devices.get(i), properties.get(j));
                     item.isInt = PropertyType.Integer == core_.getPropertyType(item.device, item.name);
                     
                     if ((showReadOnly_ && item.readOnly) || !item.readOnly)
                        propList_.add(item);
                  }
               }
            }
         } catch (Exception e) {
            handleException(e);
         }
         this.fireTableStructureChanged();
      }
      
      public boolean isShowReadOnly() {
         return showReadOnly_;
      }
      public void setShowReadOnly(boolean showReadOnly) {
         this.showReadOnly_ = showReadOnly;
      }      
   }
   
   /**
    * Cell editing using either JTextField or JComboBox depending on whether the
    * property enforces a set of allowed values.
    */
   public class PropertyCellEditor extends AbstractCellEditor implements TableCellEditor {
      private static final long serialVersionUID = 1L;
      // This is the component that will handle the editing of the cell value
      JTextField text_ = new JTextField();
      JComboBox combo_ = new JComboBox();
      JCheckBox check_ = new JCheckBox();
      SliderPanel slider_ = new SliderPanel();
      int editingCol_;
      PropertyItem item_;
      
      public PropertyCellEditor() {
         super();
         check_.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               fireEditingStopped();
            }
         });
         
         slider_.addEditActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               fireEditingStopped();
            }            
         });
         
         slider_.addSliderMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
               slider_.setPosition(e.getX());
            }
            public void mouseReleased(MouseEvent e) {
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
               if (item_.hasRange) {
                  Dimension d = slider_.getPreferredSize();
                  table_.setRowHeight(rowIndex, d.height);
                  if (item_.isInt)
                     slider_.setLimits((int)item_.lowerLimit, (int)item_.upperLimit);
                  else
                     slider_.setLimits(item_.lowerLimit, item_.upperLimit);
                  slider_.setText((String)value);
                  return slider_;
               } else {
                  text_.setText((String)value);
                  return text_;
               }
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
            if (item_.allowed.length == 0) {
               if (item_.hasRange)
                  return slider_.getText();
               else
                  return text_.getText();
            } else {
               return combo_.getSelectedItem();
            }
         } else if (editingCol_ == 2)
            return check_;
         
         return null;
      }
   }
   
   /**
    * Cell rendering for the device property table
    */
   public class PropertyCellRenderer implements TableCellRenderer {
      // This method is called each time a cell in a column
      // using this renderer needs to be rendered.
      PropertyItem item_;
      
      public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int rowIndex, int colIndex) {
         
         PropertyTableData data = (PropertyTableData)table.getModel();
         item_ = data.getPropertyItem(rowIndex);
         
         if (isSelected) {
            // cell (and perhaps other cells) are selected
         }
         
         if (hasFocus) {
            // this cell is the anchor and the table has the focus
         }
         
         Component comp;
         
         if (colIndex == 0) {
            JLabel lab = new JLabel();
            lab.setText((String)value);
            lab.setOpaque(true);
            lab.setHorizontalAlignment(JLabel.LEFT);
            comp = lab;
         } else if (colIndex == 1) {
            if (item_.hasRange) {
               SliderPanel slider = new SliderPanel();
               slider.setLimits(item_.lowerLimit, item_.upperLimit);
               slider.setText((String)value);
               slider.setToolTipText((String)value);
               comp = slider;
            } else {
               JLabel lab = new JLabel();
               lab.setOpaque(true);
               lab.setText(item_.value.toString());
               lab.setHorizontalAlignment(JLabel.LEFT);
               comp = lab;
            }
         } else {
            comp = new JLabel("Undefinded");
         }
         
         if (item_.readOnly) {
            comp.setBackground(Color.LIGHT_GRAY);
         } else {
            comp.setBackground(Color.white);
         }         
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
}

