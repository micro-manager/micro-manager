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
// CVS:          $Id$
package org.micromanager.conf;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;

import org.micromanager.utils.MMDialog;
import org.micromanager.utils.ReportingUtils;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Dialog to add a new device to the configuration.
 */
public class AddDeviceDlg extends MMDialog implements MouseListener,
      TreeSelectionListener {

   private static final long serialVersionUID = 1L;

   class TreeWContextMenu extends JTree implements ActionListener {
      private static final long serialVersionUID = 1L;

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

      JPopupMenu popupMenu_;
      AddDeviceDlg d_; // pretty ugly

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
   
   class TreeNodeShowsDeviceAndDescription extends DefaultMutableTreeNode {
      private static final long serialVersionUID = 1L;

      public TreeNodeShowsDeviceAndDescription(String value) {
         super(value);
      }

      @Override
      public String toString() {
         String ret = "";
         Object uo = getUserObject();
         if (null != uo) {
            if (uo.getClass().isArray()) {
               Object[] userData = (Object[]) uo;
               if (2 < userData.length) {
                  ret = userData[1].toString() + " | " + userData[2].toString();
               }
            } else {
               ret = uo.toString();
            }
         }
         return ret;
      }

      // if user clicks on a container node, just return a null array instead of
      // the user data

      public Object[] getUserDataArray() {
         Object[] ret = null;

         Object uo = getUserObject();
         if (null != uo) {
            if (uo.getClass().isArray()) {
               // retrieve the device info tuple
               Object[] userData = (Object[]) uo;
               if (1 < userData.length) {
                  ret = userData;
               }
            }

         }
         return ret;
      }
   }

   private MicroscopeModel model_;
   private DevicesPage devicesPage_;
   private TreeWContextMenu theTree_;
   final String documentationURLroot_;
   String libraryDocumentationName_;
   private JCheckBox cbShowAll_;
   private JScrollPane scrollPane_;

   /**
    * Create the dialog
    */
   public AddDeviceDlg(MicroscopeModel model, DevicesPage devicesPage) {
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
      setBounds(400, 100, 596, 529);
      loadPosition(400, 100);
      devicesPage_ = devicesPage;

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
            buildTree(model_);
            scrollPane_.setViewportView(theTree_);
         }
      });
      cbShowAll_.setBounds(487, 462, 81, 23);
      cbShowAll_.setSelected(false); // showing only hubs by default
      getContentPane().add(cbShowAll_);
      documentationButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            displayDocumentation();
         }
      });
      
      buildTree(model);
      scrollPane_ = new JScrollPane(theTree_);
      scrollPane_.setBounds(10, 10, 471, 475);
      getContentPane().add(scrollPane_);

   }
   
   private void buildTree(MicroscopeModel model) {
      Device devices_[] = null;
      if (cbShowAll_.isSelected())
         devices_ = model.getAvailableDeviceList();
      else
         devices_ = model.getAvailableDevicesCompact();
      
      String thisLibrary = "";
      DefaultMutableTreeNode root = new DefaultMutableTreeNode(
            "Devices supported by " + "\u00B5" + "Manager");
      TreeNodeShowsDeviceAndDescription node = null;
      for (int idd = 0; idd < devices_.length; ++idd) {
         // assume that the first library doesn't have an empty name! (of
         // course!)
         if (0 != thisLibrary.compareTo(devices_[idd].getLibrary())) {
            // create a new node of devices for this library
            node = new TreeNodeShowsDeviceAndDescription(
                  devices_[idd].getLibrary());
            root.add(node);
            thisLibrary = devices_[idd].getLibrary(); // remember which library
                                                      // we are processing
         }
         Object[] userObject = { devices_[idd].getLibrary(),
               devices_[idd].getAdapterName(), devices_[idd].getDescription() };
         TreeNodeShowsDeviceAndDescription aLeaf = new TreeNodeShowsDeviceAndDescription(
               "");
         aLeaf.setUserObject(userObject);
         node.add(aLeaf);
      }
      // try building a tree
      theTree_ = new TreeWContextMenu(root, this);
      theTree_.addTreeSelectionListener(this);
      
      MouseListener ml = new TreeMouseListener() ;
      
      theTree_.addMouseListener(ml);
      theTree_.setRootVisible(false);
      theTree_.setShowsRootHandles(true);
   }


   private void displayDocumentation() {
      try {
         ij.plugin.BrowserLauncher.openURL(documentationURLroot_
               + libraryDocumentationName_);
      } catch (IOException e1) {
         ReportingUtils.showError(e1);
      }
   }

   private void rebuildTable() {
      devicesPage_.rebuildTable();
   }

   public void mouseClicked(MouseEvent e) {
   }

   public void mousePressed(MouseEvent e) {
   }

   public void mouseReleased(MouseEvent e) {
   }

   public void mouseEntered(MouseEvent e) {
   }

   public void mouseExited(MouseEvent e) {
   }

   protected boolean addDevice() {
      int srows[] = theTree_.getSelectionRows();
      if (srows == null) {
         return false;
      }
      if (0 < srows.length) {
         if (0 < srows[0]) {
            TreeNodeShowsDeviceAndDescription node = (TreeNodeShowsDeviceAndDescription) theTree_
                  .getLastSelectedPathComponent();

            Object[] userData = node.getUserDataArray();
            if (null == userData) {
               // if a folder has one child go ahead and add the childer
               if (1 == node.getLeafCount()) {
                  node = (TreeNodeShowsDeviceAndDescription) node.getChildAt(0);
                  userData = node.getUserDataArray();
                  if (null == userData)
                     return false;
               }
            }
            boolean validName = false;
            while (!validName) {
               String name = JOptionPane.showInputDialog(
                     "Please type in the new device name",
                     userData[1].toString());
               if (name == null) {
                  return false;
               }
               Device newDev = new Device(name, userData[0].toString(),
                     userData[1].toString(), userData[2].toString());
               try {
                  model_.addDevice(newDev);
                  validName = true;
               } catch (MMConfigFileException e) {
                  ReportingUtils.showError(e);
                  return false;
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
               TreeNodeShowsDeviceAndDescription node = (TreeNodeShowsDeviceAndDescription) theTree_
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
   
}
