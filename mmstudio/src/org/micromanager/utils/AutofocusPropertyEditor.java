///////////////////////////////////////////////////////////////////////////////
//FILE:          AutofocusPropertyEditor.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com
//
// COPYRIGHT:    100X Imaging Inc, San Francisco, 2009
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

package org.micromanager.utils;

/**
 * @author Nenad Amodaj
 * PropertyEditor provides UI for manipulating sets of autofocus properties
 */

import java.awt.Color;
import java.awt.Component;
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
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpringLayout;
import javax.swing.border.BevelBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import com.swtdesigner.SwingResourceManager;
import java.text.ParseException;

/**
 * JFrame based component for generic manipulation of device properties.
 * Represents the entire system state as a list of triplets:
 * device - property - value
 */
public class AutofocusPropertyEditor extends MMDialog {
   private final SpringLayout springLayout;
   private static final long serialVersionUID = 1507097881635431043L;
   
   private JTable table_;
   private PropertyTableData data_;
   private final PropertyCellEditor cellEditor_;
   private JCheckBox showReadonlyCheckBox_;
   
   private static final String PREF_SHOW_READONLY = "show_readonly";
   private final JScrollPane scrollPane_;
   private final AutofocusManager afMgr_;
   private final JButton btnClose;
   private JComboBox methodCombo_;
   
   public AutofocusPropertyEditor(AutofocusManager afmgr) {
      super();
      afMgr_ = afmgr;
      setModal(false);
      data_ = new PropertyTableData();
      table_ = new JTable();
      table_.setAutoCreateColumnsFromModel(false);
      table_.setModel(data_);
     
      cellEditor_ = new PropertyCellEditor();
      PropertyCellRenderer renderer = new PropertyCellRenderer();
     
      for (int k=0; k < data_.getColumnCount(); k++) {
         TableColumn column = new TableColumn(k, 200, renderer, cellEditor_);
         table_.addColumn(column);
      }
            
      //Preferences root = Preferences.userNodeForPackage(this.getClass());
      //setPrefsNode(root.node(root.absolutePath() + "/AutofocusPropertyEditor"));
      
      springLayout = new SpringLayout();
      getContentPane().setLayout(springLayout);
      setSize(551, 514);
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
            cleanup();
         }
         
