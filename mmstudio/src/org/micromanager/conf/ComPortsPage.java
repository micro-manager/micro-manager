///////////////////////////////////////////////////////////////////////////////
//FILE:          ComPortsPage.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, November 12, 2006
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

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import java.util.prefs.Preferences;

import javax.swing.InputMap;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import mmcorej.MMCoreJ;
import mmcorej.StrVector;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.PropertyItem;
import org.micromanager.utils.PropertyNameCellRenderer;
import org.micromanager.utils.PropertyValueCellEditor;
import org.micromanager.utils.PropertyValueCellRenderer;
//import org.apache.commons.collections;

/**
 * Config Wizard COM ports page.
 * Select and configure COM ports.
 */
public class ComPortsPage extends PagePanel {

   private static final long serialVersionUID = 1L;
   private JTable portTable_;
   private JTable serialDeviceTable_;
   private static final String HELP_FILE_NAME = "conf_comport_page.html";
   Vector<String> portsVect_ = new Vector<String>();
	
	// the model uses the 'in-use' property to decide to display the serial device settings
	// so we need to save the original settings, overwrite them before re-rendering the table and then
	// restore them after we finish
	private HashMap<Integer, Boolean> saveSerialPortsInUse_;
	HashMap<String,String> devicesToTheirPort_;


   public class SelectionListener implements ListSelectionListener {
      JTable table;

      // It is necessary to keep the table since it is not possible
      // to determine the table from the event's source
      SelectionListener(JTable table) {
          this.table = table;
      }
      public void valueChanged(ListSelectionEvent e) {
         if (e.getValueIsAdjusting())
            return;

			int rowSel = -1;
         ListSelectionModel lsm = (ListSelectionModel)e.getSource();
         SerialDeviceTableModel ltm = (SerialDeviceTableModel)serialDeviceTable_.getModel();
         if (lsm.isSelectionEmpty()) {
				// nothing selected
         } else {
				rowSel = lsm.getMinSelectionIndex();
            String deviceName = (String)table.getValueAt(rowSel, 0);
				// this will be the name of the device which USES the serial port, so... we need to map it back to a serial port device name...
				String spname = devicesToTheirPort_.get(deviceName);
            ltm.setValue(spname);
         }
         ltm.fireTableStructureChanged();
			//   this does not do quite what you would think....
			//if( -1 < rowSel)
			//	serialDeviceTable_.setRowSelectionInterval(rowSel, rowSel);

			//serialDeviceTable_.getColumnModel().getColumn(0).setWidth(50);
         rebuildTable();

      }
   }


   /**
    * Create the panel
    */
   public ComPortsPage(Preferences prefs) {
      super();
      title_ = "Setup Serial ports";
      prefs_ = prefs;
      setLayout(null);
      setHelpFileName(HELP_FILE_NAME);

      final JLabel usePortsLabel = new JLabel();
      usePortsLabel.setText("Port Setting for Device:");
      usePortsLabel.setBounds(10, 23, 357, 14);
      add(usePortsLabel);

      final JScrollPane serialDeviceScrollPane = new JScrollPane();
      serialDeviceScrollPane.setBounds(10, 44, 157, 194);
      add(serialDeviceScrollPane);
		devicesToTheirPort_ = new HashMap<String,String>();
		saveSerialPortsInUse_ = new HashMap<Integer,Boolean>();
      serialDeviceTable_ = new JTable();
		serialDeviceTable_.setColumnSelectionAllowed(false);
		serialDeviceTable_.setRowSelectionAllowed(true);
		serialDeviceTable_.setModel(new SerialDeviceTableModel());
      serialDeviceTable_.setAutoCreateColumnsFromModel(false);

		serialDeviceTable_.getSelectionModel().addListSelectionListener(new SelectionListener(serialDeviceTable_));
      serialDeviceTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      serialDeviceScrollPane.setViewportView(serialDeviceTable_);



		// this is the table of serial port properties for the user to review and possibly modify
      final JScrollPane scrollPane = new JScrollPane();
      scrollPane.setBounds(173, 23, 397, 215);
      add(scrollPane);

      portTable_ = new JTable();
      portTable_.setAutoCreateColumnsFromModel(false);
      portTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		// was working okay without these attributes
      GUIUtils.setClickCountToStartEditing(portTable_,1);
      GUIUtils.stopEditingOnLosingFocus(portTable_);


      scrollPane.setViewportView(portTable_);

      //
   }

