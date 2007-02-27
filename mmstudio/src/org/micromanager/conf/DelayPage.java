///////////////////////////////////////////////////////////////////////////////
//FILE:          DelayPage.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, December 2, 2006
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

import java.util.prefs.Preferences;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import org.micromanager.conf.DevicesPage.DeviceTable_TableModel;

/**
 * Wizard page to set device delays.
 *
 */
public class DelayPage extends PagePanel {

   class DelayTableModel extends AbstractTableModel {
      public final String[] COLUMN_NAMES = new String[] {
            "Name",
            "Adapter",
            "Delay [ms]"
      };
      
      MicroscopeModel model_;
      Device devices_[];
      
      public DelayTableModel(MicroscopeModel model) {
         devices_ = model.getDevices();
         model_ = model;
      }
      
      public void setMicroscopeModel(MicroscopeModel mod) {
         devices_ = mod.getDevices();
         model_ = mod;
      }
      
      public int getRowCount() {
         return devices_.length;
      }
      public int getColumnCount() {
         return COLUMN_NAMES.length;
      }
      public String getColumnName(int columnIndex) {
         return COLUMN_NAMES[columnIndex];
      }
      
      public Object getValueAt(int rowIndex, int columnIndex) {
         
         if (columnIndex == 0)
            return devices_[rowIndex].getName();
         else if (columnIndex == 1)
            return devices_[rowIndex].getAdapterName();
         else
            return new Double(devices_[rowIndex].getDelay());
      }
      public void setValueAt(Object value, int row, int col) {
         if (col == 2) {
            try {
               devices_[row].setDelay(Double.parseDouble((String)value));
               fireTableCellUpdated(row, col);
            } catch (Exception e) {
               handleError(e.getMessage());
            }
         }
      }
     
      public boolean isCellEditable(int nRow, int nCol) {
         if(nCol == 2)
            return true;
         else
            return false;
      }
      
      public void refresh() {
         devices_ = model_.getDevices();
         this.fireTableDataChanged();
      }
   }

   
   private JTable deviceTable_;
   /**
    * Create the panel
    */
   public DelayPage(Preferences prefs) {
      super();
      title_ = "Set delays for devices without synchronization capabilities";
      helpText_ = "Some devices can't signal when they are done with the command, so that we have to guess by manually setting the delay. " +
      "This means that the device will signal to be busy for the specified delay time after extecuting each command." +
      " Devices that may require setting the delay manually are mostly shutters or filter wheels. " +
      "\n\nIf device has normal synchronization capabilities, or you are not sure about it, leave this parameter at 0.";
      setHelpFileName("conf_delays_page.html");
      prefs_ = prefs;
      setLayout(null);

      final JScrollPane scrollPane = new JScrollPane();
      scrollPane.setBounds(22, 21, 357, 199);
      add(scrollPane);

      deviceTable_ = new JTable();
      scrollPane.setViewportView(deviceTable_);
      //
   }

   public boolean enterPage(boolean next) {      
      rebuildTable();
      return true;
  }

   public boolean exitPage(boolean next) {
      // apply delays to hardware
      try {
         model_.applyDelaysToHardware(core_);
      } catch (Exception e) {
         handleError(e.getMessage());
         if (next)
            return false; // refuse to go to the next page
      }
      return true;
   }
   
   private void rebuildTable() {
      TableModel tm = deviceTable_.getModel();
      DelayTableModel tmd;
      if (tm instanceof DeviceTable_TableModel) {
         tmd = (DelayTableModel) deviceTable_.getModel();
         tmd.refresh();
      } else {
         tmd = new DelayTableModel(model_);
         deviceTable_.setModel(tmd);
      }
      tmd.fireTableStructureChanged();
      tmd.fireTableDataChanged();
   }
   
   public void refresh() {
      rebuildTable();
   }

   public void loadSettings() {
   }

   public void saveSettings() {
   }

}
