///////////////////////////////////////////////////////////////////////////////
//FILE:          DevicesPage.java
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
// CVS:          $Id: DevicesPage.java 7557 2011-08-04 20:31:15Z nenad $
//
package org.micromanager.conf2;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JComponent;
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

import org.micromanager.utils.ReportingUtils;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;

/**
 * Wizard page to add or remove devices.
 */
public class DevicesPage extends PagePanel implements ListSelectionListener, MouseListener, TreeSelectionListener {
   private static final long serialVersionUID = 1L;

   private JTable deviceTable_;
   private JScrollPane installedScrollPane_;
   private static final String HELP_FILE_NAME = "conf_devices_page.html";
   private JButton editButton;
   private JButton removeButton;
   private JButton peripheralsButton;
   private boolean listByLib_;
   private TreeWContextMenu theTree_;
   private JScrollPane availableScrollPane_;
   final String documentationURLroot_;
   String libraryDocumentationName_;
   private JComboBox hubsCombo_;

private JComboBox byLibCombo_;

   ///////////////////////////////////////////////////////////
   /**
    * Inner class DeviceTable_TableModel
    */
   class DeviceTable_TableModel extends AbstractTableModel {
      private static final long serialVersionUID = 1L;

      public final String[] COLUMN_NAMES = new String[] {
            "Name",
            "Adapter/Library",
            "Description",
            "Status"
      };
      
      MicroscopeModel model_;
      Device devices_[];

      public DeviceTable_TableModel(MicroscopeModel model) {
         setMicroscopeModel(model);
      }
      
      public final void setMicroscopeModel(MicroscopeModel mod) {
         devices_ = mod.getDevices();
         model_ = mod;
      }
      
