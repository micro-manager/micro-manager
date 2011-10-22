package org.micromanager.conf2;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableColumn;

import mmcorej.CMMCore;
import mmcorej.DeviceDetectionStatus;
import mmcorej.DeviceType;
import mmcorej.MMCoreJ;
import mmcorej.StrVector;

import org.micromanager.utils.MMDialog;
import org.micromanager.utils.PropertyItem;
import org.micromanager.utils.PropertyNameCellRenderer;
import org.micromanager.utils.PropertyValueCellEditor;
import org.micromanager.utils.PropertyValueCellRenderer;
import org.micromanager.utils.ReportingUtils;

public class DeviceSetupDlg extends MMDialog {
   private static final long serialVersionUID = 1L;
   private final JPanel contentPanel = new JPanel();
   private CMMCore core;
   private Device portDev;
   private MicroscopeModel model;
   private Device dev;
   private JTable propTable;
   private JButton detectButton;
   private boolean requestCancel;
   private DetectorJDialog progressDialog;
   private DetectionTask dt;
   private final String DETECT_PORTS = "Scan";
   private JTable comTable;
   private JTextField devLabel;

   /**
    * Create the dialog.
    */
   public DeviceSetupDlg(MicroscopeModel mod, CMMCore c, Device d) {
      //setModalityType(ModalityType.APPLICATION_MODAL);
      setModal(true);
      setBounds(100, 100, 478, 515);
      model = mod;
      core = c;
      portDev = null;
      dev = d;
      
      getContentPane().setLayout(new BorderLayout());
      contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
      getContentPane().add(contentPanel, BorderLayout.CENTER);
      contentPanel.setLayout(null);
      {
         JLabel lblNewLabel = new JLabel("Label");
         lblNewLabel.setBounds(10, 11, 35, 14);
         contentPanel.add(lblNewLabel);
      }
      
      devLabel = new JTextField(dev.getName());
      devLabel.setBounds(47, 8, 165, 20);
      contentPanel.add(devLabel);
      devLabel.setColumns(10);
                 
      {
         JPanel buttonPane = new JPanel();
         buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
         getContentPane().add(buttonPane, BorderLayout.SOUTH);
         {
            JButton okButton = new JButton("OK");
            okButton.addActionListener(new ActionListener() {
               public void actionPerformed(ActionEvent e) {
                  onOK();
               }
            });
            okButton.setActionCommand("OK");
            buttonPane.add(okButton);
            getRootPane().setDefaultButton(okButton);
         }
         {
            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(new ActionListener() {
               public void actionPerformed(ActionEvent e) {
                  onCancel();
               }
            });
            cancelButton.setActionCommand("Cancel");
            buttonPane.add(cancelButton);
         }
      }
      
      addWindowListener(new WindowAdapter() {
         public void windowClosing(final WindowEvent e) {
            savePosition();
         }
      });

      Rectangle r = getBounds();
      loadPosition(r.x, r.y);
      
      setTitle("Device: " + dev.getAdapterName() + " | Library: " + dev.getLibrary());
      
      JScrollPane scrollPaneProp = new JScrollPane();
      scrollPaneProp.setBounds(10, 64, 442, 164);
      contentPanel.add(scrollPaneProp);
      
      propTable = new JTable();
      propTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      propTable.setAutoCreateColumnsFromModel(false);
      scrollPaneProp.setViewportView(propTable);
      
      detectButton = new JButton(DETECT_PORTS);
      detectButton.setEnabled(false);
      detectButton.addActionListener(new ActionListener() {

         public void actionPerformed(ActionEvent e) {
            if (detectButton.getText().equalsIgnoreCase(DETECT_PORTS)) {
               requestCancel = false;
               progressDialog = new DetectorJDialog(DeviceSetupDlg.this, false);
               progressDialog.setTitle("\u00B5" + "Manager device detection");
               progressDialog.setLocationRelativeTo(DeviceSetupDlg.this);
               progressDialog.setSize(483, 288);
               progressDialog.setVisible(true);
               dt = new DetectionTask("serial_detect");
               dt.start();
               detectButton.setText("Cancel");
           } else {
               requestCancel = true;
               dt.finish();
               detectButton.setText(DETECT_PORTS);
           }

         }
      });
      detectButton.setToolTipText("Scan COM ports to detect this device");
      detectButton.setBounds(359, 235, 93, 23);
      contentPanel.add(detectButton);
      
      JLabel lblNewLabel_1 = new JLabel("Port Properties (RS 232 settings)");
      lblNewLabel_1.setBounds(10, 239, 442, 14);
      contentPanel.add(lblNewLabel_1);
      
      JScrollPane scrollPaneCOM = new JScrollPane();
      scrollPaneCOM.setBounds(10, 264, 442, 169);
      contentPanel.add(scrollPaneCOM);
      
      comTable = new JTable();
      scrollPaneCOM.setViewportView(comTable);
      comTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      comTable.setAutoCreateColumnsFromModel(false);
      
      JLabel lblNewLabel_2 = new JLabel("Initialization Properties");
      lblNewLabel_2.setBounds(10, 39, 442, 14);
      contentPanel.add(lblNewLabel_2);
      
      loadSettings();
   }

