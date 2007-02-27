///////////////////////////////////////////////////////////////////////////////
//FILE:          EditPropertiesPage.java
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
// CVS:          $Id$
//

package org.micromanager.conf;

import java.util.ArrayList;
import java.util.prefs.Preferences;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableColumn;

import mmcorej.MMCoreJ;

/**
 * Wizard page to set device properties.
 *
 */
public class EditPropertiesPage extends PagePanel {
   private static final long serialVersionUID = 1L;

   private JTable propTable_;
   private JScrollPane scrollPane_;
   private static final String HELP_FILE_NAME = "conf_preinit_page.html";
   /**
    * Create the panel
    */
   public EditPropertiesPage(Preferences prefs) {
      super();
      title_ = "Edit pre-initialization settings";
      helpText_ = "The list of device properties which must be defined prior to initialization is shown above. ";
      setLayout(null);
      prefs_ = prefs;
      setHelpFileName(HELP_FILE_NAME);

      scrollPane_ = new JScrollPane();
      scrollPane_.setBounds(10, 9, 381, 262);
      add(scrollPane_);

      propTable_ = new JTable();
      propTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      propTable_.setAutoCreateColumnsFromModel(false);
      scrollPane_.setViewportView(propTable_);
      //
   }
   
   
   private void rebuildTable() {
      PropertyTableModel tm = new PropertyTableModel(this, model_, PropertyTableModel.PREINIT);
      propTable_.setModel(tm);
      PropertyCellEditor cellEditor = new PropertyCellEditor();
      PropertyCellRenderer renderer = new PropertyCellRenderer();
     
      if (propTable_.getColumnCount() == 0) {
         for (int k=0; k < tm.getColumnCount(); k++) {
            TableColumn column = new TableColumn(k, 200, renderer, cellEditor);
            propTable_.addColumn(column);
         }
      }

      tm.fireTableStructureChanged();
      propTable_.repaint();
   }
   
   public boolean enterPage(boolean fromNextPage) {
      rebuildTable();
      
      // create an array of allowed ports
      ArrayList ports = new ArrayList();
      Device avPorts[] = model_.getAvailableSerialPorts();
      for(int i=0; i<avPorts.length; i++)
         if (model_.isPortInUse(avPorts[i]))
            ports.add(avPorts[i]);
      
      // identify "port" properties and assign available com ports declared for use
      Device devices[] = model_.getDevices();
      for (int i=0; i<devices.length; i++) {
         for (int j=0; j<devices[i].getNumberOfProperties(); j++) {
            Property p = devices[i].getProperty(j);
            if (p.name_.compareTo(MMCoreJ.getG_Keyword_Port()) == 0) {
               String allowed[] = new String[ports.size()];
               for (int k=0; k<ports.size(); k++)
                  allowed[k] = ((Device)ports.get(k)).getName();
               p.allowedValues_ = allowed;
            }
         }
      }
      return true;
   }

   public boolean exitPage(boolean toNextPage) {
      try {
         if (toNextPage) {
            // apply the properties
            PropertyTableModel ptm = (PropertyTableModel)propTable_.getModel();
            for (int i=0; i<ptm.getRowCount(); i++) {
               Setting s = ptm.getSetting(i);
               core_.setProperty(s.deviceName_, s.propertyName_, s.propertyValue_);
               Device dev = model_.findDevice(s.deviceName_);
               Property prop = dev.findSetupProperty(s.propertyName_);
               if (prop == null)
                  model_.addSetupProperty(s.deviceName_, new Property(s.propertyName_, s.propertyValue_, true));
               model_.setDeviceSetupProperty(s.deviceName_, s.propertyName_, s.propertyValue_);
            }
            
            // initialize the entire system
            core_.initializeAllDevices();
            model_.loadDeviceDataFromHardware(core_);
         } else {
            core_.unloadAllDevices();
         }
      } catch (Exception e) {
         handleException(e);
         if (toNextPage)
            return false;
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
}