      public int getRowCount() {
         return devices_.length;
      }
      public int getColumnCount() {
         return COLUMN_NAMES.length;
      }
      @Override
      public String getColumnName(int columnIndex) {
         return COLUMN_NAMES[columnIndex];
      }
      public Object getValueAt(int rowIndex, int columnIndex) {
         
         if (columnIndex == 0)
            return devices_[rowIndex].getName();
         else if (columnIndex == 1)
            return new String (devices_[rowIndex].getAdapterName() + "/" + devices_[rowIndex].getLibrary());
         else if (columnIndex == 2)
            return devices_[rowIndex].getDescription();
         else {
            if (devices_[rowIndex].isCore())
               return "Default";
            else
               return devices_[rowIndex].isInitialized() ? "OK" : "Failed";
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
            } catch (Exception e) {
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
   /**
    * TreeWContextMenu
    */
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

         addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
               if (e.isPopupTrigger()) {
                  popupMenu_.show((JComponent) e.getSource(), e.getX(),
                        e.getY());
               }
            }

         });

      }

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
   /**
    * TreeMouseListener
    */
   class TreeMouseListener extends MouseAdapter {
      public void mousePressed(MouseEvent e) {
         if (2 == e.getClickCount()) {
            if (addDevice()) {
               rebuildDevicesTable();
            }
         }
      }
   }

   /**
    * Create the panel
    */
   public DevicesPage(Preferences prefs) {
      super();
      title_ = "Add or remove devices";
      helpText_ = "The list of selected devices is displayed above. " +
      "You can add or remove devices to/from this list.\n" +
      "The first column shows the device's assigned name for this particular configuration. " +
      "In subsequent steps devices will be referred to by their assigned names.\n\n" +
      "You can edit device names by double-clicking in the first column. Device name must be unique and should not contain any special characters.";
      
      listByLib_ = true;

      setLayout(null);
      prefs_ = prefs;
      setHelpFileName(HELP_FILE_NAME);
      documentationURLroot_ = "https://micro-manager.org/wiki/";

      installedScrollPane_ = new JScrollPane();
      installedScrollPane_.setBounds(10, 21, 431, 241);
      add(installedScrollPane_);

      deviceTable_ = new JTable();
      deviceTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      installedScrollPane_.setViewportView(deviceTable_);
      deviceTable_.getSelectionModel().addListSelectionListener(this);
      
      deviceTable_.addMouseListener(new MouseAdapter() {
         public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
               editDevice();
            }
         }});

      final JButton addButton = new JButton();
      addButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            addDevice();
         }
      });
      addButton.setText("Add...");
      addButton.setBounds(451, 291, 99, 23);
      add(addButton);

      removeButton = new JButton();
      removeButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            removeDevice();
         }
      });
      removeButton.setText("Remove");
      removeButton.setBounds(451, 72, 99, 23);
      add(removeButton);
      removeButton.setEnabled(false);
      
      editButton = new JButton("Edit...");
      editButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            editDevice();
         }
      });
      editButton.setBounds(451, 21, 99, 23);
      add(editButton);
      editButton.setEnabled(false);
      
      peripheralsButton = new JButton("Peripherals...");
      peripheralsButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            editPeripherals();
         }
      });
      peripheralsButton.setEnabled(false);
      peripheralsButton.setBounds(451, 46, 99, 23);
      add(peripheralsButton);
      
      JLabel lblNewLabel = new JLabel("Installed Devices:");
      lblNewLabel.setFont(new Font("Tahoma", Font.BOLD, 11));
      lblNewLabel.setBounds(10, 0, 431, 14);
      add(lblNewLabel);
      
      JLabel lblNewLabel_1 = new JLabel("Available Devices:");
      lblNewLabel_1.setFont(new Font("Tahoma", Font.BOLD, 11));
      lblNewLabel_1.setBounds(10, 273, 128, 14);
      add(lblNewLabel_1);
      
      availableScrollPane_ = new JScrollPane((Component) null);
      availableScrollPane_.setBounds(10, 299, 431, 250);
      add(availableScrollPane_);
      
      JButton helpButton = new JButton("Help");
      helpButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            displayDocumentation();
         }
      });
      helpButton.setBounds(451, 319, 99, 23);
      add(helpButton);
      
      byLibCombo_ = new JComboBox();
      byLibCombo_.addActionListener(new ActionListener() {
      	public void actionPerformed(ActionEvent arg0) {
      		if (byLibCombo_.getSelectedIndex() == 0)
      			listByLib_ = true;
      		else {
      			listByLib_ = false;
      			hubsCombo_.setSelectedIndex(1); // force show-all
      		}
      		buildTree();
      	}
      });
      byLibCombo_.setModel(new DefaultComboBoxModel(new String[] {"list by vendor", "list by type"}));
      byLibCombo_.setBounds(146, 270, 146, 20);
      add(byLibCombo_);
      
      hubsCombo_ = new JComboBox();
      hubsCombo_.addActionListener(new ActionListener() {
      	public void actionPerformed(ActionEvent e) {
      		buildTree();
      	}
      });
      hubsCombo_.setModel(new DefaultComboBoxModel(new String[] {"compact view", "show all"}));
      hubsCombo_.setBounds(302, 270, 139, 20);
      add(hubsCombo_);
      //

   }
   

   protected void editPeripherals() {
      int selRow = deviceTable_.getSelectedRow();
      if (selRow < 0)
         return;      
      String devName = (String)deviceTable_.getValueAt(selRow, 0);
      
      Device dev = model_.findDevice(devName);

      String installed[] = dev.getPeripherals();
      Vector<Device> peripherals = new Vector<Device>();

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
         PeripheralSetupDlg dlgp = new PeripheralSetupDlg(model_, core_, dev.getName(), peripherals);
         dlgp.setVisible(true);
         Device sel[] = dlgp.getSelectedPeripherals();
         for (int i=0; i<sel.length; i++) {
            try {
               core_.loadDevice(sel[i].getName(), sel[i].getLibrary(), sel[i].getAdapterName());
               sel[i].setParentHub(dev.getName());
               core_.setParentLabel(sel[i].getName(), dev.getName());
               sel[i].loadDataFromHardware(core_);
               model_.addDevice(sel[i]);
               
               // offer to edit pre-init properties
               String props[] = sel[i].getPreInitProperties();
               if (props.length > 0) {
                  DeviceSetupDlg dlgProps = new DeviceSetupDlg(model_, core_, sel[i]);
                  dlgProps.setVisible(true);
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
      if (selRow < 0)
         return;      
      String devName = (String)deviceTable_.getValueAt(selRow, 0);
      
      Device dev = model_.findDevice(devName);
      try {
         dev.loadDataFromHardware(core_);
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
      DeviceSetupDlg dlg = new DeviceSetupDlg(model_, core_, dev);
      dlg.setVisible(true);
      model_.setModified(true);
      
      if (!dev.isInitialized()) {
         // user canceled or things did not work out
         int ret = JOptionPane.showConfirmDialog(this, "Device setup did not work out. Remove from the list?", "Device failed", JOptionPane.YES_NO_OPTION);
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
      if (sel < 0)
         return;      
      String devName = (String)deviceTable_.getValueAt(sel, 0);
      
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
   
   public void refresh() {
      rebuildDevicesTable();
   }
   
	public boolean enterPage(boolean fromNextPage) {
      Cursor oldCur = getCursor();
      setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		try {
         // double check that list of device libraries is valid before continuing.
         core_.getDeviceAdapterNames();
			model_.removeDuplicateComPorts();
			rebuildDevicesTable();
			if (fromNextPage) {
			   // do nothing for now
			} else {
			   model_.loadModel(core_);
            model_.initializeModel(core_);
			}
	      buildTree();
			return true;
		} catch (Exception e2) {
			ReportingUtils.showError(e2);
			setCursor(Cursor.getDefaultCursor());
		} finally {
         setCursor(oldCur);
      }
      
		return false;
	}

    public boolean exitPage(boolean toNextPage) {
       Device devs[] = model_.getDevices();
       for (Device d : devs) {
          if (!d.isCore() && !d.isInitialized()) {
             JOptionPane.showMessageDialog(this, "Unable to continue: at least one device failed.\n" +
                   "To proceed to next step, either remove failed device(s) from the list,\nor edit settings until the status reads OK.\n" +
                   "To avoid making any changes exit the wizard without saving the configuration.");
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

   public void loadSettings() {
      
   }
   
   public void saveSettings() {
      
   }


   /**
    * Handler for list selection events in our device table
    */
   public void valueChanged(ListSelectionEvent e) {
      int row = deviceTable_.getSelectedRow();
      if (row < 0) {
         // nothing selected
         editButton.setEnabled(false);
         removeButton.setEnabled(false);
         return;
      }
      
      String devName = (String)deviceTable_.getValueAt(row, 0);
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
               // if a folder has one child go ahead and add the children
               if (1 == node.getLeafCount()) {
                  node = (DeviceTreeNode) node.getChildAt(0);
                  userData = node.getUserDataArray();
                  if (null == userData)
                     return false;
               }
            }

            // get selected device
            if (userData == null) {
               JOptionPane.showMessageDialog(this, "Multiple devices available in this node!\nPlease expand the node and select a specific device to add.");
               return false;
            }
            String adapterName = userData[1].toString();
            String lib = userData[0].toString();
            String descr = userData[2].toString();

            // load new device
            String label = new String(adapterName);
            Device d = model_.findDevice(label);
            int retries = 0;
            while (d != null) {
               retries++;
               label = new String(adapterName + "-" + retries);
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
            DeviceSetupDlg dlg = new DeviceSetupDlg(model_, core_, dev);
            dlg.setVisible(true);

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
               Vector<Device> peripherals = new Vector<Device>();

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

               if (peripherals.size() > 0) {
                  PeripheralSetupDlg dlgp = new PeripheralSetupDlg(model_, core_, dev.getName(), peripherals);
                  dlgp.setVisible(true);
                  Device sel[] = dlgp.getSelectedPeripherals();
                  for (int i=0; i<sel.length; i++) {
                     try {
                        core_.loadDevice(sel[i].getName(), sel[i].getLibrary(), sel[i].getAdapterName());
                        //core_.loadPeripheralDevice(sel[i].getName(), dev.getName(), sel[i].getAdapterName());
                        sel[i].setParentHub(dev.getName());
                        core_.setParentLabel(sel[i].getName(), dev.getName());
                        model_.addDevice(sel[i]);
                        sel[i].loadDataFromHardware(core_);
                        
                        // offer to edit pre-init properties
                        String props[] = sel[i].getPreInitProperties();
                        if (props.length > 0) {
                           DeviceSetupDlg dlgProps = new DeviceSetupDlg(model_, core_, sel[i]);
                           dlgProps.setVisible(true);
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
      if (listByLib_)
         buildTreeByLib(model_);
      else
         buildTreeByType(model_);
      availableScrollPane_.setViewportView(theTree_);
   }
   
   private void buildTreeByType(MicroscopeModel model) {
      Device devices_[] = null;
      if (hubsCombo_.getSelectedIndex() == 1)
         devices_ = model.getAvailableDeviceList();
      else
         devices_ = model.getAvailableDevicesCompact();

      // organize devices by type
      Hashtable<String, Vector<Device>> nodes = new Hashtable<String, Vector<Device>>();
      for (int i = 0; i < devices_.length; i++) {
         if (nodes.containsKey(devices_[i].getTypeAsString()))
            nodes.get(devices_[i].getTypeAsString()).add(devices_[i]);
         else {
            Vector<Device> v = new Vector<Device>();
            v.add(devices_[i]);
            nodes.put(devices_[i].getTypeAsString(), v);
         }
      }

      DefaultMutableTreeNode root = new DefaultMutableTreeNode("Devices supported by " + "\u00B5" + "Manager");

      DeviceTreeNode node = null;
      Object nodeNames[] = nodes.keySet().toArray();
      for (Object nodeName : nodeNames) {
         // create a new node of devices for this library
         node = new DeviceTreeNode((String) nodeName, false);
         root.add(node);
         Vector<Device> devs = nodes.get(nodeName);
         for (int i = 0; i < devs.size(); i++) {
            Object[] userObject = { devs.get(i).getLibrary(), devs.get(i).getAdapterName(), devs.get(i).getDescription(), new Boolean(devs.get(i).isHub()) };
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
      Device devices_[] = null;
      if (hubsCombo_.getSelectedIndex() == 1)
         devices_ = model.getAvailableDeviceList();
      else
         devices_ = model.getAvailableDevicesCompact();

      String thisLibrary = "";
      DefaultMutableTreeNode root = new DefaultMutableTreeNode("Devices supported by " + "\u00B5" + "Manager");
      DeviceTreeNode node = null;
      for (int idd = 0; idd < devices_.length; ++idd) {
         // assume that the first library doesn't have an empty name! (of
         // course!)
         if (0 != thisLibrary.compareTo(devices_[idd].getLibrary())) {
            // create a new node of devices for this library
            node = new DeviceTreeNode(devices_[idd].getLibrary(), true);
            root.add(node);
            thisLibrary = devices_[idd].getLibrary(); // remember which library
                                                      // we are processing
         }
         Object[] userObject = { devices_[idd].getLibrary(), devices_[idd].getAdapterName(), devices_[idd].getDescription(), new Boolean(devices_[idd].isHub()) };
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
         ij.plugin.BrowserLauncher.openURL(documentationURLroot_ + libraryDocumentationName_);
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