   protected void onCancel() {
      dispose();
   }

   protected void onOK() {
      Device d = model.findDevice(devLabel.getText());
      if (d==null) {
         showMessage("Device " + devLabel.getText() + " is not loaded properly.\nPress Cancel and try again.");
         return;
      }
      
      if (initializeDevice())
         dispose();
      else
         return;
   }  

   private void loadSettings() {

      rebuildPropTable();

      // setup com ports
      ArrayList<Device> ports = new ArrayList<Device>();
      Device avPorts[] = model.getAvailableSerialPorts();
      for(int i=0; i<avPorts.length; i++)
         if (!model.isPortInUse(avPorts[i]))
            ports.add(avPorts[i]);

      // identify "port" properties and assign available com ports declared for use
      for (int i=0; i<dev.getNumberOfProperties(); i++) {
         PropertyItem p = dev.getProperty(i);
         if (p.name.compareTo(MMCoreJ.getG_Keyword_Port()) == 0) {
            if (ports.size() == 0) {
               // no ports available, tell user and return
               JOptionPane.showMessageDialog(null, "No Serial Ports are found in your computer!");
               return;
            }
            String allowed[] = new String[ports.size()];
            for (int k=0; k<ports.size(); k++)
               allowed[k] = ports.get(k).getName();
            p.allowed = allowed;
         }
      }
   }
   
   private void rebuildPropTable() {
      Device d = model.findDevice(devLabel.getText());
      if (d == null)
         return;
      
      PropertyTableModel tm = new PropertyTableModel(model, d, this);
      propTable.setModel(tm);
      PropertyValueCellEditor propValueEditor = new PropertyValueCellEditor();
      PropertyValueCellRenderer propValueRenderer = new PropertyValueCellRenderer();
      PropertyNameCellRenderer propNameRenderer = new PropertyNameCellRenderer();
      if (propTable.getColumnCount() == 0) {
          TableColumn column;
          column = new TableColumn(0, 200, propNameRenderer, null);
          propTable.addColumn(column);
          column = new TableColumn(1, 200, propNameRenderer, null);
          propTable.addColumn(column);
          column = new TableColumn(2, 200, propValueRenderer, propValueEditor);
          propTable.addColumn(column);
      }
      tm.fireTableStructureChanged();
      tm.fireTableDataChanged();
      boolean any = false;
      Device devices[] = model.getDevices();
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
      detectButton.setEnabled(any);
      propTable.repaint();
   }

   public void rebuildComTable(String portName) {
      if (portName == null)
         return;
      
      portDev = model.findSerialPort(portName);
      if (portDev == null)
         return;
      
      // load port if necessary
      StrVector loadedPorts = core.getLoadedDevicesOfType(DeviceType.SerialDevice);
      Iterator<String> lp = loadedPorts.iterator();
      boolean loaded = false;
      while (lp.hasNext()) {
         lp.next().compareTo(portName);
         loaded = true;
      }
      if (! loaded) {
         try {
            core.loadDevice(portDev.getName(), portDev.getLibrary(), portDev.getAdapterName());
            portDev.loadDataFromHardware(core);
         } catch (Exception e) {
            ReportingUtils.logError(e);
         }
      }
      
      try {
         System.out.println("rebuild " + portDev.getPropertyValue("BaudRate"));
      } catch (MMConfigFileException e1) {
         // TODO Auto-generated catch block
         e1.printStackTrace();
      }
      
      ComPropTableModel tm = new ComPropTableModel(model, portDev);
      comTable.setModel(tm);
      PropertyValueCellEditor propValueEditor = new PropertyValueCellEditor();
      PropertyValueCellRenderer propValueRenderer = new PropertyValueCellRenderer();
      PropertyNameCellRenderer propNameRenderer = new PropertyNameCellRenderer();
      if (comTable.getColumnCount() == 0) {
          TableColumn column;
          column = new TableColumn(0, 200, propNameRenderer, null);
          comTable.addColumn(column);
          column = new TableColumn(1, 200, propNameRenderer, null);
          comTable.addColumn(column);
          column = new TableColumn(2, 200, propValueRenderer, propValueEditor);
          comTable.addColumn(column);
      }
      tm.fireTableStructureChanged();
      tm.fireTableDataChanged();
      comTable.repaint();
   }

