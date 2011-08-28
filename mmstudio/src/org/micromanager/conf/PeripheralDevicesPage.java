///////////////////////////////////////////////////////////////////////////////
//FILE:          PeripheralDevicesPage.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Karl Hoover
//
// COPYRIGHT:    University of California, San Francisco, 2011
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
// CVS:          $Id: PeripheralDevicesPage.java 6620 2011-02-25 23:47:13Z karlh $
//
package org.micromanager.conf;

import java.awt.Container;
import java.awt.Cursor;
import java.util.Vector;
import java.util.prefs.Preferences;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import mmcorej.MMCoreJ;
import mmcorej.StrVector;
import org.micromanager.utils.ReportingUtils;

/**
 * Wizard page to add or remove devices.
 */
public class PeripheralDevicesPage extends PagePanel {

   private static final long serialVersionUID = 1L;
   private static final int HUBCOLUMN = 0;
   private static final int NAMECOLUMN = 1;
   private static final int ADAPTERCOLUMN = 2;
   private static final int DESCRIPTIONCOLUMN = 3;
   private static final int SELECTIONCOLUMN = 4;

   class DeviceTable_TableModel extends AbstractTableModel {

      private static final long serialVersionUID = 1L;
      public final String[] COLUMN_NAMES = new String[]{
         "Hub",
         "Name",
         "Adapter/Library",
         "Description",
         "Selected"
      };
      MicroscopeModel model_;
      Device devices_[];
      Vector<Device> peripheralDevices_;
      Vector<Boolean> selected_;
      Vector<String> masterDevices_;

      public DeviceTable_TableModel(MicroscopeModel model) {
         peripheralDevices_ = new Vector<Device>();
         selected_ = new Vector<Boolean>();
         masterDevices_ = new Vector<String>();
         setMicroscopeModel(model);
      }

      public void setMicroscopeModel(MicroscopeModel mod) {
         model_ = mod;
         rebuild();
      }

      public int getRowCount() {
         return peripheralDevices_.size();
      }

      public int getColumnCount() {
         return COLUMN_NAMES.length;
      }

      @Override
      public String getColumnName(int columnIndex) {
         return COLUMN_NAMES[columnIndex];
      }

      @Override
      public Class getColumnClass(int c) {
         Class ret = String.class;
         if (SELECTIONCOLUMN == c) {
            ret = Boolean.class;
         }
         return ret;
      }

      public Object getValueAt(int rowIndex, int columnIndex) {

         if (HUBCOLUMN == columnIndex) {
            return masterDevices_.get(rowIndex);
         } else if (columnIndex == NAMECOLUMN) {
            return peripheralDevices_.get(rowIndex).getName();
         } else if (columnIndex == ADAPTERCOLUMN) {
            return new String(peripheralDevices_.get(rowIndex).getAdapterName() + "/" + peripheralDevices_.get(rowIndex).getLibrary());
         } else if (columnIndex == DESCRIPTIONCOLUMN) {
            return peripheralDevices_.get(rowIndex).getDescription();
         } else if (SELECTIONCOLUMN == columnIndex) {
            return selected_.get(rowIndex);
         } else {
            return null;
         }
      }

      @Override
      public void setValueAt(Object value, int row, int col) {
         switch (col) {
            case HUBCOLUMN:
               break;
            case NAMECOLUMN: {
               String n = (String) value;
               String o = peripheralDevices_.get(row).getName();
               try {

                  //  NOT YET!  model_.changeDeviceName(o, n);
                  fireTableCellUpdated(row, col);
               } catch (Exception e) {
                  handleError(e.getMessage());
               }
            }
            break;
            case ADAPTERCOLUMN:
               break;
            case DESCRIPTIONCOLUMN:
               break;
            case SELECTIONCOLUMN:
               selected_.set(row, (Boolean) value);
               break;
         }
      }

      @Override
      public boolean isCellEditable(int nRow, int nCol) {
         boolean ret = false;
         switch (nCol) {
            case HUBCOLUMN:
               break;
            case NAMECOLUMN:
               ret = true;
               break;
            case ADAPTERCOLUMN:
               break;
            case DESCRIPTIONCOLUMN:
               break;
            case SELECTIONCOLUMN:
               ret = true;
               break;
         }
         return ret;
      }

      public void refresh() {
         devices_ = model_.getDevices();
         this.fireTableDataChanged();
      }

      Vector<Device> getPeripheralDevices() {
         return peripheralDevices_;
      }

      ;

      Vector<Boolean> getSelected() {
         return selected_;
      }

      ;

      Vector<String> getMasterDevices() {
         return masterDevices_;
      }

