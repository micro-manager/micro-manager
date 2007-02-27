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

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

/**
 * Dialog to add a new device to the configuration.
 */
public class AddDeviceDlg extends JDialog {
   
   class Dev_TableModel extends AbstractTableModel {
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

   /**
    * Create the dialog
    */
   public AddDeviceDlg(MicroscopeModel model) {
      super();
      setModal(true);
      setResizable(false);
      getContentPane().setLayout(null);
      setTitle("Add Device");
      setBounds(100, 100, 596, 375);

      final JScrollPane scrollPane = new JScrollPane();
      scrollPane.setBounds(10, 10, 471, 321);
      getContentPane().add(scrollPane);

      final JButton okButton = new JButton();
      okButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            if (addDevice())
               dispose();
         }
      });
      okButton.setText("OK");
      okButton.setBounds(490, 10, 93, 23);
      getContentPane().add(okButton);

      final JButton cancelButton = new JButton();
      cancelButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            dispose();
         }
      });
      cancelButton.setText("Cancel");
      cancelButton.setBounds(490, 39, 93, 23);
      getContentPane().add(cancelButton);
      
      model_ = model;
      Dev_TableModel tm = new Dev_TableModel(model);
      
      devTable_= new JTable();
      devTable_.setModel(tm);
      devTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      scrollPane.setViewportView(devTable_);
            
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
      
      String name = new String("");
      boolean validName = false;
      while (!validName) {
         name = JOptionPane.showInputDialog("Please type in the new device name");
         if (name == null)
            return false;
         Device newDev = new Device(name, dev.getLibrary(), dev.getAdapterName(), dev.getDescription());
         try {
            model_.addDevice(newDev);
            validName = true;
         } catch (MMConfigFileException e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
         }
      }
      return true;
   }
}