   public boolean enterPage(boolean next) {
		if( next){
         try {
			core_.unloadAllDevices();
			// this mostly duplicates the exitPage of the DevicesPage.....
			// would be nicer to wrap this into a method of some sort but who owns it??
			StrVector ld = core_.getLoadedDevices();

			// first load com ports
			Device ports[] = model_.getAvailableSerialPorts();

		  // allow the user to first associate the COM port with the device,
			// later we will clear the 'use' flag after we determine we don't need the serial port
			for( Device p : ports)
				 model_.useSerialPort(p, true);

			for (int i=0; i<ports.length; i++) {
				if (model_.isPortInUse(ports[i])) {
					 core_.loadDevice(ports[i].getName(), ports[i].getLibrary(), ports[i].getAdapterName());
				}
			}

			// load devices
			Device devs[] = model_.getDevices();
			for (int i=0; i<devs.length; i++) {
				if (!devs[i].isCore()) {
					core_.loadDevice(devs[i].getName(), devs[i].getLibrary(), devs[i].getAdapterName());
				}
			}
         } catch (Exception e) {
            handleException(e);
            return false;
         }
		}
		portsVect_.clear();
      model_.removeDuplicateComPorts();
      Device ports[] = model_.getAvailableSerialPorts();

      for (Device d : ports) {
         try {
            d.loadDataFromHardware(core_);
         } catch (Exception e) {
            handleError(e.getMessage());
         }
      }

      //map the serial ports corresponding to the other devices
      HashMap<String, ArrayList<String>> portUse = new HashMap<String, ArrayList<String>>();

      Device devices[] = model_.getDevices();
      for (int i = 0; i < devices.length; i++) {
         for (int j = 0; j < devices[i].getNumberOfProperties(); j++) {
            PropertyItem p = devices[i].getProperty(j);
            if (p.name.compareTo(MMCoreJ.getG_Keyword_Port()) == 0) {
               // add this device to the devices controlled by this serial port
               ArrayList<String> devicesHere = portUse.get(p.value);
               if (null == devicesHere) {
                  portUse.put(p.value, devicesHere = new ArrayList<String>());
               }
               devicesHere.add(devices[i].getName());
            }
         }
      }

      for (Integer i = 0; i < ports.length; i++) {
			boolean inUse = model_.isPortInUse(ports[i]);
			// save the original 'in-use' state
			saveSerialPortsInUse_.put(i, inUse);
         if (inUse) {
            ArrayList<String> ds = portUse.get(ports[i].getAdapterName());
            String value = ports[i].getAdapterName();
				String thisPort = new String(value);
				String theseDevices = new String(value);
            if (null != ds) {
               // if any devices use this serial port, put that name on the button rather than the serial port name
               value = "";
               for (String s : ds) {
                  if( 0 < value.length() )
                     value += " ";
                  value += s;
               }
					theseDevices = value;
            }
				// stuff for selecting the serial port via the name of the controlled devices
				devicesToTheirPort_.put(theseDevices, thisPort);
            portsVect_.add(value);
         }
      }
		// force the data model to provide display for the first serial port
		boolean selectFirstUsedPort = true;
      for (Integer i = 0; i < ports.length; i++) {
			if( model_.isPortInUse(ports[i]) && selectFirstUsedPort)			{
	         SerialDeviceTableModel ltm = (SerialDeviceTableModel)serialDeviceTable_.getModel();
				ltm.setValue(ports[i].getAdapterName());
				selectFirstUsedPort = false;
			}
		}


      rebuildTable();

      return true;
   }

