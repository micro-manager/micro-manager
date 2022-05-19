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
// CVS:          $Id: PropertyTableModel.java 6462 2011-02-09 22:43:55Z arthur $
//

package org.micromanager.internal.hcwizard;

import java.util.ArrayList;
import javax.swing.table.AbstractTableModel;
import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
import org.micromanager.internal.utils.MMPropertyTableModel;
import org.micromanager.internal.utils.PropertyItem;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Table model for device property tables. 
 */
class PropertyTableModel extends AbstractTableModel implements MMPropertyTableModel {
   private static final long serialVersionUID = 1L;
   public final String[] columnNames = new String[] {
         "Device",
         "Property",
         "Value"
   };
   
   // TODO: should be enum in Java 5.0
   public static final int PREINIT = 1;
   public static final int COMPORT = 2;
   
   MicroscopeModel model_;
   Device[] devices_;
   PropertyItem[] props_;
   String[] devNames_;
   DeviceSetupDlg setupDlg_;
   CMMCore core_;

   // OK to delete?
   public PropertyTableModel(MicroscopeModel model, int mode) {
      setupDlg_ = null;
      updateValues(model, mode, null);
   }

   /**
    * Handles single device case.
    */
   public PropertyTableModel(MicroscopeModel model, CMMCore core, Device dev, DeviceSetupDlg dlg) {
      core_ = core;
      setupDlg_ = dlg;
      updateValues(model, PREINIT, dev);
   }

   public void updateValues(MicroscopeModel model, int mode, Device dev) {
      model_ = model;
      if (dev == null) {
         if (mode == COMPORT) {
            devices_ = model.getAvailableSerialPorts();
         } else {
            devices_ = model.getDevices();
         }
      } else {
         devices_ = new Device[1];
         devices_[0] = dev;
      }
      
      model_.dumpComPortsSetupProps();

      ArrayList<PropertyItem> props = new ArrayList<>();
      ArrayList<String> dn = new ArrayList<>();
      for (Device device : devices_) {
         for (int j = 0; j < device.getNumberOfProperties(); j++) {
            PropertyItem p = device.getProperty(j);
            if (mode == PREINIT) {
               if (!p.readOnly && p.preInit && !device.isSerialPort()) {
                  props.add(p);
                  dn.add(device.getName());
                  PropertyItem setupProp = device.findSetupProperty(p.name);
                  if (setupProp != null) {
                     p.value = setupProp.value;
                  }
               }
            } else if (mode == COMPORT) {
               if (device.isSerialPort() && model_.isPortInUse(device) && !p.readOnly) {
                  props.add(p);
                  dn.add(device.getName());
                  PropertyItem setupProp = device.findSetupProperty(p.name);
                  if (setupProp != null) {
                     p.value = setupProp.value;
                  }
               }
            } else {
               if (!p.readOnly && !device.isSerialPort()) {
                  props.add(p);
                  dn.add(device.getName());
                  PropertyItem setupProp = device.findSetupProperty(p.name);
                  if (setupProp != null) {
                     p.value = setupProp.value;
                  }
               }
            }
         }
      }
      
      props_ = new PropertyItem[props.size()];
      devNames_ = new String[dn.size()];
      for (int i = 0; i < props.size(); i++) {
         props_[i] = props.get(i);
         devNames_[i] = dn.get(i);
      }
   }
   
   public int getRowCount() {
      return props_.length;
   }

   public int getColumnCount() {
      return columnNames.length;
   }

   @Override
   public String getColumnName(int columnIndex) {
      return columnNames[columnIndex];
   }

   public Object getValueAt(int rowIndex, int columnIndex) {
      if (columnIndex == 0) {
         return devNames_[rowIndex];
      } else if (columnIndex == 1) {
         return props_[rowIndex].name;
      } else {
         return props_[rowIndex].value;
      }
   }
   
   public void setValueAt(Object value, int row, int col) {
      if (col == 2) {
         try {
            Device dev = model_.findDevice(devNames_[row]);
            props_[row].value = (String) value;
            if (props_[row].name.compareTo(MMCoreJ.getG_Keyword_Port()) == 0 && setupDlg_ != null) {
               setupDlg_.rebuildComTable(props_[row].value);
            }
            // set the property in the device, so that it can change other pre-init properties
            dev.setPropertyValueInHardware(core_, props_[row].name, props_[row].value);
            // reload the device to update possibly change pre-init properties
            dev.loadDataFromHardware(core_);
            // the listener will rebuild the table to reflect possibly changed pre-init properties
            fireTableCellUpdated(row, col);
         } catch (Exception e) {
            ReportingUtils.logError(e.getMessage());
         }
      }
   }
   
   public boolean isCellEditable(int nRow, int nCol) {
      return nCol == 2 && !props_[nRow].readOnly;
   }
   
   public void refresh() {
      this.fireTableDataChanged();
   }
   
   public PropertyItem getPropertyItem(int rowIndex) {
      return props_[rowIndex];
   }
   
   public Setting getSetting(int rowIndex) {
      return new Setting(devNames_[rowIndex], props_[rowIndex].name, props_[rowIndex].value);
   }
   
   public PropertyItem getProperty(Setting s) {
      for (Device device : devices_) {
         if (device.getName().compareTo(s.deviceName_) == 0) {
            return device.findSetupProperty(s.propertyName_);
         }
      }
      return null;
   }
}
