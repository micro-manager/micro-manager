///////////////////////////////////////////////////////////////////////////////
//FILE:          EditPropertiesPage.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 29, 2006
//               Karl Hoover January 2011
//               (automatic device detection)
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
// CVS:          $Id$
//
package org.micromanager.conf;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableColumn;
import org.micromanager.utils.GUIUtils;
import mmcorej.MMCoreJ;
import mmcorej.DeviceDetectionStatus;
import mmcorej.StrVector;
import org.micromanager.utils.PropertyItem;
import org.micromanager.utils.PropertyNameCellRenderer;
import org.micromanager.utils.PropertyValueCellEditor;
import org.micromanager.utils.PropertyValueCellRenderer;
import org.micromanager.utils.ReportingUtils;
/**
 * Wizard page to set device properties.
 *
 */
public class EditPropertiesPage extends PagePanel {
    class DetectionTask extends Thread {
        DetectionTask(String id) {
            super(id);
        }
        public void run() {
            boolean currentDebugLogSetting = core_.debugLogEnabled();
            try {
                ArrayList<Device> ports = new ArrayList<Device>();
                model_.removeDuplicateComPorts();
                Device availablePorts[] = model_.getAvailableSerialPorts();
                for (Device p : availablePorts) {
                    model_.useSerialPort(p, true);
                }
                String portsInModel = "Serial ports available in configuration: ";
                for (int ip = 0; ip < availablePorts.length; ++ip) {
                    if (model_.isPortInUse(availablePorts[ip])) {
                        ports.add(availablePorts[ip]);
                    }
                }
                for (Device p1 : ports) {
                    if (0 < portsInModel.length()) {
                        portsInModel += " ";
                    }
                    portsInModel += p1.getName();
                }
                class Detector extends Thread {
                    Detector(String deviceName, String portName) {
                        super(deviceName);
                        portName_ = portName;
                        st0_ = DeviceDetectionStatus.Misconfigured;
                    }
                    private DeviceDetectionStatus st0_;
                    private String portName_;
                    public void run() {
                        st0_ = core_.detectDevice(getName());
                    }
                    public DeviceDetectionStatus getStatus() {
                        return st0_;
                    }
                    public String PortName() {
                        return portName_;
                    }
                    public void finish() {
                        try {
                            join();
                        } catch (InterruptedException ex) {
                            //ReportingUtils.showError(ex);
                        }
                    }
                }
                ArrayList<Device> devicesToSearch = new ArrayList<Device>();
                ArrayList<Detector> detectors = new ArrayList<Detector>();
                // if the device does respond on any port, only communicating ports are allowed in the drop down
                Map<String, ArrayList<String>> portsFoundCommunicating = new HashMap<String, ArrayList<String>>();
                // if the device does not respond on any port, let the user pick any port that was setup with a valid serial port name, etc.
                Map<String, ArrayList<String>> portsOtherwiseCorrectlyConfigured = new HashMap<String, ArrayList<String>>();
                Device devices[] = model_.getDevices();
                //  build list of devices to look for on the serial ports
                for (int i = 0; i < devices.length; i++) {
                    for (int j = 0; j < devices[i].getNumberOfProperties(); j++) {
                        PropertyItem p = devices[i].getProperty(j);
                        if (p.name.compareTo(MMCoreJ.getG_Keyword_Port()) == 0) {
                            if (0 == ports.size() ) {
                                // no ports available, tell user and return
                                JOptionPane.showMessageDialog(null, "No serial communication ports were found in your computer!");
                                return;
                            } else {
                                devicesToSearch.add(devices[i]);
                            }
                        }
                    }
                }
                // now simply start a thread for each permutation of port and device, taking care to keep the threads working
                // on unique combinations of device and port
                String looking = "";
                // no devices need to configure serial ports
                if (devicesToSearch.size() < 1) {
                    return;
                }
                // during detection we'll generate lots of spurious error messages.
                core_.enableDebugLog(false);
                if (devicesToSearch.size() <= ports.size()) {
                    // for case where there are more serial ports than devices
                    for (int iteration = 0; iteration < ports.size(); ++iteration) {
                        detectors.clear();
                        looking = "";
                        for (int diterator = 0; diterator < devicesToSearch.size(); ++diterator) {
                            int portOffset = (diterator + iteration) % ports.size();
                            try {
                                core_.setProperty(devicesToSearch.get(diterator).getName(), MMCoreJ.getG_Keyword_Port(), ports.get(portOffset).getName());
                                detectors.add(new Detector(devicesToSearch.get(diterator).getName(), ports.get(portOffset).getName()));
                                if (0 < looking.length()) {
                                    looking += "\n";
                                }
                                looking += devicesToSearch.get(diterator).getName() + " on " + ports.get(portOffset).getName();
                            } catch (Exception e) {
                                // USB devices will try to open the interface and return an error on failure
                                // so do not show, but only log the error
                                ReportingUtils.logError(e);
                            }
                        }
                        progressDialog_.ProgressText("Looking for:\n" + looking);
                        for (Detector d : detectors) {
                            d.start();
                        }
                        for (Detector d : detectors) {
                            d.finish();
                            if (progressDialog_.CancelRequest()| requestCancel_) {
                                System.out.print("cancel request");
                                return; //
                            }
                        }
                        // now the detection at this iteration is complete
                        for (Detector d : detectors) {
                            DeviceDetectionStatus st = d.getStatus();
                            if (DeviceDetectionStatus.CanCommunicate == st) {
                                ArrayList<String> llist = portsFoundCommunicating.get(d.getName());
                                if (null == llist) {
                                    portsFoundCommunicating.put(d.getName(), llist = new ArrayList<String>());
                                }
                                llist.add(d.PortName());
                            } else {
                                ArrayList<String> llist = portsOtherwiseCorrectlyConfigured.get(d.getName());
                                if (null == llist) {
                                    portsOtherwiseCorrectlyConfigured.put(d.getName(), llist = new ArrayList<String>());
                                }
                                llist.add(d.PortName());
                            }
                        }
                    }
                    //// ****** complete this detection iteration
                } else { // there are more devices than serial ports...
                    for (int iteration = 0; iteration < devicesToSearch.size(); ++iteration) {
                        detectors.clear();
                        looking = "";
                        for (int piterator = 0; piterator < ports.size(); ++piterator) {
                            int dOffset = (piterator + iteration) % devicesToSearch.size();
                            try {
                                core_.setProperty(devicesToSearch.get(dOffset).getName(), MMCoreJ.getG_Keyword_Port(), ports.get(piterator).getName());
                                detectors.add(new Detector(devicesToSearch.get(dOffset).getName(), ports.get(piterator).getName()));
                                if (0 < looking.length()) {
                                    looking += "\n";
                                }
                                looking += devicesToSearch.get(dOffset).getName() + " on " + ports.get(piterator).getName();
                            } catch (Exception e) {
                                ReportingUtils.showError(e);
                            }
                        }
                        progressDialog_.ProgressText("Looking for:\n" + looking);
                        for (Detector d : detectors) {
                            d.start();
                        }
                        for (Detector d : detectors) {
                            d.finish();
                            if (progressDialog_.CancelRequest() || requestCancel_) {
                                System.out.print("cancel request");
                                return; //
                            }                        }
                        // the detection at this iteration is complete
                        for (Detector d : detectors) {
                            DeviceDetectionStatus st = d.getStatus();
                            if (DeviceDetectionStatus.CanCommunicate == st) {
                                ArrayList<String> llist = portsFoundCommunicating.get(d.getName());
                                if (null == llist) {
                                    portsFoundCommunicating.put(d.getName(), llist = new ArrayList<String>());
                                }
                                llist.add(d.PortName());
                            } else {
                                ArrayList<String> llist = portsOtherwiseCorrectlyConfigured.get(d.getName());
                                if (null == llist) {
                                    portsOtherwiseCorrectlyConfigured.put(d.getName(), llist = new ArrayList<String>());
                                }
                                llist.add(d.PortName());
                            }
                        }
                    }
                }
                String foundem = "";
                // show the user the result and populate the drop down data
                for (Device dd : devicesToSearch) {
                    ArrayList<String> communicating = portsFoundCommunicating.get(dd.getName());
                    ArrayList<String> onlyConfigured = portsOtherwiseCorrectlyConfigured.get(dd.getName());
                    String allowed[] = new String[0];
                    boolean any = false;
                    if (null != communicating) {
                        if (0 < communicating.size()) {
                            any = true;
                            allowed = new String[communicating.size()];
                            int aiterator = 0;
                            foundem += dd.getName() + " on ";
                            for (String ss : communicating) {
                                foundem += (ss + "\n");
                                allowed[aiterator++] = ss;
                            }
                        }
                    }
                    // all this ugliness  because no multimap in Java...
                    if (!any) {
                        if (null != onlyConfigured) {
                            if (0 < onlyConfigured.size()) {
                                Collections.sort(onlyConfigured);
                                allowed = new String[onlyConfigured.size()];
                                int i2 = 0;
                                for (String ss : onlyConfigured) {
                                    allowed[i2++] = ss;
                                }
                            }
                        }
                    }
                    PropertyItem p = dd.findProperty(MMCoreJ.getG_Keyword_Port());
                    p.allowed = allowed;
                    p.value = "";
                    if (0 < allowed.length) {
                        p.value = allowed[0];
                    }
                }
                progressDialog_.ProgressText("Found:\n " + foundem);
                try {
                    Thread.sleep(900);
                } catch (InterruptedException ex) {
                }
            } finally { // nmatches try at entry
                progressDialog_.setVisible(false);
                core_.enableDebugLog(currentDebugLogSetting);
                rebuildTable();
                // restore normal operation of the Detect button
                detectButton_.setText(detectPorts_);
            }
        }
        public void finish() {
            try {
                join();
            } catch (InterruptedException ex) {
            }
        }
    }
    private final String detectPorts_ = "Scan Ports";
    private static final long serialVersionUID = 1L;
    private JTable propTable_;
    private JScrollPane scrollPane_;
    private static final String HELP_FILE_NAME = "conf_preinit_page.html";
    private boolean requestCancel_;
    private DetectorJDialog progressDialog_;
    private JButton detectButton_;
    private DetectionTask dt_;
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
        scrollPane_.setBounds(10, 9, 421, 262);
        add(scrollPane_);
        propTable_ = new JTable();
        propTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        propTable_.setAutoCreateColumnsFromModel(false);
        scrollPane_.setViewportView(propTable_);
        progressDialog_ = null;
        final EditPropertiesPage thisPage = this;
        detectButton_ = new JButton();
        detectButton_.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                if (detectButton_.getText().equalsIgnoreCase(detectPorts_)) {
                    requestCancel_ = false;
                    progressDialog_ = new DetectorJDialog(parent_, false);
                    progressDialog_.setTitle("\u00B5" + "Manager device detection");
                    progressDialog_.setLocationRelativeTo(thisPage);
                    progressDialog_.setSize(483, 288);
                    progressDialog_.setVisible(true);
                    dt_ = new DetectionTask("serial_detect");
                    dt_.start();
                    detectButton_.setText("Cancel");
                } else {
                    requestCancel_ = true;
                    dt_.finish();
                    detectButton_.setText(detectPorts_);
                }
            }
        });
        detectButton_.setText(detectPorts_);
        detectButton_.setBounds(454, 10, 108, 23);
        add(detectButton_);
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
        Device devices[] = model_.getDevices();
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
        detectButton_.setEnabled(any);
        propTable_.repaint();
    }
    public boolean enterPage(boolean fromNextPage) {
        if (fromNextPage) {
            return true;
        }
        requestCancel_ = false;
        rebuildTable();


      // initially, user can select any serial port he wishes
      // later, the Scan Ports button can be pressed to identify which device is on which port
      ArrayList<Device> ports = new ArrayList<Device>();
      Device avPorts[] = model_.getAvailableSerialPorts();
      for(int i=0; i<avPorts.length; i++)
         if (model_.isPortInUse(avPorts[i]))
            ports.add(avPorts[i]);

      // identify "port" properties and assign available com ports declared for use
      Device devices[] = model_.getDevices();
      for (int i=0; i<devices.length; i++) {
         for (int j=0; j<devices[i].getNumberOfProperties(); j++) {
            PropertyItem p = devices[i].getProperty(j);
            if (p.name.compareTo(MMCoreJ.getG_Keyword_Port()) == 0) {
            	if (ports.size() == 0) {
            		// no ports available, tell user and return
            		JOptionPane.showMessageDialog(null, "No Serial Ports are found in your computer!");
            		return true;
            	}
               String allowed[] = new String[ports.size()];
               for (int k=0; k<ports.size(); k++)
                  allowed[k] = ports.get(k).getName();
               p.allowed = allowed;
            }
         }
      }




        return true;
    }
    public boolean exitPage(boolean toNextPage) {
        try {
            if(null!=dt_)
                if(dt_.isAlive())
                    dt_.finish();
            if (null != progressDialog_) {
                progressDialog_.setVisible(false);
            }
            if (toNextPage) {
                // create an array of allowed port names
                ArrayList<String> ports = new ArrayList<String>();
                Device avPorts[] = model_.getAvailableSerialPorts();
                for (int ip = 0; ip
                        < avPorts.length;
                        ++ip) {
                    if (model_.isPortInUse(avPorts[ip])) {
                        ports.add(avPorts[ip].getAdapterName());
                    }
                }
                // clear all the 'use' flags
                for (Device p : avPorts) {
                    model_.useSerialPort(p, false);
                } // apply the properties and mark the serial ports that are really in use
                PropertyTableModel ptm = (PropertyTableModel) propTable_.getModel();
                for (int i = 0; i< ptm.getRowCount(); i++) {
                    Setting s = ptm.getSetting(i);
                    if (s.propertyName_.compareTo(MMCoreJ.getG_Keyword_Port()) == 0) {
                        // check that this is a valid port
                        if (!ports.contains(s.propertyValue_)) {
                            JOptionPane.showMessageDialog(null, "Please select a valid serial port for " + s.deviceName_);
                            return false;
                        } else {
                            for (int j = 0; j< avPorts.length; ++j) {
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