   public boolean exitPage(boolean toNextPage) {
      try {

       Device ports[] = model_.getAvailableSerialPorts();

			// restore the port in-use flags
         for (int i = 0; i < ports.length; i++) {
				model_.useSerialPort(i,  saveSerialPortsInUse_.get(i));
			}
         if (toNextPage) {

//            reloadDevices();
            // apply the properties
            PropertyTableModel ptm = (PropertyTableModel) portTable_.getModel();
            for (int i = 0; i < ptm.getRowCount(); i++) {
               Setting s = ptm.getSetting(i);
               core_.setProperty(s.deviceName_, s.propertyName_, s.propertyValue_);
               Device dev = model_.findSerialPort(s.deviceName_);
               PropertyItem prop = dev.findSetupProperty(s.propertyName_);
               if (prop == null) {
                  dev.addSetupProperty(new PropertyItem(s.propertyName_, s.propertyValue_, true));
               } else {
                  prop.value = s.propertyValue_;
               }
            }
            for (int i = 0; i < ports.length; i++) {
               if (model_.isPortInUse(ports[i])) {
                  // core_.loadDevice(ports[i].getName(), ports[i].getLibrary(), ports[i].getAdapterName());

                  // serial ports are associated with devices so we can now set the COM port settings
                  Device d = model_.findSerialPort(ports[i].getName());
                  for (int j = 0; j < d.getNumberOfSetupProperties(); j++) {
                     PropertyItem prop = d.getSetupProperty(j);
                     core_.setProperty(d.getName(), prop.name, prop.value);
                  }
               }
            }

            // initialize the entire system
            core_.initializeAllDevices();
            GUIUtils.preventDisplayAdapterChangeExceptions();
            model_.loadDeviceDataFromHardware(core_);

         } else {
            //core_.unloadAllDevices();
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
   }

   public void loadSettings() {
      // TODO Auto-generated method stub
   }

   public void saveSettings() {
      // TODO Auto-generated method stub
   }


   private void rebuildTable() {

//      reloadDevices();

      PropertyTableModel tm = new PropertyTableModel(this, model_, PropertyTableModel.COMPORT);
      portTable_.setModel(tm);
      PropertyValueCellEditor propValueEditor = new PropertyValueCellEditor();
      PropertyValueCellRenderer propValueRenderer = new PropertyValueCellRenderer();
      PropertyNameCellRenderer propNameRenderer = new PropertyNameCellRenderer();


      if (portTable_.getColumnCount() == 0) {
         TableColumn column;
         column = new TableColumn(0, 200, propNameRenderer, null);
         portTable_.addColumn(column);
         column = new TableColumn(1, 200, propNameRenderer, null);
         portTable_.addColumn(column);
         column = new TableColumn(2, 200, propValueRenderer, propValueEditor);
         portTable_.addColumn(column);

      }

      tm.fireTableStructureChanged();
      // tm.fire
      portTable_.repaint();
   }

class SerialDeviceTableModel extends AbstractTableModel {
      private static final long serialVersionUID = 1L;
      public final String[] COLUMN_NAMES = new String[] {
            "Serial devices"
      };
		private String selectedPort_;

		public void setValue(String value){
			selectedPort_ = value;
         Device ports[] = model_.getAvailableSerialPorts();
			for (Integer i = 0; i < ports.length; i++) {
				boolean used = false;
				if( value.equals( ports[i].getAdapterName() ))			{
					used = true;
				}
				model_.useSerialPort(i, used);
			}
		}

      public int getRowCount() {
         return portsVect_.size();
      }
      public int getColumnCount() {
         return COLUMN_NAMES.length;
      }
      public String getColumnName(int columnIndex) {
         return COLUMN_NAMES[columnIndex];
      }
      public Object getValueAt(int rowIndex, int columnIndex) {
         return portsVect_.get(rowIndex);
      }
   }




}