         @Override
         public void windowOpened(WindowEvent e) {
            // restore values from the previous session
            Preferences prefs = getPrefsNode();
            showReadonlyCheckBox_.setSelected(prefs.getBoolean(PREF_SHOW_READONLY, true));
            data_.updateStatus();
            data_.fireTableStructureChanged();
        }
      });
      setTitle("Autofocus properties");

      loadAndRestorePosition(100, 100, 400, 300);

      scrollPane_ = new JScrollPane();
      scrollPane_.setFont(new Font("Arial", Font.PLAIN, 10));
      scrollPane_.setBorder(new BevelBorder(BevelBorder.LOWERED));
      getContentPane().add(scrollPane_);
      springLayout.putConstraint(SpringLayout.SOUTH, scrollPane_, -5, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, scrollPane_, 70, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, scrollPane_, -5, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, scrollPane_, 5, SpringLayout.WEST, getContentPane());
      
      scrollPane_.setViewportView(table_);
      
      table_ = new JTable();
      table_.setAutoCreateColumnsFromModel(false);
      
      final JButton refreshButton = new JButton();
      springLayout.putConstraint(SpringLayout.NORTH, refreshButton, 10, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, refreshButton, 10, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, refreshButton, 33, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, refreshButton, 110, SpringLayout.WEST, getContentPane());
      refreshButton.setIcon(SwingResourceManager.getIcon(AutofocusPropertyEditor.class, "/org/micromanager/icons/arrow_refresh.png"));
      refreshButton.setFont(new Font("Arial", Font.PLAIN, 10));
      getContentPane().add(refreshButton);
      refreshButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            refresh();
         }
      });
      refreshButton.setText("Refresh! ");

      showReadonlyCheckBox_ = new JCheckBox();
      springLayout.putConstraint(SpringLayout.NORTH, showReadonlyCheckBox_, 41, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, showReadonlyCheckBox_, 10, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, showReadonlyCheckBox_, 64, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, showReadonlyCheckBox_, 183, SpringLayout.WEST, getContentPane());
      showReadonlyCheckBox_.setFont(new Font("Arial", Font.PLAIN, 10));
      showReadonlyCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            // show/hide read-only properties
            data_.setShowReadOnly(showReadonlyCheckBox_.isSelected());
            data_.updateStatus();
            data_.fireTableStructureChanged();
          }
      });
      showReadonlyCheckBox_.setText("Show read-only properties");
      getContentPane().add(showReadonlyCheckBox_);
      
      // restore values from the previous session
      Preferences prefs = getPrefsNode();
      showReadonlyCheckBox_.setSelected(prefs.getBoolean(PREF_SHOW_READONLY, true));
      {
         btnClose = new JButton("Close");
         btnClose.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
               cleanup();
               dispose();
            }
         });
         springLayout.putConstraint(SpringLayout.SOUTH, btnClose, 0, SpringLayout.SOUTH, refreshButton);
         springLayout.putConstraint(SpringLayout.EAST, btnClose, -10, SpringLayout.EAST, getContentPane());
         getContentPane().add(btnClose);
      }
      
      if (afMgr_ != null) {
         methodCombo_ = new JComboBox();
         String afDevs[] = afMgr_.getAfDevices();
         for (String devName : afDevs) {
            methodCombo_.addItem(devName);
         }
         if (afMgr_.getDevice() != null) {
            methodCombo_.setSelectedItem(afMgr_.getDevice().getDeviceName());
         } 
         methodCombo_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
               changeAFMethod((String)methodCombo_.getSelectedItem());
            }
         });
         springLayout.putConstraint(SpringLayout.WEST, methodCombo_, 80, SpringLayout.EAST, refreshButton);
         springLayout.putConstraint(SpringLayout.SOUTH, methodCombo_, 0, SpringLayout.SOUTH, refreshButton);
         springLayout.putConstraint(SpringLayout.EAST, methodCombo_, -6, SpringLayout.WEST, btnClose);
         getContentPane().add(methodCombo_);
      }
      
      data_.setShowReadOnly(showReadonlyCheckBox_.isSelected());
   }
   
   protected void changeAFMethod(String focusDev) {
      try  {
         cellEditor_.stopEditing();
         afMgr_.selectDevice(focusDev);
      } catch (MMException e) {
         handleException(e);
      }
      
      updateStatus();
   }

   protected void refresh() {
      data_.refresh();
   }

   public void rebuild() {
      String afDevice = afMgr_.getDevice().getDeviceName();
      ActionListener l = methodCombo_.getActionListeners()[0];
      
      try {
         if (l != null)
            methodCombo_.removeActionListener(l);
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
       
      methodCombo_.removeAllItems();
      if (afMgr_ != null) {
         String afDevs[] = afMgr_.getAfDevices();
         for (String devName : afDevs) {
            methodCombo_.addItem(devName);
         }
         methodCombo_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
               changeAFMethod((String)methodCombo_.getSelectedItem());
            }
         });
         if (afDevice != null)
            methodCombo_.setSelectedItem(afDevice);
         else
            if (afMgr_.getDevice() != null) {
               methodCombo_.setSelectedItem(afMgr_.getDevice().getDeviceName());
         }
      }
   }

   public void updateStatus() {
      if (data_ != null)
         data_.updateStatus();
   }
   
   private void handleException (Exception e) {
      ReportingUtils.showError(e);
   }
         

   public void cleanup() {
      getPrefsNode().putBoolean(PREF_SHOW_READONLY, 
              showReadonlyCheckBox_.isSelected());
      if (afMgr_ != null)
         if (afMgr_.getDevice() != null) {
            afMgr_.getDevice().applySettings();
            afMgr_.getDevice().saveSettings();
         }
   }


   /**
    * Property table data model, representing MMCore data
    */
   final class PropertyTableData extends AbstractTableModel {
      private static final long serialVersionUID = 1L;

      final public String columnNames_[] = {
            "Property",
            "Value",
      };
      
      ArrayList<PropertyItem> propList_ = new ArrayList<PropertyItem>();
      private boolean showReadOnly_ = true;
      
      public PropertyTableData() {
         updateStatus();
      }
      
      public void setShowReadOnly(boolean show) {
         showReadOnly_ = show;
      }
                       
      @Override
      public int getRowCount() {
         return propList_.size();
      }
      
      @Override
      public int getColumnCount() {
         return columnNames_.length;
      }
      
      public PropertyItem getPropertyItem(int row) {
         return propList_.get(row);
      }
      
      @Override
      public Object getValueAt(int row, int col) {
         
         PropertyItem item = propList_.get(row);
         if (col == 0)
            return item.device + "-" + item.name;
         else if (col == 1)
            return item.value;
         
         return null;
      }
      
      @Override
      public void setValueAt(Object value, int row, int col) {
         PropertyItem item = propList_.get(row);
         if (col == 1 && afMgr_.getDevice() != null) {
            try {
               if (item.isInteger()) {
                  afMgr_.getDevice().setPropertyValue(item.name, NumberUtils.intStringDisplayToCore(value));
               } else if (item.isFloat()) {
                  afMgr_.getDevice().setPropertyValue(item.name, NumberUtils.doubleStringDisplayToCore(value));
               } else  {
                  afMgr_.getDevice().setPropertyValue(item.name, value.toString());
               }

               refresh();

               fireTableCellUpdated(row, col);
            } catch (ParseException e) {
               handleException(e);
            } catch (MMException e) {
               handleException(e);
            }
         }
      }
      
      @Override
      public String getColumnName(int column) {
         return columnNames_[column];
      }
      
      @Override
      public boolean isCellEditable(int nRow, int nCol) {
         if(nCol == 1)
            return !propList_.get(nRow).readOnly;
         else
            return false;
      }
            
      public void refresh(){
         if (afMgr_.getDevice() == null)
            return;
         
         try {            
        	
            for (int i=0; i<propList_.size(); i++){
               PropertyItem item = propList_.get(i);
               item.value = afMgr_.getDevice().getPropertyValue(item.name);
            }
        	
            this.fireTableDataChanged();
         } catch (MMException e) {
            handleException(e);
         }
      }
      
      public void updateStatus(){
         
         propList_.clear();                
         PropertyItem properties[] = new PropertyItem[0];
         
         if (afMgr_.getDevice() != null)
            properties = afMgr_.getDevice().getProperties();
         
         for (int j=0; j<properties.length; j++){  
            if (!properties[j].preInit) {
               if ((showReadOnly_ && properties[j].readOnly) || !properties[j].readOnly) {
                  propList_.add(properties[j]);
               }
            }
         }
         this.fireTableStructureChanged();
      }   
      
      public boolean isShowReadOnly() {
         return showReadOnly_;
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
            @Override
            public void actionPerformed(ActionEvent e) {
               fireEditingStopped();
            }
         });
         
         slider_.addEditActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               fireEditingStopped();
            }            
         });
         
         slider_.addSliderMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
               fireEditingStopped();
            }
         });
      }
      
      public void stopEditing() {
         fireEditingStopped();
      }

      // This method is called when a cell value is edited by the user.
      @Override
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
                  if (item_.isInteger())
                     slider_.setLimits((int)item_.lowerLimit, (int)item_.upperLimit);
                  else {
                     slider_.setLimits(item_.lowerLimit, item_.upperLimit);
                /*     try {
                        value = NumberUtils.NumberToString(Double.parseDouble((String)value));
                     } catch (Exception e) {
                        ReportingUtils.logError(e);
                     }*/
                  }
                  try {
                     slider_.setText((String)value);
                  } catch (ParseException ex) {
                    ReportingUtils.logError(ex);
                  }
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
               @Override
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
      @Override
      public Object getCellEditorValue() {
         if (editingCol_ == 1) {
            if (item_.allowed.length == 0) {
               if (item_.hasRange) {
                  return slider_.getText();
               } else
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
      
      @Override
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
               try {
                  slider.setText((String)value);
               } catch (ParseException ex) {
                  ReportingUtils.logError(ex);
               }
               slider.setToolTipText((String)value);
               comp = slider;
            } else {
               JLabel lab = new JLabel();
               lab.setOpaque(true);
               lab.setText(item_.value);
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
      }
   }
}

