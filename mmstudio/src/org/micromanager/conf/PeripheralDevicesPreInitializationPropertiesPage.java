///////////////////////////////////////////////////////////////////////////////
//FILE:          PeripheralDevicesPreInitializationPropertiesPage.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Karl Hoover January 2011
//
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
// CVS:          $Id: PeripheralDevicesPreInitializationPropertiesPage.java 6968 2011-04-13 19:30:09Z karlh $
//
package org.micromanager.conf;

import java.util.prefs.Preferences;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableColumn;
import org.micromanager.utils.GUIUtils;
import mmcorej.MMCoreJ;
import org.micromanager.utils.PropertyItem;
import org.micromanager.utils.PropertyNameCellRenderer;
import org.micromanager.utils.PropertyValueCellEditor;
import org.micromanager.utils.PropertyValueCellRenderer;
import org.micromanager.utils.ReportingUtils;

/**
 * Wizard page to set device properties.
 *
 */
public class PeripheralDevicesPreInitializationPropertiesPage extends PagePanel {

   private static final long serialVersionUID = 1L;
   private JTable propTable_;
   private JScrollPane scrollPane_;
   private static final String HELP_FILE_NAME = "conf_preinit_page.html";

   /**
    * Create the panel
    */
   public PeripheralDevicesPreInitializationPropertiesPage(Preferences prefs) {
      super();
      title_ = "Edit peripheral device pre-initialization settings";
      helpText_ = "The list of device properties which must be defined prior to initialization is shown above. ";
      setLayout(null);
      prefs_ = prefs;
      setHelpFileName(HELP_FILE_NAME);
      scrollPane_ = new JScrollPane();
      scrollPane_.setBounds(10, 9, 421, 262);
      add(scrollPane_);
      propTable_ = new JTable();
      propTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      propTable_.setAutoCreateColumnsFromModel(false);
      scrollPane_.setViewportView(propTable_);
      final PeripheralDevicesPreInitializationPropertiesPage thisPage = this;
   }

   private void rebuildTable() {
      PropertyTableModel tm = new PropertyTableModel(this, model_, PropertyTableModel.PREINIT);
      propTable_.setModel(tm);
      PropertyValueCellEditor propValueEditor = new PropertyValueCellEditor();
      PropertyValueCellRenderer propValueRenderer = new PropertyValueCellRenderer();
      PropertyNameCellRenderer propNameRenderer = new PropertyNameCellRenderer();
      if (propTable_.getColumnCount() == 0) {
         TableColumn column;
         column = new TableColumn(0, 200, propNameRenderer, null);
         propTable_.addColumn(column);
         column = new TableColumn(1, 200, propNameRenderer, null);
         propTable_.addColumn(column);
         column = new TableColumn(2, 200, propValueRenderer, propValueEditor);
         propTable_.addColumn(column);
      }
      tm.fireTableStructureChanged();
      tm.fireTableDataChanged();
      boolean any = false;
      Device devices[] = model_.getPeripheralDevices();

      //  build list of devices to look for on the serial ports
      for (int i = 0; i < devices.length; i++) {
         for (int j = 0; j < devices[i].getNumberOfProperties(); j++) {
            PropertyItem p = devices[i].getProperty(j);
            if (p.name.compareTo(MMCoreJ.getG_Keyword_Port()) == 0) {
               any = true;
               break;
            }
         }
         if (any) {
            break;
         }
      }
      propTable_.repaint();
   }

   public boolean enterPage(boolean fromNextPage) {
      if (fromNextPage) {
         return true;
      }
      rebuildTable();
      return true;
   }

   public boolean exitPage(boolean toNextPage) {
      try {
         if (toNextPage) {

            // set all pre-initialization properties

            PropertyTableModel ptm = (PropertyTableModel) propTable_.getModel();
            for (int i = 0; i < ptm.getRowCount(); i++) {
               Setting s = ptm.getSetting(i);
               core_.setProperty(s.deviceName_, s.propertyName_, s.propertyValue_);
               Device dev = model_.findDevice(s.deviceName_);
               PropertyItem prop = dev.findSetupProperty(s.propertyName_);
               if (prop == null) {
                  model_.addSetupProperty(s.deviceName_, new PropertyItem(s.propertyName_, s.propertyValue_, true));
               }
               model_.setDeviceSetupProperty(s.deviceName_, s.propertyName_, s.propertyValue_);
            }

            // Reset the serial port settings
            Device ports[] = model_.getAvailableSerialPorts();
            for (int i = 0; i < ports.length; i++) {
               if (model_.isPortInUse(ports[i])) {
                  // core_.loadDevice(ports[i].getName(), ports[i].getLibrary(), ports[i].getAdapterName());
                  Device d = model_.findSerialPort(ports[i].getName());
                  for (int j = 0; j < d.getNumberOfSetupProperties(); j++) {
                     PropertyItem prop = d.getSetupProperty(j);
                     core_.setProperty(d.getName(), prop.name, prop.value);
                  }
               }
            }

            try {
               core_.initializeAllDevices();
               // create the post-initialization properties
               model_.loadDeviceDataFromHardware(core_);
               model_.loadStateLabelsFromHardware(core_);
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
            }

         } else {
            GUIUtils.preventDisplayAdapterChangeExceptions();
         }
      } catch (Exception e) {
         handleException(e);
         if (toNextPage) {
            return false;
         }
      }
      return true;
   }

   public void refresh() {
      rebuildTable();
   }

   public void loadSettings() {
   }

   public void saveSettings() {
   }

   public JTable GetPropertyTable() {
      return propTable_;
   }
}
