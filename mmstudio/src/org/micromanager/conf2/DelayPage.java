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
// CVS:          $Id: DelayPage.java 3761 2010-01-14 02:38:08Z arthur $
//
package org.micromanager.conf2;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.prefs.Preferences;

import javax.swing.CellEditor;
import javax.swing.InputMap;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import org.micromanager.conf2.DevicesPage.DeviceTable_TableModel;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * Wizard page to set device delays.
 *
 */
public class DelayPage extends PagePanel {
   private static final long serialVersionUID = 1L;

   class DelayTableModel extends AbstractTableModel {
      private static final long serialVersionUID = 1L;

      public final String[] COLUMN_NAMES = new String[] {
            "Name",
            "Adapter",
            "Delay [ms]"
      };
      
      MicroscopeModel model_;
      ArrayList<Device> devices_;
      
      public DelayTableModel(MicroscopeModel model) {
         devices_ = new ArrayList<Device>();
         Device allDevices[] = model.getDevices();
         for (int i=0; i<allDevices.length; i++) {
            if (allDevices[i].usesDelay())
               devices_.add(allDevices[i]);
         }
         model_ = model;
      }
      
      public void setMicroscopeModel(MicroscopeModel mod) {
         Device allDevices[] = mod.getDevices();
         for (int i=0; i<allDevices.length; i++) {
            if (allDevices[i].usesDelay())
            devices_.add(allDevices[i]);
         }
         model_ = mod;
      }
      
      public int getRowCount() {
         return devices_.size();
      }
      public int getColumnCount() {
         return COLUMN_NAMES.length;
      }
      public String getColumnName(int columnIndex) {
         return COLUMN_NAMES[columnIndex];
      }
      
      public Object getValueAt(int rowIndex, int columnIndex) {
         
         if (columnIndex == 0)
            return devices_.get(rowIndex).getName();
         else if (columnIndex == 1)
            return devices_.get(rowIndex).getAdapterName();
         else
            return new Double(devices_.get(rowIndex).getDelay());
      }
      public void setValueAt(Object value, int row, int col) {
         if (col == 2) {
            try {
               devices_.get(row).setDelay(Double.parseDouble((String)value));
               fireTableCellUpdated(row, col);
            } catch (Exception e) {
               ReportingUtils.logError(e);
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
         Device allDevices[] = model_.getDevices();
         for (int i=0; i<allDevices.length; i++) {
            if (allDevices[i].usesDelay())
            devices_.add(allDevices[i]);
         }
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
      scrollPane.setBounds(22, 21, 517, 337);
      add(scrollPane);

      deviceTable_ = new JTable();
      deviceTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      InputMap im = deviceTable_.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      im.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, 0 ), "none" );
      scrollPane.setViewportView(deviceTable_);
      GUIUtils.setClickCountToStartEditing(deviceTable_,1);
      GUIUtils.stopEditingOnLosingFocus(deviceTable_);
   }

   public boolean enterPage(boolean next) {      
      rebuildTable();
      return true;
  }

   public boolean exitPage(boolean next) {
      CellEditor ce = deviceTable_.getCellEditor();
      if (ce != null) {
        deviceTable_.getCellEditor().stopCellEditing();
      }
      // apply delays to hardware
      try {

         model_.applyDelaysToHardware(core_);
      } catch (Exception e) {
         ReportingUtils.logError(e);
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
