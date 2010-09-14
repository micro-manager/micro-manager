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

import javax.swing.JOptionPane;
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
      propTable_.repaint();
   }
   
   public boolean enterPage(boolean fromNextPage) {
      rebuildTable();
      
      // create an array of allowed ports
      ArrayList<Device> ports = new ArrayList<Device>();
      model_.removeDuplicateComPorts();
      Device avPorts[] = model_.getAvailableSerialPorts();
      for(int ip=0; ip<avPorts.length; ++ip)
         if (model_.isPortInUse(avPorts[ip]))
            ports.add(avPorts[ip]);
      
      // identify "port" properties and assign available com ports declared for use
      Device devices[] = model_.getDevices();
      for (int i=0; i<devices.length; i++) {
         for (int j=0; j<devices[i].getNumberOfProperties(); j++) {
            PropertyItem p = devices[i].getProperty(j);
            if (p.name.compareTo(MMCoreJ.getG_Keyword_Port()) == 0) {
            	if (ports.size() == 0) {
            		// no ports available, tell user and return
            		JOptionPane.showMessageDialog(null, "No serial communication ports were found in your computer!");
            		return false;
            	}
               String allowed[] = new String[ports.size()];
               int nPortsFoundCommunicating = 0;
               String portsFoundCommunicating[] = new String[ports.size()];
               boolean anyPortsWereInvalidated = false;
               for (int k=0; k<ports.size(); k++){
                  // for each physical serial port, preliminarily attempt communication
                  // some devices adapters are able to respond before initialization
                  // generally only one of the serial ports should succede.
                  // currently this uses the OnPort property interface,therefor
                  // we only know that the device communicating if it invalidated some other port selection value
                  try{
                     core_.setProperty(devices[i].getName(), p.name, ports.get(k).getName());
                     String newValue = core_.getProperty(devices[i].getName(), p.name);
                     if( 0 == newValue.compareTo(ports.get(k).getName())){
                        portsFoundCommunicating[nPortsFoundCommunicating++] = ports.get(k).getName();
                     }
                     else{
                        anyPortsWereInvalidated = true;
                        // the device adapter attempted communication with the equipment and failed
                        // and has there set the property to Undefined
                     }
                  }catch(Exception e){
                  }
                  allowed[k] = ports.get(k).getName();
               }
               if(anyPortsWereInvalidated &&  0 < nPortsFoundCommunicating ){
                  // at least one port has this type of device connected, so only show these ports in the selection list
                  String newAllowed[] = new String[nPortsFoundCommunicating];
                  for(int ia = 0; ia < nPortsFoundCommunicating; ++ia){
                     newAllowed[ia] = portsFoundCommunicating[ia];
                  }
                  p.allowed = newAllowed;
                  p.value = newAllowed[0];
               }else{
                  p.allowed = allowed;
               }
            }
         }
      }
      return true;
   }

   public boolean exitPage(boolean toNextPage) {
      try {
         if (toNextPage) {
         // create an array of allowed port names
            ArrayList<String> ports = new ArrayList<String>();
            Device avPorts[] = model_.getAvailableSerialPorts();
            for(int ip=0; ip<avPorts.length; ++ip)
                if (model_.isPortInUse(avPorts[ip]))
                   ports.add(avPorts[ip].getAdapterName());

             // clear all the 'use' flags
            for( Device p : avPorts)
                model_.useSerialPort(p, false);
             
            // apply the properties and mark the serial ports that are really in use
            PropertyTableModel ptm = (PropertyTableModel)propTable_.getModel();
            for (int i=0; i<ptm.getRowCount(); i++) {
               Setting s = ptm.getSetting(i);
               if (s.propertyName_.compareTo(MMCoreJ.getG_Keyword_Port()) == 0) {
            	   // check that this is a valid port
            	   if (!ports.contains(s.propertyValue_)) {
            		  JOptionPane.showMessageDialog(null, "Please select a valid serial port for " + s.deviceName_);
            		  return false;
            	   }else{
                     for(int j=0; j<avPorts.length; ++j){
                        if( 0==s.propertyValue_.compareTo(avPorts[j].getAdapterName()))
                           model_.useSerialPort(avPorts[j], true);
                     }
                  }
               }
               core_.setProperty(s.deviceName_, s.propertyName_, s.propertyValue_);
               Device dev = model_.findDevice(s.deviceName_);
               PropertyItem prop = dev.findSetupProperty(s.propertyName_);
            	   
               if (prop == null)
                  model_.addSetupProperty(s.deviceName_, new PropertyItem(s.propertyName_, s.propertyValue_, true));
               model_.setDeviceSetupProperty(s.deviceName_, s.propertyName_, s.propertyValue_);
            }
            

         } else {
            core_.unloadAllDevices();
            GUIUtils.preventDisplayAdapterChangeExceptions();
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

   public JTable GetPropertyTable()   {
      return propTable_;
   }

}