      public void rebuild() {
         devices_ = model_.getDevices();
         masterDevices_.clear();
         peripheralDevices_.clear();
         selected_.clear();

         for (Device d : devices_) {
            if (!d.getName().equals("Core")) {

               // device "discovery" happens here
               StrVector installed = core_.getInstalledDevices(d.getName());
               // end of discovery

               if (0 < installed.size()) {
                  for (int i=0; i<installed.size(); i++) {                        
                     try {
                        if (model_.findDevice(installed.get(i)) == null)
                        {
                           String descr = model_.getDeviceDescription(d.getLibrary(), installed.get(i));
                           Device newDev = new Device(installed.get(i), d.getLibrary(), installed.get(i), descr);   
                           selected_.add(false);
                           masterDevices_.add(d.getName());
                           peripheralDevices_.add(newDev);
                        }
                     } catch (Exception e) {
                        ReportingUtils.logError(e.getMessage());
                     }
                  }
               }
            }
         }
      }
   }
   private JTable deviceTable_;
   private JScrollPane scrollPane_;
   private static final String HELP_FILE_NAME = "conf_devices_page.html";

   /**
    * Create the panel
    */
   public PeripheralDevicesPage(Preferences prefs) {
      super();

      String hubColumn = Integer.toString(HUBCOLUMN + 1);
      String nameColumn = Integer.toString(NAMECOLUMN + 1);
      title_ = "The following peripherals have been discovered";
      helpText_ = "The list of detected peripheral devices is displayed above. "
              + "You select and de-select the devices in the above list.\n"
              + "Column " + hubColumn + " shows which hub the device was reported on\n"
              + "Column " + nameColumn + " specified the device name, which you can edit\n"
              + "In subsequent steps devices will be referred to by their assigned names.\n\n"
              + "You can edit device names by double-clicking the name. Device name must be unique and should not contain any special characters.";

      setLayout(null);
      prefs_ = prefs;
      setHelpFileName(HELP_FILE_NAME);

      scrollPane_ = new JScrollPane();
      scrollPane_.setBounds(10, 10, 453, 262);
      add(scrollPane_);

      deviceTable_ = new JTable();
      deviceTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      scrollPane_.setViewportView(deviceTable_);
   }

   protected void removeDevice() {
      int sel = deviceTable_.getSelectedRow();
      if (sel < 0) {
         return;
      }
      String devName = (String) deviceTable_.getValueAt(sel, 0);

      if (devName.contentEquals(new StringBuffer().append(MMCoreJ.getG_Keyword_CoreDevice()))) {
         handleError(MMCoreJ.getG_Keyword_CoreDevice() + " device can't be removed!");
         return;
      }

      model_.removeDevice(devName);
      rebuildTable();
   }

   public void rebuildTable() {
      TableModel tm = deviceTable_.getModel();
      DeviceTable_TableModel tmd;
      if (tm instanceof DeviceTable_TableModel) {
         tmd = (DeviceTable_TableModel) deviceTable_.getModel();
         tmd.refresh();
      } else {
         tmd = new DeviceTable_TableModel(model_);
         deviceTable_.setModel(tmd);
      }
      tmd.fireTableStructureChanged();
      tmd.fireTableDataChanged();
   }

   public void refresh() {
      rebuildTable();
   }

   public boolean enterPage(boolean fromNextPage) {
      boolean status = true;
      Container ancestor = getTopLevelAncestor();
      Cursor oldc = null;
      if (null != ancestor) {
         oldc = ancestor.getCursor();
         Cursor waitc = new Cursor(Cursor.WAIT_CURSOR);
         ancestor.setCursor(waitc);
      }

      try {
         rebuildTable();
         if (fromNextPage) {
            model_.loadModel(core_, false);
         }
      } catch (Exception e2) {
         ReportingUtils.showError(e2);
         status = false;
      } finally {
         if (null != ancestor) {
            if (null != oldc) {
               ancestor.setCursor(oldc);
            }
         }
      }
      return status;
   }

   public boolean exitPage(boolean toNextPage) {
      boolean status = true;
      if (toNextPage) {
         try {
            DeviceTable_TableModel tmd = (DeviceTable_TableModel) deviceTable_.getModel();

            Vector<Device> pd = tmd.getPeripheralDevices();
            Vector<String> hubs = tmd.getMasterDevices();
            Vector<Boolean> sel = tmd.getSelected();
            model_.AddSelectedPeripherals(core_, pd, hubs, sel);
            model_.loadDeviceDataFromHardware(core_);
            tmd.rebuild();
         } catch (Exception e) {
            handleException(e);
            status = false;
         } finally {
            try {
               core_.unloadAllDevices();
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }
            Container ancestor = getTopLevelAncestor();
            Cursor oldc = null;
            if (null != ancestor) {
               oldc = ancestor.getCursor();
               Cursor waitc = new Cursor(Cursor.WAIT_CURSOR);
               ancestor.setCursor(waitc);
            }
            model_.loadModel(core_, false);

            if (null != ancestor) {
               if (null != oldc) {
                  ancestor.setCursor(oldc);
               }
            }
         }
      }
      return status;
   }

   public void loadSettings() {
   }

   public void saveSettings() {
   }
}
