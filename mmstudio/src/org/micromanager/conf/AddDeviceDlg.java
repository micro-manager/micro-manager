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
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import org.micromanager.utils.ReportingUtils;

/**
 * Dialog to add a new device to the configuration.
 */
public class AddDeviceDlg extends JDialog implements MouseListener{
   private static final long serialVersionUID = 1L;

   class Dev_TableModel extends AbstractTableModel {
      private static final long serialVersionUID = 1L;

      Device devs_[];
      
      public final String[] COLUMN_NAMES = new String[] {
            "Library",
            "Adapter",
            "Description"
      };
      
      public Dev_TableModel(MicroscopeModel model) {
         devs_ = model.getAvailableDeviceList();
      }
      
      public int getRowCount() {
         return devs_.length;
      }
      public int getColumnCount() {
         return COLUMN_NAMES.length;
      }
      public String getColumnName(int columnIndex) {
         return COLUMN_NAMES[columnIndex];
      }
      public Object getValueAt(int rowIndex, int columnIndex) {
         if (columnIndex == 1) {
            return devs_[rowIndex].getAdapterName();
         } else if (columnIndex == 0) {
            return devs_[rowIndex].getLibrary();
         } else
            return devs_[rowIndex].getDescription();
      }
      
      public boolean isCellEditable(int nRow, int nCol) {
         return false;
      }
      
      public Device getDevice(int row) {
         if (row >= 0 && row <devs_.length)
            return devs_[row];
         
         return null;
      }
   }

   private JTable devTable_;
   private MicroscopeModel model_;
   private DevicesPage devicesPage_;

   /**
    * Create the dialog
    */
   public AddDeviceDlg(MicroscopeModel model, DevicesPage devicesPage) {
      super();
      setModal(true);
      setResizable(false);
      getContentPane().setLayout(null);
      setTitle("Add Device");
      setBounds(400, 100, 596, 529);
      devicesPage_ = devicesPage;

      final JScrollPane scrollPane = new JScrollPane();
      scrollPane.setBounds(10, 10, 471, 475);
      getContentPane().add(scrollPane);

      final JButton addButton = new JButton();
      addButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            if (addDevice())
               rebuildTable();
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
      Dev_TableModel tm = new Dev_TableModel(model);
      
      devTable_= new JTable();
      devTable_.setModel(tm);
      devTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      InputMap im = devTable_.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      im.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, 0 ), "none" );
      scrollPane.setViewportView(devTable_);
      devTable_.addMouseListener(this);
   }

   private void rebuildTable() {
      devicesPage_.rebuildTable();
   }

   public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() >= 2)
         if (addDevice())
            rebuildTable();
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
      int idx = devTable_.getSelectedRow();
      if (idx < 0) {
         JOptionPane.showMessageDialog(this, "No device is currently selected."); 
         return false;
      }
      
      Dev_TableModel tm = (Dev_TableModel)devTable_.getModel();
      Device dev = tm.getDevice(idx);
      if (dev == null)
         return false;
      
      String name = new String(dev.getAdapterName());
      boolean validName = false;
      while (!validName) {
         name = JOptionPane.showInputDialog("Please type in the new device name", name);
         if (name == null)
            return false;
         Device newDev = new Device(name, dev.getLibrary(), dev.getAdapterName(), dev.getDescription());
         try {
            model_.addDevice(newDev);
            validName = true;
         } catch (MMConfigFileException e) {
            ReportingUtils.showError(e);
         }
      }
      return true;
   }
}
