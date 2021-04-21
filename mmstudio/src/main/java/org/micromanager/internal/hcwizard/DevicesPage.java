///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------
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
// CVS:          $Id: DevicesPage.java 7557 2011-08-04 20:31:15Z nenad $
//
package org.micromanager.internal.hcwizard;

import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import mmcorej.MMCoreJ;
import net.miginfocom.swing.MigLayout;
import org.micromanager.internal.utils.DaytimeNighttime;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ReportingUtils;

/** Wizard page to add or remove devices. */
public final class DevicesPage extends PagePanel
    implements ListSelectionListener, MouseListener, TreeSelectionListener {
  private static final long serialVersionUID = 1L;
  public static final String WEBSITE_ROOT = "https://micro-manager.org/wiki/";

  private final JTable deviceTable_;
  private final JScrollPane installedScrollPane_;
  private final JButton editButton;
  private final JButton removeButton;
  private final JButton peripheralsButton;
  private final JScrollPane availableScrollPane_;
  private boolean listByLib_;
  private TreeWContextMenu theTree_;
  String libraryDocumentationName_;
  private JComboBox byLibCombo_;
  private final AtomicBoolean amLoadingPage_;

  ///////////////////////////////////////////////////////////
  /** Inner class DeviceTable_TableModel */
  class DeviceTable_TableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;

    public final String[] COLUMN_NAMES =
        new String[] {"Name", "Adapter/Module", "Description", "Status"};

    MicroscopeModel model_;
    Device devices_[];

    public DeviceTable_TableModel(MicroscopeModel model) {
      setMicroscopeModel(model);
    }

    public final void setMicroscopeModel(MicroscopeModel mod) {
      devices_ = mod.getDevices();
      model_ = mod;
    }

    @Override
    public int getRowCount() {
      return devices_.length;
    }

    @Override
    public int getColumnCount() {
      return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int columnIndex) {
      return COLUMN_NAMES[columnIndex];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {

      switch (columnIndex) {
        case 0:
          return devices_[rowIndex].getName();
        case 1:
          return devices_[rowIndex].getAdapterName() + "/" + devices_[rowIndex].getLibrary();
        case 2:
          return devices_[rowIndex].getDescription();
        default:
          if (devices_[rowIndex].isCore()) return "Default";
          else return devices_[rowIndex].isInitialized() ? "OK" : "Failed";
      }
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
      String newName = (String) value;
      String oldName = devices_[row].getName();
      if (col == 0) {
        try {
          model_.changeDeviceName(oldName, newName);
          fireTableCellUpdated(row, col);
        } catch (MMConfigFileException e) {
          handleError(e.getMessage());
        }
      }
    }

    @Override
    public boolean isCellEditable(int nRow, int nCol) {
      return false;
    }

    public void refresh() {
      devices_ = model_.getDevices();
      this.fireTableDataChanged();
    }
  }

  ////////////////////////////////////////////////////////////////
  /** TreeWContextMenu */
  class TreeWContextMenu extends JTree implements ActionListener {
    private static final long serialVersionUID = 1L;
    JPopupMenu popupMenu_;
    DevicesPage dp_;

    public TreeWContextMenu(DefaultMutableTreeNode n, DevicesPage d) {
      super(n);
      dp_ = d;
      popupMenu_ = new JPopupMenu();
      JMenuItem jmi = new JMenuItem("Add");
      jmi.setActionCommand("add");
      jmi.addActionListener(this);
      popupMenu_.add(jmi);
      jmi = new JMenuItem("Help");
      jmi.setActionCommand("help");
      jmi.addActionListener(this);

      popupMenu_.add(jmi);
      popupMenu_.setOpaque(true);
      popupMenu_.setLightWeightPopupEnabled(true);

      super.addMouseListener(
          new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
              if (e.isPopupTrigger()) {
                popupMenu_.show((JComponent) e.getSource(), e.getX(), e.getY());
              }
            }
          });

      // Add the selected device when the Enter key is pressed.
      super.addKeyListener(
          new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
              if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                addDevice();
              }
            }
          });
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
      if (ae.getActionCommand().equals("help")) {
        dp_.displayDocumentation();
      } else if (ae.getActionCommand().equals("add")) {
        if (dp_.addDevice()) {
          dp_.rebuildDevicesTable();
        }
      }
    }
  }

  //////////////////////////////////////////////////
  /** TreeMouseListener */
  class TreeMouseListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent e) {
      if (2 == e.getClickCount()) {
        if (addDevice()) {
          rebuildDevicesTable();
        }
      }
    }
  }

  /** Create the panel */
  public DevicesPage() {
    super();
    title_ = "Add or remove devices";
    listByLib_ = true;
    amLoadingPage_ = new AtomicBoolean(false);

    setLayout(new MigLayout("fill, flowx"));

    add(
        createHelpText(
            "Select devices from the \"Available Devices\" list to include in this configuration."),
        "spanx, growx, wrap");
    JLabel lblNewLabel = new JLabel("Installed Devices:");
    lblNewLabel.setFont(new Font("Arial", Font.BOLD, 11));
    add(lblNewLabel, "wrap");

    installedScrollPane_ = new JScrollPane();
    add(installedScrollPane_, "growy, width 430");
    deviceTable_ = new DaytimeNighttime.Table();
    deviceTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    installedScrollPane_.setViewportView(deviceTable_);
    deviceTable_.getSelectionModel().addListSelectionListener(this);

    deviceTable_.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
              editDevice();
            }
          }
        });

    editButton = new JButton("Edit...");
    editButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            editDevice();
          }
        });
    add(editButton, "split, flowy, aligny top, width 115!");
    editButton.setEnabled(false);

    peripheralsButton = new JButton("Peripherals...");
    peripheralsButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            editPeripherals();
          }
        });
    peripheralsButton.setEnabled(false);
    add(peripheralsButton, "width 115!");

    removeButton = new JButton("Remove");
    removeButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent arg0) {
            removeDevice();
          }
        });
    removeButton.setEnabled(false);
    add(removeButton, "width 115!, wrap");

    JLabel availDevices = new JLabel("Available Devices:");
    availDevices.setFont(new Font("Arial", Font.BOLD, 11));
    add(availDevices, "split, spanx");

    byLibCombo_ = new JComboBox(new String[] {"List by Module", "List by Type"});
    byLibCombo_.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent arg0) {
            listByLib_ = byLibCombo_.getSelectedIndex() == 0;
            buildTree();
          }
        });
    add(byLibCombo_, "wrap");

    availableScrollPane_ = new JScrollPane();
    add(availableScrollPane_, "growy, width 430");

    final JButton addButton = new JButton("Add...");
    addButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent arg0) {
            addDevice();
          }
        });
    add(addButton, "split, flowy, aligny top, width 115!");

    JButton helpButton = new JButton("Help");
    helpButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            displayDocumentation();
          }
        });
    helpButton.setBounds(451, 319, 99, 23);
    add(helpButton, "width 115!");
  }

  protected void editPeripherals() {
    int selRow = deviceTable_.getSelectedRow();
    if (selRow < 0) return;
    String devName = (String) deviceTable_.getValueAt(selRow, 0);

    Device dev = model_.findDevice(devName);

    String installed[] = dev.getPeripherals();
    Vector<Device> peripherals = new Vector<>();

    // find which devices can be installed
    for (int i = 0; i < installed.length; i++) {
      try {
        if (!model_.hasAdapterName(dev.getLibrary(), dev.getName(), installed[i])) {
          String description = model_.getDeviceDescription(dev.getLibrary(), installed[i]);
          Device newDev = new Device(installed[i], dev.getLibrary(), installed[i], description);
          peripherals.add(newDev);
        }
      } catch (Exception e) {
        ReportingUtils.logError(e.getMessage());
      }
    }

    // display dialog and load selected
    if (peripherals.size() > 0) {
      PeripheralSetupDlg dlgp =
          new PeripheralSetupDlg(model_, studio_.core(), dev.getName(), peripherals);
      dlgp.setVisible(true);
      Device sel[] = dlgp.getSelectedPeripherals();
      for (int i = 0; i < sel.length; i++) {
        try {
          core_.loadDevice(sel[i].getName(), sel[i].getLibrary(), sel[i].getAdapterName());
          sel[i].setParentHub(dev.getName());
          core_.setParentLabel(sel[i].getName(), dev.getName());
          sel[i].loadDataFromHardware(core_);
          model_.addDevice(sel[i]);

          // offer to edit pre-init properties
          String props[] = sel[i].getPreInitProperties();
          if (props.length > 0) {
            DeviceSetupDlg dlgProps = new DeviceSetupDlg(model_, studio_, sel[i]);
            if (!sel[i].isInitialized()) {
              core_.unloadDevice(sel[i].getName());
              model_.removeDevice(sel[i].getName());
            }
          } else {
            core_.initializeDevice(sel[i].getName());
            sel[i].setInitialized(true);
          }
        } catch (MMConfigFileException e) {
          JOptionPane.showMessageDialog(this, e.getMessage());
        } catch (Exception e) {
          JOptionPane.showMessageDialog(this, e.getMessage());
        }
      }
      rebuildDevicesTable();
    } else {
      handleError("There are no available peripheral devices.");
    }
  }

  private void editDevice() {
    int selRow = deviceTable_.getSelectedRow();
    if (selRow < 0) return;
    String devName = (String) deviceTable_.getValueAt(selRow, 0);

    Device dev = model_.findDevice(devName);
    try {
      dev.loadDataFromHardware(core_);
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    DeviceSetupDlg dlg = new DeviceSetupDlg(model_, studio_, dev);
    model_.setModified(true);

    if (!dev.isInitialized()) {
      // user canceled or things did not work out
      int ret =
          JOptionPane.showConfirmDialog(
              this,
              "Device setup did not work out. Remove from the list?",
              "Device failed",
              JOptionPane.YES_NO_OPTION);
      if (ret == JOptionPane.YES_OPTION) {
        model_.removeDevice(dev.getName());
        try {
          core_.unloadDevice(dev.getName());
        } catch (Exception e) {
          JOptionPane.showMessageDialog(this, e.getMessage());
        }
      }
    }
    rebuildDevicesTable();
  }

  protected void removeDevice() {
    int sel = deviceTable_.getSelectedRow();
    if (sel < 0) return;
    String devName = (String) deviceTable_.getValueAt(sel, 0);

    if (devName.contentEquals(new StringBuffer().append(MMCoreJ.getG_Keyword_CoreDevice()))) {
      handleError(MMCoreJ.getG_Keyword_CoreDevice() + " device can't be removed!");
      return;
    }
    model_.removePeripherals(devName, core_);
    model_.removeDevice(devName);
    try {
      core_.unloadDevice(devName);
    } catch (Exception e) {
      handleError(e.getMessage());
    }
    rebuildDevicesTable();
  }

  public void rebuildDevicesTable() {
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

  @Override
  public void refresh() {
    rebuildDevicesTable();
  }

  @Override
  public boolean enterPage(final boolean fromNextPage) {
    Cursor oldCur = getCursor();
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    amLoadingPage_.set(true);
    monitorEnteringPage();
    try {
      // double check that list of device libraries is valid before continuing.
      model_.removeDuplicateComPorts();
      rebuildDevicesTable();
      if (fromNextPage) {
        // do nothing for now
      } else {
        model_.loadModel(core_);
        model_.initializeModel(core_, amLoadingPage_);
      }
      buildTree();
      return true;
    } catch (Exception e) {
      // Hide the loading dialog *first*, or else the error dialog can't be
      // interacted with.
      amLoadingPage_.set(false);
      ReportingUtils.showError(e);
      // Clean up from loading the model.
      try {
        core_.unloadAllDevices();
      } catch (Exception e2) {
        ReportingUtils.logError(e2, "Error unloading devices");
      }
      setCursor(Cursor.getDefaultCursor());
    } finally {
      setCursor(oldCur);
      amLoadingPage_.set(false);
    }
    return false;
  }

  // Displays a pop-up dialog telling the user to wait while the page loads,
  // after a 500ms delay since sometimes this page loads quickly. Creates a
  // new thread to wait the 500ms, of course.
  // This is really ugly: because the wait dialog must be modal to appear on
  // top of the config wizard dialog (which is also modal), as soon as we call
  // setVisible(), we block -- so the action to dismiss the wait dialog must
  // be on a *second* separate thread.
  private void monitorEnteringPage() {
    final JDialog waiter =
        GUIUtils.createBareMessageDialog(parent_, "Loading devices, please wait...");
    waiter.setAlwaysOnTop(true);
    waiter.setModal(true);
    Thread showThread =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                try {
                  Thread.sleep(500);
                } catch (InterruptedException e) {
                  // This should never happen.
                  ReportingUtils.logError(e, "Interrupted waiting to show wait dialog");
                }
                if (amLoadingPage_.get()) {
                  waiter.setVisible(true);
                  waiter.toFront(); // This blocks this thread.
                }
              }
            });
    Thread hideThread =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                // TODO: use proper synchronization
                while (amLoadingPage_.get()) {
                  try {
                    Thread.sleep(100);
                  } catch (InterruptedException e) {
                    // This should be impossible.
                    ReportingUtils.logError(e, "Interrupted while waiting to hide wait dialog");
                  }
                }
                waiter.dispose();
              }
            });
    hideThread.start();
    showThread.start();
  }

  @Override
  public boolean exitPage(boolean toNextPage) {
    Device devs[] = model_.getDevices();
    for (Device d : devs) {
      if (!d.isCore() && !d.isInitialized()) {
        JOptionPane.showMessageDialog(
            this,
            "Unable to continue: at least one device failed.\n"
                + "To proceed to next step, either remove failed device(s) from the list,\nor edit settings until the status reads OK.\n"
                + "To avoid making any changes exit the wizard without saving the configuration.");
        return toNextPage ? false : true;
      }

      // refresh parent ID references (backward compatibility with old config files)
      try {
        if (d.getParentHub().length() == 0) {
          String parentID = core_.getParentLabel(d.getName());
          d.setParentHub(parentID);
          if (d.isStage()) {
            d.setFocusDirection(core_.getFocusDirection(d.getName()));
          }
        }
      } catch (Exception e) {
        ReportingUtils.logError(e);
      }
    }
    return true;
  }

  @Override
  public void loadSettings() {}

  @Override
  public void saveSettings() {}

  /** Handler for list selection events in our device table */
  @Override
  public void valueChanged(ListSelectionEvent e) {
    int row = deviceTable_.getSelectedRow();
    if (row < 0) {
      // nothing selected
      editButton.setEnabled(false);
      removeButton.setEnabled(false);
      return;
    }

    String devName = (String) deviceTable_.getValueAt(row, 0);
    Device dev = model_.findDevice(devName);
    if (dev == null) {
      // device selected but not found in the model
      // this should never happen
      ReportingUtils.logError("Internal error in PeripheralSetupDlg: device not found");
      editButton.setEnabled(false);
      removeButton.setEnabled(false);
      peripheralsButton.setEnabled(false);
      return;
    }

    // if device is hub it may have some children available
    peripheralsButton.setEnabled(dev.isHub() && dev.getPeripherals().length > 0);

    // settings can be edited unless device is core
    editButton.setEnabled(!dev.isCore());

    // any selected device can be removed unless it is Core
    removeButton.setEnabled(!dev.isCore());
  }

  @Override
  public void valueChanged(TreeSelectionEvent event) {

    // update URL for library documentation
    int srows[] = theTree_.getSelectionRows();
    if (null != srows) {
      if (0 < srows.length) {
        if (0 < srows[0]) {
          DeviceTreeNode node = (DeviceTreeNode) theTree_.getLastSelectedPathComponent();
          Object uo = node.getUserObject();
          if (uo != null) {
            if (uo.getClass().isArray()) {
              libraryDocumentationName_ = ((Object[]) uo)[0].toString();
            } else {
              libraryDocumentationName_ = uo.toString();
            }
          }
        }
      }
    }
  }

  private boolean addDevice() {
    int srows[] = theTree_.getSelectionRows();
    if (srows == null) {
      return false;
    }
    if (0 < srows.length) {
      if (0 < srows[0]) {
        DeviceTreeNode node = (DeviceTreeNode) theTree_.getLastSelectedPathComponent();

        Object[] userData = node.getUserDataArray();
        if (null == userData) {
          // Adding a folder; invalid operation, so do nothing.
          return false;
        }

        String adapterName = userData[1].toString();
        String lib = userData[0].toString();
        String descr = userData[2].toString();

        // load new device
        String label = adapterName;
        Device d = model_.findDevice(label);
        int retries = 0;
        while (d != null) {
          retries++;
          label = adapterName + "-" + retries;
          d = model_.findDevice(label);
        }

        Device dev;
        try {
          core_.loadDevice(label, lib, adapterName);
          dev = new Device(label, lib, adapterName, descr);
          dev.loadDataFromHardware(core_);
          model_.addDevice(dev);
        } catch (Exception e) {
          JOptionPane.showMessageDialog(this, e.getMessage());
          return false;
        }

        // open device setup dialog
        DeviceSetupDlg dlg = new DeviceSetupDlg(model_, studio_, dev);

        if (!dev.isInitialized()) {
          // user canceled or things did not work out
          model_.removeDevice(dev.getName());
          try {
            core_.unloadDevice(dev.getName());
          } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
          }
          return false;
        }
        // > at this point device is initialized and added to the model
        refresh();

        // check if there are any child devices installed
        if (dev.isHub() && !dev.getName().equals("Core")) {

          String installed[] = dev.getPeripherals();
          Vector<Device> peripherals = new Vector<>();

          for (int i = 0; i < installed.length; i++) {
            try {
              if (!model_.hasAdapterName(dev.getLibrary(), dev.getName(), installed[i])) {
                String description = model_.getDeviceDescription(dev.getLibrary(), installed[i]);
                Device newDev =
                    new Device(installed[i], dev.getLibrary(), installed[i], description);
                peripherals.add(newDev);
              }
            } catch (Exception e) {
              ReportingUtils.logError(e.getMessage());
            }
          }

          if (peripherals.size() > 0) {
            PeripheralSetupDlg dlgp =
                new PeripheralSetupDlg(model_, core_, dev.getName(), peripherals);
            dlgp.setVisible(true);
            Device sel[] = dlgp.getSelectedPeripherals();
            for (int i = 0; i < sel.length; i++) {
              try {
                core_.loadDevice(sel[i].getName(), sel[i].getLibrary(), sel[i].getAdapterName());
                // core_.loadPeripheralDevice(sel[i].getName(), dev.getName(),
                // sel[i].getAdapterName());
                sel[i].setParentHub(dev.getName());
                core_.setParentLabel(sel[i].getName(), dev.getName());
                model_.addDevice(sel[i]);
                sel[i].loadDataFromHardware(core_);

                // offer to edit pre-init properties
                String props[] = sel[i].getPreInitProperties();
                if (props.length > 0) {
                  DeviceSetupDlg dlgProps = new DeviceSetupDlg(model_, studio_, sel[i]);
                  if (!sel[i].isInitialized()) {
                    core_.unloadDevice(sel[i].getName());
                    model_.removeDevice(sel[i].getName());
                  }
                } else {
                  core_.initializeDevice(sel[i].getName());
                  sel[i].setInitialized(true);
                }

              } catch (MMConfigFileException e) {
                JOptionPane.showMessageDialog(this, e.getMessage());
              } catch (Exception e) {
                JOptionPane.showMessageDialog(this, e.getMessage());
              } finally {
                refresh();
              }
            }
          }
        }
      }
    }
    return true;
  }

  private void buildTree() {
    if (listByLib_) buildTreeByLib(model_);
    else buildTreeByType(model_);
    availableScrollPane_.setViewportView(theTree_);
  }

  private void buildTreeByType(MicroscopeModel model) {
    Device devices[] = model.getAvailableDevicesCompact();

    // organize devices by type
    Hashtable<String, Vector<Device>> nodes = new Hashtable<String, Vector<Device>>();
    for (int i = 0; i < devices.length; i++) {
      if (nodes.containsKey(devices[i].getTypeAsString()))
        nodes.get(devices[i].getTypeAsString()).add(devices[i]);
      else {
        Vector<Device> v = new Vector<Device>();
        v.add(devices[i]);
        nodes.put(devices[i].getTypeAsString(), v);
      }
    }

    DefaultMutableTreeNode root =
        new DefaultMutableTreeNode("Devices supported by " + "\u00B5" + "Manager");

    DeviceTreeNode node = null;
    Object nodeNames[] = nodes.keySet().toArray();
    for (Object nodeName : nodeNames) {
      // create a new node of devices for this library
      node = new DeviceTreeNode((String) nodeName, false);
      root.add(node);
      Vector<Device> devs = nodes.get(nodeName);
      for (int i = 0; i < devs.size(); i++) {
        Object[] userObject = {
          devs.get(i).getLibrary(),
          devs.get(i).getAdapterName(),
          devs.get(i).getDescription(),
          new Boolean(devs.get(i).isHub())
        };
        DeviceTreeNode aLeaf = new DeviceTreeNode("", false);
        aLeaf.setUserObject(userObject);
        node.add(aLeaf);
      }
    }
    // try building a tree
    theTree_ = new TreeWContextMenu(root, this);
    theTree_.addTreeSelectionListener(this);

    MouseListener ml = new TreeMouseListener();

    theTree_.addMouseListener(ml);
    theTree_.setRootVisible(false);
    theTree_.setShowsRootHandles(true);
  }

  private void buildTreeByLib(MicroscopeModel model) {
    Device devices[] = model.getAvailableDevicesCompact();
    Arrays.sort(devices, new DeviceSorter());

    String thisLibrary = "";
    DefaultMutableTreeNode root =
        new DefaultMutableTreeNode("Devices supported by " + "\u00B5" + "Manager");
    DeviceTreeNode node = null;
    for (int idd = 0; idd < devices.length; ++idd) {
      // assume that the first library doesn't have an empty name! (of
      // course!)
      if (0 != thisLibrary.compareTo(devices[idd].getLibrary())) {
        // create a new node of devices for this library
        node = new DeviceTreeNode(devices[idd].getLibrary(), true);
        root.add(node);
        thisLibrary = devices[idd].getLibrary(); // remember which library
        // we are processing
      }
      Object[] userObject = {
        devices[idd].getLibrary(),
        devices[idd].getAdapterName(),
        devices[idd].getDescription(),
        new Boolean(devices[idd].isHub())
      };
      DeviceTreeNode aLeaf = new DeviceTreeNode("", true);
      aLeaf.setUserObject(userObject);
      node.add(aLeaf);
    }
    String badLibs[] = model.getBadLibraries();
    for (String lib : badLibs) {
      DeviceTreeNode nd = new DeviceTreeNode(lib + " (unavailable)", true);
      root.add(nd);
    }

    // try building a tree
    theTree_ = new TreeWContextMenu(root, this);
    theTree_.addTreeSelectionListener(this);

    MouseListener ml = new TreeMouseListener();

    theTree_.addMouseListener(ml);
    theTree_.setRootVisible(false);
    theTree_.setShowsRootHandles(true);
  }

  private void displayDocumentation() {
    try {
      ij.plugin.BrowserLauncher.openURL(WEBSITE_ROOT + libraryDocumentationName_);
    } catch (IOException e1) {
      ReportingUtils.showError(e1);
    }
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    // TODO Auto-generated method stub

  }

  @Override
  public void mousePressed(MouseEvent e) {
    // TODO Auto-generated method stub

  }

  @Override
  public void mouseReleased(MouseEvent e) {
    // TODO Auto-generated method stub

  }

  @Override
  public void mouseEntered(MouseEvent e) {
    // TODO Auto-generated method stub

  }

  @Override
  public void mouseExited(MouseEvent e) {
    // TODO Auto-generated method stub

  }
}