   private boolean initializeDevice() {
      try {
         if (dev.isInitialized()) {
            // re-initialize
            return true;
         } else {
            // transfer properties to device
            PropertyTableModel ptm = (PropertyTableModel)propTable.getModel();
            for (int i=0; i<ptm.getRowCount(); i++) {
               Setting s = ptm.getSetting(i);
               core.setProperty(s.deviceName_, s.propertyName_, s.propertyValue_);
            }

            // first initialize port...
            if (initializePort()) {
               // ...then device
               dev.setName(devLabel.getText());
               core.initializeDevice(dev.getName());
               dev.setInitialized(true);
               dev.discoverPeripherals(core);
               return true;
            }
            
            return false; // port failed
         }
      } catch (Exception e) {
         showMessage(e.getMessage());
         return false;
      }
   }
   
   private boolean initializePort() {
      if (portDev != null) {
         try {
            core.unloadDevice(portDev.getName());
            core.loadDevice(portDev.getName(), portDev.getLibrary(), portDev.getAdapterName());
            System.out.println("InitPort " + portDev.getPropertyValue("BaudRate"));
            for (int j = 0; j < portDev.getNumberOfProperties(); j++) {
               PropertyItem prop = portDev.getProperty(j);
               core.setProperty(portDev.getName(), prop.name, prop.value);
            }
            core.initializeDevice(portDev.getName());
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
   
   private class DetectionTask extends Thread {

      DetectionTask(String id) {
         super(id);
      }

      public void run() {
         boolean currentDebugLogSetting = core.debugLogEnabled();
         try {
            ArrayList<Device> ports = new ArrayList<Device>();
            model.removeDuplicateComPorts();
            Device availablePorts[] = model.getAvailableSerialPorts();
            for (Device p : availablePorts) {
               model.useSerialPort(p, true);
            }
            String portsInModel = "Serial ports available in configuration: ";
            for (int ip = 0; ip < availablePorts.length; ++ip) {
               if (model.isPortInUse(availablePorts[ip])) {
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
                  st0_ = core.detectDevice(getName());
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
            Device devices[] = model.getDevices();
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
            core.enableDebugLog(false);
            if (devicesToSearch.size() <= ports.size()) {
               // for case where there are more serial ports than devices
               for (int iteration = 0; iteration < ports.size(); ++iteration) {
                  detectors.clear();
                  looking = "";
                  for (int diterator = 0; diterator < devicesToSearch.size(); ++diterator) {
                     int portOffset = (diterator + iteration) % ports.size();
                     try {
                        core.setProperty(devicesToSearch.get(diterator).getName(), MMCoreJ.getG_Keyword_Port(), ports.get(portOffset).getName());
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
                  progressDialog.ProgressText("Looking for:\n" + looking);
                  for (Detector d : detectors) {
                     d.start();
                  }
                  for (Detector d : detectors) {
                     d.finish();
                     if (progressDialog.CancelRequest()| requestCancel) {
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
                        core.setProperty(devicesToSearch.get(dOffset).getName(), MMCoreJ.getG_Keyword_Port(), ports.get(piterator).getName());
                        detectors.add(new Detector(devicesToSearch.get(dOffset).getName(), ports.get(piterator).getName()));
                        if (0 < looking.length()) {
                           looking += "\n";
                        }
                        looking += devicesToSearch.get(dOffset).getName() + " on " + ports.get(piterator).getName();
                     } catch (Exception e) {
                        ReportingUtils.showError(e);
                     }
                  }
                  progressDialog.ProgressText("Looking for:\n" + looking);
                  for (Detector d : detectors) {
                     d.start();
                  }
                  for (Detector d : detectors) {
                     d.finish();
                     if (progressDialog.CancelRequest() || requestCancel) {
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
            progressDialog.ProgressText("Found:\n " + foundem);
            try {
               Thread.sleep(900);
            } catch (InterruptedException ex) {
            }
         } finally { // nmatches try at entry
            progressDialog.setVisible(false);
            core.enableDebugLog(currentDebugLogSetting);
            rebuildPropTable();
            // restore normal operation of the Detect button
            detectButton.setText(DETECT_PORTS);
         }
      }
      public void finish() {
         try {
            join();
         } catch (InterruptedException ex) {
         }
      }
   }

   public String getDeviceName() {
      return devLabel.getText();
   }
}
