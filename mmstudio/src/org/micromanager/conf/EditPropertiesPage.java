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
import mmcorej.DeviceDiscoveryStatus;
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
		ArrayList<Device> ports = new ArrayList<Device>();
		model_.removeDuplicateComPorts();
		Device avPorts[] = model_.getAvailableSerialPorts();
		for (int ip = 0; ip < avPorts.length; ++ip) {
			if (model_.isPortInUse(avPorts[ip])) {
				ports.add(avPorts[ip]);
			}
		}
		Device devices[] = model_.getDevices();
		for (int i = 0; i < devices.length; i++) {
			for (int j = 0; j < devices[i].getNumberOfProperties(); j++) {
				PropertyItem p = devices[i].getProperty(j);
				if (p.name.compareTo(MMCoreJ.getG_Keyword_Port()) == 0) {
					if (ports.size() == 0) {
						// no ports available, tell user and return
						JOptionPane.showMessageDialog(null, "No serial communication ports were found in your computer!");
						return false;
					}
					String allowed[] = new String[0];
					ArrayList<String> portsFoundCommunicating = new ArrayList<String>();
					ArrayList<String> portsOtherwiseCorrectlyConfigured = new ArrayList<String>();
					for (int k = 0; k < ports.size(); k++) {
						try {
							// some devices adapters are able to respond before initialization -
							// so, for each physical serial port, preliminarily attempt communication
							// generally only one of the serial ports should succede. (status = CanCommunicate)
							// if more than 0 ports are found to communicate, allowed values will be set to that set
							// else all ports that are not flagged with 'misconfiguered' comm. status will allowed

							//todo - need a way to mark the port as 'allocated' if it already communicates with a device.
							core_.setProperty(devices[i].getName(), p.name, ports.get(k).getName());
							DeviceDiscoveryStatus st = core_.getDeviceDiscoveryStatus(devices[i].getName());

							if (DeviceDiscoveryStatus.CanCommunicate == st) {
								portsFoundCommunicating.add(ports.get(k).getName());
							} else if (DeviceDiscoveryStatus.Misconfigured != st) {
								portsOtherwiseCorrectlyConfigured.add(ports.get(k).getName());
							}
						} catch (Exception e) {
						}
					}
					if (0 < portsFoundCommunicating.size()) {
						allowed = new String[portsFoundCommunicating.size()];
						int aiterator = 0;
						for (String ss : portsFoundCommunicating) {
							allowed[aiterator++] = ss;
						}
					} else if (0 < portsOtherwiseCorrectlyConfigured.size()) {
						allowed = new String[portsOtherwiseCorrectlyConfigured.size()];
						int i2 = 0;
						for (String ss : portsOtherwiseCorrectlyConfigured) {
							allowed[i2++] = ss;
						}
					}
					p.allowed = allowed;
					p.value = "";
					if (0 < allowed.length) {
						p.value = allowed[0];
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
				for (int ip = 0; ip < avPorts.length; ++ip) {
					if (model_.isPortInUse(avPorts[ip])) {
						ports.add(avPorts[ip].getAdapterName());
					}
				}

				// clear all the 'use' flags
				for (Device p : avPorts) {
					model_.useSerialPort(p, false);
				}

				// apply the properties and mark the serial ports that are really in use
				PropertyTableModel ptm = (PropertyTableModel) propTable_.getModel();
				for (int i = 0; i < ptm.getRowCount(); i++) {
					Setting s = ptm.getSetting(i);
					if (s.propertyName_.compareTo(MMCoreJ.getG_Keyword_Port()) == 0) {
						// check that this is a valid port
						if (!ports.contains(s.propertyValue_)) {
							JOptionPane.showMessageDialog(null, "Please select a valid serial port for " + s.deviceName_);
							return false;
						} else {
							for (int j = 0; j < avPorts.length; ++j) {
								if (0 == s.propertyValue_.compareTo(avPorts[j].getAdapterName())) {
									model_.useSerialPort(avPorts[j], true);
								}
							}
						}
					}
					core_.setProperty(s.deviceName_, s.propertyName_, s.propertyValue_);
					Device dev = model_.findDevice(s.deviceName_);
					PropertyItem prop = dev.findSetupProperty(s.propertyName_);

					if (prop == null) {
						model_.addSetupProperty(s.deviceName_, new PropertyItem(s.propertyName_, s.propertyValue_, true));
					}
					model_.setDeviceSetupProperty(s.deviceName_, s.propertyName_, s.propertyValue_);
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
