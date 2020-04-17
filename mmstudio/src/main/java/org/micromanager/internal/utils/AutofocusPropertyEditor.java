///////////////////////////////////////////////////////////////////////////////
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

package org.micromanager.internal.utils;

/**
 * @author Nenad Amodaj
 * PropertyEditor provides UI for manipulating sets of autofocus properties
 */

import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.ParseException;
import java.util.ArrayList;
import javax.swing.AbstractCellEditor;
import javax.swing.ImageIcon;
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
import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.internal.MMStudio;

/**
 * JFrame based component for generic manipulation of device properties.
 * Represents the entire system state as a list of triplets:
 * device - property - value
 */
public final class AutofocusPropertyEditor extends MMDialog {
   private final Studio studio_;
   private final SpringLayout springLayout;
   private static final long serialVersionUID = 1507097881635431043L;
   
   private JTable table_;
   private PropertyTableData data_;
   private final PropertyCellEditor cellEditor_;
   private JCheckBox showReadonlyCheckBox_;
   
   private static final String PREF_SHOW_READONLY = "show_readonly";
   private final JScrollPane scrollPane_;
   private final DefaultAutofocusManager afMgr_;
   private final JButton btnClose;
   private JComboBox methodCombo_;
   
   public AutofocusPropertyEditor(Studio studio, DefaultAutofocusManager afmgr) {
      super("autofocus property editor");
      studio_ = studio;
      afMgr_ = afmgr;
      setModal(false);
      data_ = new PropertyTableData();
      table_ = new DaytimeNighttime.Table();
      table_.setAutoCreateColumnsFromModel(false);
      table_.setModel(data_);
     
      cellEditor_ = new PropertyCellEditor();
      PropertyCellRenderer renderer = new PropertyCellRenderer(studio);
     
      for (int k=0; k < data_.getColumnCount(); k++) {
         TableColumn column = new TableColumn(k, 200, renderer, cellEditor_);
         table_.addColumn(column);
      }
            
      springLayout = new SpringLayout();
      getContentPane().setLayout(springLayout);
      setSize(551, 514);
      final UserProfile profile = MMStudio.getInstance().profile();
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
            cleanup();
         }
         
         @Override
         public void windowOpened(WindowEvent e) {
            // restore values from the previous session
            showReadonlyCheckBox_.setSelected(
               profile.getSettings(AutofocusPropertyEditor.class).getBoolean(
                  PREF_SHOW_READONLY, true));
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
      
      table_ = new DaytimeNighttime.Table();
      table_.setAutoCreateColumnsFromModel(false);
      
      final JButton refreshButton = new JButton();
      springLayout.putConstraint(SpringLayout.NORTH, refreshButton, 10, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, refreshButton, 10, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, refreshButton, 33, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, refreshButton, 110, SpringLayout.WEST, getContentPane());
      refreshButton.setIcon(new ImageIcon(getClass().getResource(
              "/org/micromanager/icons/arrow_refresh.png")));
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
      showReadonlyCheckBox_.setSelected(profile.getSettings(
               AutofocusPropertyEditor.class).getBoolean(PREF_SHOW_READONLY, true));
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
         if (afMgr_.getAutofocusMethod() != null) {
            methodCombo_.setSelectedItem(afMgr_.getAutofocusMethod().getName());
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
      cellEditor_.stopEditing();
      afMgr_.setAutofocusMethodByName(focusDev);

      updateStatus();
   }

   protected void refresh() {
      data_.refresh();
   }

   public void rebuild() {
      String afDevice = afMgr_.getAutofocusMethod().getName();
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
            if (afMgr_.getAutofocusMethod() != null) {
               methodCombo_.setSelectedItem(afMgr_.getAutofocusMethod().getName());
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
      studio_.profile().getSettings(AutofocusPropertyEditor.class).
              putBoolean(PREF_SHOW_READONLY, showReadonlyCheckBox_.isSelected());
      if (afMgr_ != null)
         if (afMgr_.getAutofocusMethod() != null) {
            afMgr_.getAutofocusMethod().applySettings();
            afMgr_.getAutofocusMethod().saveSettings();
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
         if (col == 1 && afMgr_.getAutofocusMethod() != null) {
            try {
               if (item.isInteger()) {
                  afMgr_.getAutofocusMethod().setPropertyValue(item.name, NumberUtils.intStringDisplayToCore(value));
               } else if (item.isFloat()) {
                  afMgr_.getAutofocusMethod().setPropertyValue(item.name, NumberUtils.doubleStringDisplayToCore(value));
               } else  {
                  afMgr_.getAutofocusMethod().setPropertyValue(item.name, value.toString());
               }

               refresh();

               fireTableCellUpdated(row, col);
            } catch (Exception e) {
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
         if (afMgr_.getAutofocusMethod() == null)
            return;
         
         try {            
        	
            for (PropertyItem item : propList_) {
               item.value = afMgr_.getAutofocusMethod().getPropertyValue(item.name);
            }
        	
            this.fireTableDataChanged();
         } catch (Exception e) {
            handleException(e);
         }
      }
      
      public void updateStatus(){
         
         propList_.clear();                
         PropertyItem properties[] = new PropertyItem[0];
         
         if (afMgr_.getAutofocusMethod() != null)
            properties = afMgr_.getAutofocusMethod().getProperties();
         
         for (PropertyItem propertie : properties) {
            if (!propertie.preInit) {
               if ((showReadOnly_ && propertie.readOnly) || !propertie.readOnly) {
                  propList_.add(propertie);
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
   public final class PropertyCellEditor extends AbstractCellEditor implements TableCellEditor {
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
            for (ActionListener l1 : l) {
               combo_.removeActionListener(l1);
            }
            combo_.removeAllItems();
            for (String allowed : item_.allowed) {
               combo_.addItem(allowed);
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
   public final class PropertyCellRenderer implements TableCellRenderer {
      // This method is called each time a cell in a column
      // using this renderer needs to be rendered.
      PropertyItem item_;
      Studio studio_;
      
      public PropertyCellRenderer(Studio studio) {
         super();
         studio_ = studio;
      }
      
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
            comp.setBackground(studio_.app().skin().getDisabledBackgroundColor());
            comp.setForeground(studio_.app().skin().getDisabledTextColor());
         } else {
            comp.setBackground(studio_.app().skin().getBackgroundColor());
            comp.setForeground(studio_.app().skin().getEnabledTextColor());
         }         
         return comp;
      }
      
      // The following methods override the defaults for performance reasons
      public void validate() {}
      public void revalidate() {}
      protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {}
      public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}

   }
}

