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

import java.awt.Container;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Vector;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import mmcorej.CMMCore;
import mmcorej.MMCoreJ;

import org.micromanager.utils.ReportingUtils;

/**
 * Wizard page to add or remove devices.
 */
public class DevicesPage extends PagePanel implements ListSelectionListener {
   private static final long serialVersionUID = 1L;

   class DeviceTable_TableModel extends AbstractTableModel {
      private static final long serialVersionUID = 1L;

      public final String[] COLUMN_NAMES = new String[] {
            "Name",
            "Adapter/Library",
            "Description"
      };
      
      MicroscopeModel model_;
      Device devices_[];

      public DeviceTable_TableModel(MicroscopeModel model) {
         setMicroscopeModel(model);
      }
      
      public void setMicroscopeModel(MicroscopeModel mod) {
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
         else
            return devices_[rowIndex].getDescription();
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

   private JTable deviceTable_;
   private JScrollPane scrollPane_;
   private static final String HELP_FILE_NAME = "conf_devices_page.html";
   private JButton editButton;
   private JButton removeButton;
   private JButton peripheralsButton;
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
      
      setLayout(null);
      prefs_ = prefs;
      setHelpFileName(HELP_FILE_NAME);

      scrollPane_ = new JScrollPane();
      scrollPane_.setBounds(10, 10, 453, 262);
      add(scrollPane_);

      deviceTable_ = new JTable();
      deviceTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      scrollPane_.setViewportView(deviceTable_);
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
      addButton.setBounds(469, 41, 99, 23);
      add(addButton);

      removeButton = new JButton();
      removeButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            removeDevice();
         }
      });
      removeButton.setText("Remove");
      removeButton.setBounds(469, 119, 99, 23);
      add(removeButton);
      removeButton.setEnabled(false);
      
      editButton = new JButton("Edit...");
      editButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            editDevice();
         }
      });
      editButton.setBounds(469, 68, 99, 23);
      add(editButton);
      editButton.setEnabled(false);
      
      peripheralsButton = new JButton("Peripherals...");
      peripheralsButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            editPeripherals();
         }
      });
      peripheralsButton.setEnabled(false);
      peripheralsButton.setBounds(469, 93, 99, 23);
      add(peripheralsButton);
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
            if (model_.findDevice(installed[i]) == null) {
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
         rebuildTable();
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
      
      if (!dev.isInitialized()) {
         // user canceled or things did not work out
         model_.removeDevice(dev.getName());
         try {
            core_.unloadDevice(dev.getName());
         } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
         }
      }
      rebuildTable();
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
      rebuildTable();
   }
   
   protected void addDevice() {
      AddDeviceDlg dlg = new AddDeviceDlg(model_, core_, this);
      dlg.setVisible(true);
      rebuildTable();
   }
   
   public void rebuildTable() {
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
      rebuildTable();
   }
   
	public boolean enterPage(boolean fromNextPage) {
		try {
         // double check that list of device libraries is valid before continuing.
         CMMCore.getDeviceLibraries();
			model_.removeDuplicateComPorts();
			rebuildTable();
			if (fromNextPage) {
			   // do nothing for now
			} else {
			   model_.loadModel(core_);
			   model_.initializeModel(core_);
			}
			return true;
		} catch (Exception e2) {
			ReportingUtils.showError(e2);
		}
		return false;
	}

    public boolean exitPage(boolean toNextPage) {
        boolean status = true;
        if (toNextPage) {
            Container ancestor = getTopLevelAncestor();
            Cursor oldc = null;
            if (null != ancestor){
               oldc = ancestor.getCursor();
               Cursor waitc = new Cursor(Cursor.WAIT_CURSOR);
               ancestor.setCursor(waitc);
            }
            
            // NOTE: this is not needed anymore bacause devices are already loaded
            // Maybe we just need loadDeviceDataFromHardware?
            //status = model_.loadModel(core_, true);
            if (null != ancestor){
               if( null != oldc)
                  ancestor.setCursor(oldc);
            }
        }
        return status;
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
}
