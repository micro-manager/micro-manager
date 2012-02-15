///////////////////////////////////////////////////////////////////////////////
//FILE:          AddDeviceDlg.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, November 07, 2006
//               New Tree View: Karl Hoover January 13, 2011 
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
// CVS:          $Id: AddDeviceDlg.java 7626 2011-08-26 01:06:20Z nenad $
package org.micromanager.conf2;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;

import mmcorej.CMMCore;

import org.micromanager.utils.MMDialog;
import org.micromanager.utils.ReportingUtils;
import javax.swing.JCheckBox;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Dialog to add a new device to the configuration.
 */
@SuppressWarnings("unused")
public class AddDeviceDlg extends MMDialog implements MouseListener,
      TreeSelectionListener {

   private static final long serialVersionUID = 1L;
   private MicroscopeModel model_;
   private DevicesPage devicesPage_;
   private TreeWContextMenu theTree_;
   final String documentationURLroot_;
   String libraryDocumentationName_;
   private JCheckBox cbShowAll_;
   private JScrollPane availableScrollPane_;
   private CMMCore core_;
   private boolean listByLib_;

   class TreeWContextMenu extends JTree implements ActionListener {
      private static final long serialVersionUID = 1L;
      JPopupMenu popupMenu_;
      AddDeviceDlg d_; // pretty ugly

      public TreeWContextMenu(DefaultMutableTreeNode n, AddDeviceDlg d) {
         super(n);
         d_ = d;
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
            d_.displayDocumentation();
         } else if (ae.getActionCommand().equals("add")) {
            if (d_.addDevice()) {
               d_.rebuildTable();
            }
         }
      }
   }

   class TreeMouseListener extends MouseAdapter {
      public void mousePressed(MouseEvent e) {
         if (2 == e.getClickCount()) {
            if (addDevice()) {
               rebuildTable();
            }
         }
      }
   }

   /**
    * Create the dialog
    */
   public AddDeviceDlg(MicroscopeModel model, CMMCore core, DevicesPage devicesPage) {
      super();
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
            savePosition();
         }
      });
      setModal(true);
      setResizable(false);
      getContentPane().setLayout(null);
      setTitle("Add Device");
      setBounds(400, 100, 624, 529);
      loadPosition(400, 100);
      devicesPage_ = devicesPage;
      core_ = core;
      listByLib_ = true;

      final JButton addButton = new JButton();
      addButton.addActionListener(new ActionListener() {

         public void actionPerformed(ActionEvent arg0) {
            if (addDevice()) {
               rebuildTable();
            }
         }
      });
      addButton.setText("Add");
      addButton.setBounds(490, 10, 93, 23);
      getContentPane().add(addButton);
      getRootPane().setDefaultButton(addButton);

      final JButton doneButton = new JButton();
      doneButton.addActionListener(new ActionListener() {

         public void actionPerformed(ActionEvent arg0) {
            savePosition();
            dispose();
         }
      });
      doneButton.setText("Done");
      doneButton.setBounds(490, 39, 93, 23);
      getContentPane().add(doneButton);
      getRootPane().setDefaultButton(doneButton);
      model_ = model;

      // put the URL for the documentation for the selected node into a browser
      // control
      documentationURLroot_ = "https://valelab.ucsf.edu/~MM/MMwiki/index.php/";
      final JButton documentationButton = new JButton();
      documentationButton.setText("Help");
      documentationButton.setBounds(490, 68, 93, 23);
      getContentPane().add(documentationButton);

      cbShowAll_ = new JCheckBox("Show all");
      cbShowAll_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            buildTree();
         }
      });
      cbShowAll_.setBounds(476, 462, 137, 23);
      cbShowAll_.setSelected(false); // showing only hubs by default
      getContentPane().add(cbShowAll_);
      documentationButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            displayDocumentation();
         }
      });

      if (listByLib_)
         buildTreeByLib(model);
      else
         buildTreeByType(model);

      JCheckBox cbLibrary_ = new JCheckBox("List by library");
      cbLibrary_.setSelected(listByLib_);
      cbLibrary_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            listByLib_ = ((JCheckBox) e.getSource()).isSelected();
            buildTree();
         }
      });
      cbLibrary_.setBounds(476, 436, 137, 23);
      getContentPane().add(cbLibrary_);

      availableScrollPane_ = new JScrollPane(theTree_);
      availableScrollPane_.setBounds(10, 10, 461, 480);
      availableScrollPane_.setViewportView(theTree_);
      getContentPane().add(availableScrollPane_);
   }

   private void buildTreeByType(MicroscopeModel model) {
      Device devices_[] = null;
      if (cbShowAll_.isSelected())
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

      DefaultMutableTreeNode root = new DefaultMutableTreeNode(
            "Devices supported by " + "\u00B5" + "Manager");

      DeviceTreeNode node = null;
      Object nodeNames[] = nodes.keySet().toArray();
      for (Object nodeName : nodeNames) {
         // create a new node of devices for this library
         node = new DeviceTreeNode((String) nodeName, false);
         root.add(node);
         Vector<Device> devs = nodes.get(nodeName);
         for (int i = 0; i < devs.size(); i++) {
            Object[] userObject = { devs.get(i).getLibrary(),
                  devs.get(i).getAdapterName(), devs.get(i).getDescription() };
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
      if (cbShowAll_.isSelected())
         devices_ = model.getAvailableDeviceList();
      else
         devices_ = model.getAvailableDevicesCompact();

      String thisLibrary = "";
      DefaultMutableTreeNode root = new DefaultMutableTreeNode(
            "Devices supported by " + "\u00B5" + "Manager");
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
         Object[] userObject = { devices_[idd].getLibrary(),
               devices_[idd].getAdapterName(), devices_[idd].getDescription() };
         DeviceTreeNode aLeaf = new DeviceTreeNode("", true);
         aLeaf.setUserObject(userObject);
         node.add(aLeaf);
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

   private void rebuildTable() {
      devicesPage_.rebuildDevicesTable();
   }

   private boolean addDevice() {
      int srows[] = theTree_.getSelectionRows();
      if (srows == null) {
         return false;
      }
      if (0 < srows.length) {
         if (0 < srows[0]) {
            DeviceTreeNode node = (DeviceTreeNode) theTree_
                  .getLastSelectedPathComponent();

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
                     }
                  }
               } 

            }
         }
      }
      return true;
   }

   public void valueChanged(TreeSelectionEvent event) {

      // update URL for library documentation
      int srows[] = theTree_.getSelectionRows();
      if (null != srows) {
         if (0 < srows.length) {
            if (0 < srows[0]) {
               DeviceTreeNode node = (DeviceTreeNode) theTree_
                     .getLastSelectedPathComponent();
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

   private void buildTree() {
      if (listByLib_)
         buildTreeByLib(model_);
      else
         buildTreeByType(model_);
      availableScrollPane_.setViewportView(theTree_);
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
