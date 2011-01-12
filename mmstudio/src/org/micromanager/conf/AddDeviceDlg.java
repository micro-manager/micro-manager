///////////////////////////////////////////////////////////////////////////////
//FILE:          AddDeviceDlg.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, November 07, 2006
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
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import org.micromanager.utils.ReportingUtils;

/**
 * Dialog to add a new device to the configuration.
 */
public class AddDeviceDlg extends JDialog implements MouseListener, TreeSelectionListener {

    private static final long serialVersionUID = 1L;

    class TreeNodeShowsDeviceAndDescription extends DefaultMutableTreeNode {

        public TreeNodeShowsDeviceAndDescription(String value) {
            super(value);
        }

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
        // if user clicks on a container node, just return a null array instead of the user data
        public Object[] getUserDataArray(){
            Object[] ret = null;
            
            Object uo = getUserObject();
            if( null!=uo){
                if( uo.getClass().isArray()){
                    // retrieve the device info tuple
                    Object[] userData = (Object[])uo;
                    if( 1< userData.length )
                        ret = userData;
                }
                
            } 
            return ret;
        }
    }

    private MicroscopeModel model_;
    private DevicesPage devicesPage_;
    private JTree theTree_;


    /**
     * Create the dialog
     */
    public AddDeviceDlg(MicroscopeModel model, DevicesPage devicesPage) {
        super();
        setModal(true);
        setResizable(false);
        getContentPane().setLayout(null);
        setTitle("Add Device - Open a library to see available devices & descriptions");
        setBounds(400, 100, 596, 529);
        devicesPage_ = devicesPage;

        Device allDevs_[] = model.getAvailableDeviceList();
        String thisLibrary = "";
        TreeNodeShowsDeviceAndDescription root = new TreeNodeShowsDeviceAndDescription("");
        TreeNodeShowsDeviceAndDescription node = null;
        for (int idd = 0; idd < allDevs_.length; ++idd) {
            // assume that the first library doesn't have an empty name! (of course!)
            if (0 != thisLibrary.compareTo(allDevs_[idd].getLibrary())) {
                // create a new node of devices for this library
                node = new TreeNodeShowsDeviceAndDescription(allDevs_[idd].getLibrary());
                root.add(node);
                thisLibrary = allDevs_[idd].getLibrary(); // remember which library we are processing
            }
            TreeNodeShowsDeviceAndDescription aLeaf = new TreeNodeShowsDeviceAndDescription(allDevs_[idd].getAdapterName() + " | " + allDevs_[idd].getDescription());
            Object[] userObject = {allDevs_[idd].getLibrary(), allDevs_[idd].getAdapterName(), allDevs_[idd].getDescription()};
            aLeaf.setUserObject(userObject);
            node.add(aLeaf);
        }
        // try building a tree
        theTree_ = new JTree(root);
        theTree_.addTreeSelectionListener(this);
        // for debugging the tree selection:

        //currentSelectDbg_ = new JTextField("Current Selection: NONE");
        //getContentPane().add(currentSelectDbg_, BorderLayout.NORTH);

        final JScrollPane scrollPane = new JScrollPane(theTree_);
        scrollPane.setBounds(10, 10, 471, 475);
        getContentPane().add(scrollPane);

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
        model_ = model;
        //dtm_ = new Dev_TableModel(model);

        /*
        devTable_ = new JTable();
        devTable_.setModel(tm);
        devTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        InputMap im = devTable_.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "none");
        scrollPane.setViewportView(devTable_);
        devTable_.addMouseListener(this);
         */
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
        if (srows == null)
           return false;
        if (0 < srows.length) {
            if (0 < srows[0]) {
                TreeNodeShowsDeviceAndDescription node = (TreeNodeShowsDeviceAndDescription) theTree_.getLastSelectedPathComponent();

                Object[] userData = node.getUserDataArray();
                if( null == userData)
                    return false;
                boolean validName = false;
                while (!validName) {
                    String name = JOptionPane.showInputDialog("Please type in the new device name", userData[1].toString());
                    if (name == null) {
                        return false;
                    }
                    Device newDev = new Device(name, userData[0].toString(), userData[1].toString(), userData[2].toString());
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
        //currentSelectDbg_.setText("Current Selection: "
         //       + theTree_.getLastSelectedPathComponent().toString());

        if(addDevice())
            rebuildTable();
    }
}
