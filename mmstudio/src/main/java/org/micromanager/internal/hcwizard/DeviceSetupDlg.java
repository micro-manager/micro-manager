package org.micromanager.internal.hcwizard;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.swing.*;
import javax.swing.table.TableColumn;
import mmcorej.CMMCore;
import mmcorej.DeviceDetectionStatus;
import mmcorej.DeviceType;
import mmcorej.MMCoreJ;
import mmcorej.StrVector;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.internal.utils.*;

public final class DeviceSetupDlg extends JDialog {
   private static final long serialVersionUID = 1L;
   private static final String SCAN_PORTS = "Scan Ports";

   private final JPanel contents_;
   private final CMMCore core_;
   private final Studio studio_;
   private Device portDev_;
   private final MicroscopeModel model_;
   private Device device_;
   private final JTable propTable_;
   private final JButton detectButton_;
   private DetectorJDialog progressDialog_;
   private DetectionTask detectTask_;
   private final JTable comTable_;
   private final JTextField devName_;
   private final JPanel portSettingsPanel_;
   private final JLabel scanStatus_;

   /**
    * Create the dialog.
    */
   public DeviceSetupDlg(MicroscopeModel mod, Studio studio, Device d) {
      super();
      setModal(true);
      super.setLocation(100, 100);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);
      model_ = mod;
      core_ = studio.core();
      studio_ = studio;
      portDev_ = null;
      device_ = d;

      setTitle("Device: " + device_.getAdapterName() + "; Library: " +
            device_.getLibrary());

      contents_ = new JPanel(new MigLayout("fill, insets 5"));
      setContentPane(contents_);
      contents_.add(new JLabel("Device name: "), "split");

      String parent = device_.getParentHub();

      devName_ = new JTextField(device_.getName());
      contents_.add(devName_, "width 165" +
            ((parent.length() == 0) ? ", wrap" : ""));
      if (parent.length() != 0) {
         contents_.add(new JLabel("Parent: " + parent), "wrap");
      }

      contents_.add(new JLabel("Initialization Properties"), "wrap");
      JScrollPane propertiesScroller = new JScrollPane();
      contents_.add(propertiesScroller, "spanx, growx, height 165!, wrap");

      propTable_ = new DaytimeNighttime.Table();
      propTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      propTable_.setAutoCreateColumnsFromModel(false);
      propertiesScroller.setViewportView(propTable_);

      portSettingsPanel_ = new JPanel(new MigLayout("fill, insets 0"));
      contents_.add(portSettingsPanel_, "hidemode 2, spanx, growx, wrap");

      portSettingsPanel_.add(new JLabel("Port Properties (RS232 Settings)"));

