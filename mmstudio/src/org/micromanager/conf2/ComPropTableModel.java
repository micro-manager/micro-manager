package org.micromanager.conf2;

import java.util.ArrayList;

import javax.swing.table.AbstractTableModel;
import org.micromanager.utils.MMPropertyTableModel;
import org.micromanager.utils.PropertyItem;
import org.micromanager.utils.ReportingUtils;

/**
 * Table model for device property tables. 
 */
class ComPropTableModel extends AbstractTableModel implements MMPropertyTableModel {
   private static final long serialVersionUID = 1L;
   public final String[] COLUMN_NAMES = new String[] {
         "Device",
         "Property",
         "Value"
   };
   
   MicroscopeModel model_;
   Device dev_;
   PropertyItem props_[];
   
   public ComPropTableModel(MicroscopeModel model, Device portDev) {
      updateValues(model, portDev);
   }

   public void updateValues(MicroscopeModel model, Device dev) {
      model_ = model;
      dev_ = dev;
      try {
         System.out.println("InitPort " + dev_.getPropertyValue("BaudRate"));
      } catch (MMConfigFileException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
      
      if (!dev.isSerialPort())
         return;
      
      model_.dumpComPortsSetupProps(); // ?
      
      ArrayList<PropertyItem> props = new ArrayList<PropertyItem>();
      ArrayList<String> dn = new ArrayList<String>();
      for (int i=0; i<dev_.getNumberOfProperties(); i++) {
         PropertyItem p = dev_.getProperty(i);
         if (!p.readOnly) {
            props.add(p);
            dn.add(dev_.getName());
            PropertyItem setupProp = dev_.findSetupProperty(p.name);
            if (setupProp != null)
               p.value = setupProp.value;
          
         }
      }
      
      props_ = new PropertyItem[props.size()];
      for (int i=0; i<props.size(); i++) {
         props_[i] = props.get(i);
      }
   }
   
   public int getRowCount() {
      return props_.length;
   }
   public int getColumnCount() {
      return COLUMN_NAMES.length;
   }
   @Override
   public String getColumnName(int columnIndex) {
      return COLUMN_NAMES[columnIndex];
   }
   public Object getValueAt(int rowIndex, int columnIndex) {
      
      if (columnIndex == 0)
         return dev_.getName();
      else if (columnIndex == 1)
         return props_[rowIndex].name;
      else
         return props_[rowIndex].value;
   }
   
   public void setValueAt(Object value, int row, int col) {
      if (col == 2) {
         try {
            props_[row].value = (String)value;
            dev_.setPropertyValue(props_[row].name, props_[row].value);
            fireTableCellUpdated(row, col);
            System.out.println("setVal " + dev_.getPropertyValue("BaudRate"));
         } catch (Exception e) {
            ReportingUtils.logError(e.getMessage());
         }
      }
   }
   
   public boolean isCellEditable(int nRow, int nCol) {
      if(nCol == 2 && !props_[nRow].readOnly)
         return true;
      else
         return false;
   }
   
   public void refresh() {
      this.fireTableDataChanged();
   }
   
   public PropertyItem getPropertyItem(int rowIndex) {
      return props_[rowIndex];
   }
   
   public Setting getSetting(int rowIndex) {
      return new Setting(dev_.getName(), props_[rowIndex].name, props_[rowIndex].value);
   }
   
   public PropertyItem getProperty(Setting s) {
      if (dev_.getName().compareTo(s.deviceName_) == 0)
         return dev_.findSetupProperty(s.propertyName_);
      return null;
   }
   
   public Device getPortDevice() {
      return dev_;
   }
}