      detectButton_ = new JButton(SCAN_PORTS);
      detectButton_.setEnabled(false);
      detectButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            scanPorts();
         }
      });

      detectButton_.setToolTipText("Scan COM ports to detect this device");
      portSettingsPanel_.add(detectButton_, "gapleft push, wrap");
      scanStatus_ = new JLabel();
      scanStatus_.setVisible(false);
      portSettingsPanel_.add(scanStatus_,
            "hidemode 2, span, gapleft push, wrap");

      JScrollPane portSettingsScroller = new JScrollPane();
      portSettingsPanel_.add(portSettingsScroller,
            "spanx, growx, height 165!, wrap");

      comTable_ = new DaytimeNighttime.Table();
      portSettingsScroller.setViewportView(comTable_);
      comTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      comTable_.setAutoCreateColumnsFromModel(false);

      JPanel buttonPane = new JPanel(
            new MigLayout("fillx, insets 0", "0[grow]0[]0[]0"));

      JButton helpButton = new JButton("Help");
      helpButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               ij.plugin.BrowserLauncher.openURL(
                  DevicesPage.WEBSITE_ROOT + device_.getLibrary());
            } catch (IOException e1) {
               ReportingUtils.showError(e1);
            }
         }
      });
      buttonPane.add(helpButton, "alignx left");

      JButton okButton = new JButton("OK");
      okButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            onOK();
         }
      });
      okButton.setActionCommand("OK");
      buttonPane.add(okButton, "gapleft push");
      getRootPane().setDefaultButton(okButton);

      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            onCancel();
         }
      });
      cancelButton.setActionCommand("Cancel");
      buttonPane.add(cancelButton, "wrap");
      contents_.add(buttonPane, "spanx, growx, wrap");

      pack();
      loadSettings();
      setVisible(true);
   }

   protected void onCancel() {
      dispose();
   }

   protected void onOK() {
      propTable_.editingStopped(null);
      String oldName = device_.getName();
      String newName = devName_.getText();

      if (device_.getName().compareTo(devName_.getText()) != 0) {
         if (model_.findDevice(devName_.getText()) != null) {
            showMessage("Device name " + devName_.getText() + " is already in use.\nPress Cancel and try again.");
            return;
         }

         try {
            core_.unloadDevice(device_.getName());
            device_.setInitialized(false);
            core_.loadDevice(devName_.getText(), device_.getLibrary(), device_.getAdapterName());
            core_.setParentLabel(devName_.getText(), device_.getParentHub());
         } catch (Exception e) {
            showMessage("Device failed to re-load with changed name.");
            return;
         }

         device_.setName(devName_.getText());
      }

      Device d = model_.findDevice(devName_.getText());
      if (d==null) {
         showMessage("Device " + devName_.getText() + " is not loaded properly.\nPress Cancel and try again.");
         return;
      }

      if (d.isInitialized()) {
         try {
            core_.unloadDevice(d.getName());
            core_.loadDevice(d.getName(), d.getLibrary(), d.getAdapterName());
         } catch (Exception e) {
            ReportingUtils.logError(e);
         }
      }

      if (initializeDevice()) {
         dispose();
         if (portDev_ != null)
            model_.useSerialPort(portDev_, true);
         model_.setModified(true);
      } else {
         // initialization failed
         device_.setInitialized(false);
         return;
      }

      // make sure parent refs are updated
      if (!oldName.contentEquals(newName)) {
         Device devs[] = model_.getDevices();
         for (int i=0; i<devs.length; i++) {
            if (devs[i].getParentHub().contentEquals(oldName)) {
               devs[i].setParentHub(newName);
            }
         }
      }
   }

   private void scanPorts() {
      if (detectTask_ != null && detectTask_.isAlive())
         return;
      int response = JOptionPane.showConfirmDialog(DeviceSetupDlg.this,
         "\u00b5Manager will attempt to automatically detect the " +
         "serial port and port settings\nrequired to communicate with " +
         "this device.\n\nWARNING: this will send messages through all " +
         "connected serial ports, potentially\ninterfering with other " +
         "serial devices connected to this computer. We strongly\n" +
         "recommend turning off all other serial devices prior to " +
         "starting this scan.",
         "Automatically Scan Serial Ports",
         JOptionPane.OK_CANCEL_OPTION);
      if (response != JOptionPane.OK_OPTION) {
         // User cancelled.
         return;
      }
      scanStatus_.setVisible(true);
      scanStatus_.setText("Scanning ports...");
      pack();
      progressDialog_ = new DetectorJDialog(DeviceSetupDlg.this, false);
      progressDialog_.setTitle("\u00B5" + "Manager device detection");
      progressDialog_.setLocationRelativeTo(DeviceSetupDlg.this);
      progressDialog_.setSize(483, 288);
      progressDialog_.setVisible(true);
      detectTask_ = new DetectionTask("serial_detect");
      detectTask_.start();
   }

   private void loadSettings() {
      rebuildPropTable();

      // setup com ports
      ArrayList<Device> ports = new ArrayList<Device>();
      Device avPorts[] = model_.getAvailableSerialPorts();
      for(int i=0; i<avPorts.length; i++) {
//         if (!model_.isPortInUse(avPorts[i]))
//            ports.add(avPorts[i]);
//         else if (device_.getPort().compareTo(avPorts[i].getName()) == 0)
//            ports.add(avPorts[i]);
         // NOTE: commented out code was intended to exclude ports
         // that were already used by other devices.
         // But, at this point we have to list all ports (used or not)
         // to provide compatibility with older device adapters that share the same port
         ports.add(avPorts[i]);
      }

      // identify "port" properties and assign available com ports declared for use
      boolean anyPorts = false;
      boolean anyProps = false;
      for (int i=0; i<device_.getNumberOfProperties(); i++) {
         PropertyItem p = device_.getProperty(i);
         if (p.preInit)
            anyProps = true;

         if (p.name.compareTo(MMCoreJ.getG_Keyword_Port()) == 0) {
            anyPorts = true;
            if (ports.size() == 0) {
               // no ports available, tell user and return
               JOptionPane.showMessageDialog(null, "There are no unused ports available!");
               return;
            }
            String allowed[] = new String[ports.size()];
            for (int k=0; k<ports.size(); k++)
               allowed[k] = ports.get(k).getName();
            p.allowed = allowed;

            rebuildComTable(p.value);
         }
      }

      portSettingsPanel_.setVisible(anyPorts);
      pack();
   }

   private void rebuildPropTable() {

      PropertyTableModel tm = new PropertyTableModel(model_, device_, this);
      propTable_.setModel(tm);
      PropertyValueCellEditor propValueEditor = new PropertyValueCellEditor();
      PropertyValueCellRenderer propValueRenderer = new PropertyValueCellRenderer(studio_);
      PropertyNameCellRenderer propNameRenderer = new PropertyNameCellRenderer(studio_);
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
              if (p.name.compareTo(MMCoreJ.getG_Keyword_Port()) == 0 &&
                    core_.supportsDeviceDetection(devices[i].getName())) {
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

   public void rebuildComTable(String portName) {
      if (portName == null)
         return;

      portDev_ = model_.findSerialPort(portName);
      if (portDev_ == null)
         return;

      // load port if necessary
      StrVector loadedPorts = core_.getLoadedDevicesOfType(DeviceType.SerialDevice);
      Iterator<String> lp = loadedPorts.iterator();
      boolean loaded = false;
      while (lp.hasNext()) {
         lp.next().compareTo(portName);
         loaded = true;
      }
      if (!loaded) {
         try {
            core_.loadDevice(portDev_.getName(), portDev_.getLibrary(), portDev_.getAdapterName());
            portDev_.loadDataFromHardware(core_);
         } catch (Exception e) {
            ReportingUtils.logError(e);
         }
      }

      try {
         System.out.println("rebuild " + portDev_.getPropertyValue("BaudRate"));
      } catch (MMConfigFileException e1) {
         ReportingUtils.logMessage("Property BaudRate is not defined");
      }

      ComPropTableModel tm = new ComPropTableModel(model_, portDev_);
      comTable_.setModel(tm);
      PropertyValueCellEditor propValueEditor = new PropertyValueCellEditor();
      PropertyValueCellRenderer propValueRenderer = new PropertyValueCellRenderer(studio_);
      PropertyNameCellRenderer propNameRenderer = new PropertyNameCellRenderer(studio_);
      if (comTable_.getColumnCount() == 0) {
          TableColumn column;
          column = new TableColumn(0, 200, propNameRenderer, null);
          comTable_.addColumn(column);
          column = new TableColumn(1, 200, propNameRenderer, null);
          comTable_.addColumn(column);
          column = new TableColumn(2, 200, propValueRenderer, propValueEditor);
          comTable_.addColumn(column);
      }
      tm.fireTableStructureChanged();
      tm.fireTableDataChanged();
      comTable_.repaint();
   }

   private boolean initializeDevice() {
      try {
         if (device_.isInitialized()) {
            // device was initialized before so now we have to re-set it
            core_.unloadDevice(device_.getName());
            core_.loadDevice(device_.getName(), device_.getLibrary(), device_.getAdapterName());
         }

         // transfer properties to device
         PropertyTableModel ptm = (PropertyTableModel)propTable_.getModel();
         for (int i=0; i<ptm.getRowCount(); i++) {
            Setting s = ptm.getSetting(i);
            core_.setProperty(device_.getName(), s.propertyName_, s.propertyValue_);
         }
         device_.loadDataFromHardware(core_);

         // first initialize port...
         if (initializePort()) {
            // ...then device
            device_.setName(devName_.getText());
            core_.initializeDevice(device_.getName());
            device_.loadDataFromHardware(core_);
            device_.setInitialized(true);
            device_.updateSetupProperties();
            device_.discoverPeripherals(core_);
            return true;
         }
         return false; // port failed

      } catch (Exception e) {
         showMessage(e.getMessage());
         // reset device, just in case it does not know how to handle repeated initializations
         try {
            core_.unloadDevice(device_.getName());
            core_.loadDevice(device_.getName(), device_.getLibrary(), device_.getAdapterName());
         } catch (Exception e1) {
            ReportingUtils.logError(e1);
         }
         return false;
      }
   }

   private boolean initializePort() {
      if (portDev_ != null) {
         try {
            core_.unloadDevice(portDev_.getName());
            Thread.sleep(1000);
            core_.loadDevice(portDev_.getName(), portDev_.getLibrary(), portDev_.getAdapterName());
            for (int j = 0; j < portDev_.getNumberOfProperties(); j++) {
               PropertyItem prop = portDev_.getProperty(j);
               if (prop.preInit) {
                  core_.setProperty(portDev_.getName(), prop.name, prop.value);
                  if (portDev_.findSetupProperty(prop.name) == null)
                     portDev_.addSetupProperty(new PropertyItem(prop.name, prop.value, true));
                  else
                     portDev_.setSetupPropertyValue(prop.name, prop.value);
               }
            }
            core_.initializeDevice(portDev_.getName());
            Thread.sleep(1000);
            portDev_.loadDataFromHardware(core_);
            model_.useSerialPort(portDev_, true);

         } catch (Exception e) {
            showMessage(e.getMessage());
            return false;
         }
      }
      return true;
   }

   public void showMessage(String msg) {
      JOptionPane.showMessageDialog(this, msg);
   }

   public String getDeviceName() {
      return devName_.getText();
   }

   /**
    * Thread that performs device detection
    */
   private class DetectionTask extends Thread {

      private String foundPorts[];
      private String selectedPort;

      DetectionTask(String id) {
         super(id);
         foundPorts = new String[0];
         selectedPort = new String();
      }

      @Override
      public void run() {
         boolean currentDebugLogSetting = core_.debugLogEnabled();
         String resultPorts = "";
         try {
            ArrayList<Device> ports = new ArrayList<Device>();
            model_.removeDuplicateComPorts();
            Device availablePorts[] = model_.getAvailableSerialPorts();
            String portsInModel = "Serial ports available in configuration: ";
            for (int ip = 0; ip < availablePorts.length; ++ip) {
               // NOTE: commented out code was intended to avoid checking ports
               // that were already used by other devices.
               // But, at this point we have to check all ports (used or not)
               // to provide compatibility with older device adapters that
               // share the same port
//               if (!model_.isPortInUse(availablePorts[ip])) {
//                  ports.add(availablePorts[ip]);
//               }

               ports.add(availablePorts[ip]);
            }
            for (Device p1 : ports) {
               if (0 < portsInModel.length()) {
                  portsInModel += " ";
               }
               portsInModel += p1.getName();
            }

            // if the device does respond on any port, only communicating ports
            // are allowed in the drop down
            Map<String, ArrayList<String>> portsFoundCommunicating = new HashMap<String, ArrayList<String>>();
            // if the device does not respond on any port, let the user pick
            // any port that was setup with a valid serial port name, etc.
            String looking = "";

            // during detection we'll generate lots of spurious error messages.
            ReportingUtils.logMessage("Starting port scanning; expect lots of spurious error messages");
            for (int i = 0; i < ports.size(); i++) {
               looking = "";
               try {
                  core_.setProperty(device_.getName(), MMCoreJ.getG_Keyword_Port(),
                        ports.get(i).getName());
                  if (0 < looking.length()) {
                     looking += "\n";
                  }
                  looking += device_.getName() + " on " + ports.get(i).getName();
               } catch (Exception e) {
                  // USB devices will try to open the interface and return an
                  // error on failure so do not show, but only log the error
                  ReportingUtils.logError(e);
               }

               progressDialog_.ProgressText("Looking for:\n" + looking);
               DeviceDetectionStatus st = core_.detectDevice(device_.getName());

               if (st == DeviceDetectionStatus.Unimplemented) {
                  JOptionPane.showMessageDialog(null,
                        "This device does not support auto-detection.\n" +
                        "You have to manually choose port and settings.");
                  scanStatus_.setText("Scan failed");
                  return;
               }

               if (progressDialog_.CancelRequest()) {
                  ReportingUtils.logMessage("Scan cancelled by user");
                  scanStatus_.setText("Scan cancelled");
                  return;
               }

               if (DeviceDetectionStatus.CanCommunicate == st) {
                  ArrayList<String> llist = portsFoundCommunicating.get(
                        device_.getName());
                  if (null == llist) {
                     portsFoundCommunicating.put(device_.getName(),
                           llist = new ArrayList<String>());
                  }
                  llist.add(ports.get(i).getName());
               }
            }

            // show the user the result and populate the drop down data
            ArrayList<String> communicating = portsFoundCommunicating.get(
                  device_.getName());
            foundPorts = new String[0];
            if (null != communicating) {
               if (0 < communicating.size()) {
                  foundPorts = new String[communicating.size()];
                  int aiterator = 0;
                  resultPorts += device_.getName() + " on ";
                  for (String ss : communicating) {
                     resultPorts += (ss + "\n");
                     foundPorts[aiterator++] = ss;
                  }
               }

               PropertyItem p = device_.findProperty(MMCoreJ.getG_Keyword_Port());
               p.allowed = foundPorts;
               p.value = "";
               selectedPort = "";
               if (0 < foundPorts.length) {
                  if (foundPorts.length > 1) {
                     String selectedValue = (String)JOptionPane.showInputDialog(null, "Multiple ports found, choose one", "Port",
                                            JOptionPane.INFORMATION_MESSAGE, null, foundPorts, foundPorts[0]);
                     // select the last found port
                     p.value = selectedValue;
                  }
                  else {
                     p.value = foundPorts[0];
                  }
                  selectedPort = p.value;
               }
            }
            progressDialog_.ProgressText("Found:\n " + resultPorts);
            try {
               Thread.sleep(900);
            } catch (InterruptedException ex) {
            }
         } finally { // matches try at entry
            progressDialog_.setVisible(false);
            ReportingUtils.logMessage("Finished port scanning; spurious error messages no longer expected");
            if (resultPorts.contentEquals("")) {
               scanStatus_.setText("No valid ports found");
            }
            else {
               scanStatus_.setText("Scan completed successfully");
            }
            rebuildPropTable();
            if (! (selectedPort.length() == 0)) {
               Device pd = model_.findSerialPort(selectedPort);
               if (pd != null)
                  try {
                     pd.loadDataFromHardware(core_);
                  } catch (Exception e) {
                     ReportingUtils.logError(e);
                  }
               rebuildComTable(selectedPort);
            }
            // restore normal operation of the Detect button
            detectButton_.setText(SCAN_PORTS);
         }
      }

      public void finish() {
         try {
            join();
         } catch (InterruptedException ex) {
         }
      }
   }
}
