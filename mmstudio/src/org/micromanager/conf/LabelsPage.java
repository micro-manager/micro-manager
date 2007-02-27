///////////////////////////////////////////////////////////////////////////////
//FILE:          LabelsPage.java
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
// CVS:          $Id$
//
package org.micromanager.conf;

import java.util.ArrayList;
import java.util.prefs.Preferences;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import mmcorej.MMCoreJ;

/**
 * Wizard page to define labels for state devices.
 *
 */
public class LabelsPage extends PagePanel {
   
   private String labels_[] = new String[0];
   
   public class SelectionListener implements ListSelectionListener {
      JTable table;
  
      // It is necessary to keep the table since it is not possible
      // to determine the table from the event's source
      SelectionListener(JTable table) {
          this.table = table;
      }
      public void valueChanged(ListSelectionEvent e) {
         if (e.getValueIsAdjusting())
            return;
         
         ListSelectionModel lsm = (ListSelectionModel)e.getSource();
         LabelTableModel ltm = (LabelTableModel)labelTable_.getModel();
         if (lsm.isSelectionEmpty()) {
            ltm.setData(model_, null);
         } else {
            String devName = (String)table.getValueAt(lsm.getMinSelectionIndex(), 0);
            ltm.setData(model_, devName);
         }
         ltm.fireTableStructureChanged();         
         labelTable_.getColumnModel().getColumn(0).setWidth(40);
      }
   }
   
   class LabelTableModel extends AbstractTableModel {
      public final String[] COLUMN_NAMES = new String[] {
            "Position",
            "Label"
      };
      private Device curDevice_;
      
      public void setData(MicroscopeModel model, String selDevice) {
         curDevice_ = model.findDevice(selDevice);
         labels_ = new String[0];
         if (curDevice_ == null) {
            return;
         }
         
         Property p = curDevice_.findProperty(MMCoreJ.getG_Keyword_Label());
         if (p == null)
            return;
         
         labels_ = new String[curDevice_.getNumberOfStates()];
         for (int i= 0; i<labels_.length; i++)
            labels_[i] = new String("State-" + i);
         
         for (int i=0; i<curDevice_.getNumberOfSetupLabels(); i++) {
            Label lab = curDevice_.getSetupLabel(i);
            labels_[lab.state_] = lab.label_;
         }
      }
      
      public int getRowCount() {
         return labels_.length;
      }
      public int getColumnCount() {
         return COLUMN_NAMES.length;
      }
      public String getColumnName(int columnIndex) {
         return COLUMN_NAMES[columnIndex];
      }
      public Object getValueAt(int rowIndex, int columnIndex) {
         if (columnIndex == 0)
            return Integer.toString(rowIndex);
         else
            return labels_[rowIndex];
      }
      
      public boolean isCellEditable(int nRow, int nCol) {
         if(nCol == 1)
            return true;
         else
            return false;
      }
      public void setValueAt(Object value, int row, int col) {
         if (col == 1) {
            try {
               labels_[row] = (String) value;
               curDevice_.setSetupLabel(row, (String) value);
               System.out.println("Label set:" + curDevice_.getName() + ", " + row + ", " + labels_[row]);
               fireTableCellUpdated(row, col);
            } catch (Exception e) {
               handleError(e.getMessage());
            }
         }
      }
   }

   class DevTableModel extends AbstractTableModel {
      ArrayList devices_ = new ArrayList();
      public final String[] COLUMN_NAMES = new String[] {
            "State devices"
      };
      
      public void setData(MicroscopeModel model) {
         Device devs[] = model.getDevices();
         devices_.clear();
         for (int i=0; i<devs.length; i++) {
            if (devs[i].isStateDevice()) {
               devices_.add(devs[i]);
            }
         }
      }
      
      public int getRowCount() {
         return devices_.size();
      }
      public int getColumnCount() {
         return COLUMN_NAMES.length;
      }
      public String getColumnName(int columnIndex) {
         return COLUMN_NAMES[columnIndex];
      }
      public Object getValueAt(int rowIndex, int columnIndex) {
         return ((Device)devices_.get(rowIndex)).getName();
      }
   }

   private JTable devTable_;
   private JTable labelTable_;
   /**
    * Create the panel
    */
   public LabelsPage(Preferences prefs) {
      super();
      title_ = "Define position labels for state devices";
      helpText_ = "State devices with discrete positions, such as filter changers or objective turrets, etc. can have mnemonic labels assigned for each position.\n\n" +
      "Select the device in the left-hand list and edit corresponding position labels in the right-hand list.";
      setHelpFileName("conf_labels_page.html");
      prefs_ = prefs;
      setLayout(null);

      final JScrollPane scrollPane_1 = new JScrollPane();
      scrollPane_1.setBounds(186, 10, 304, 254);
      add(scrollPane_1);

      labelTable_ = new JTable();
      labelTable_.setModel(new LabelTableModel());
      scrollPane_1.setViewportView(labelTable_);

      final JScrollPane scrollPane = new JScrollPane();
      scrollPane.setBounds(10, 10, 162, 255);
      add(scrollPane);

      devTable_ = new JTable();
      DevTableModel m = new DevTableModel();
      devTable_.setModel(m);
      devTable_.getSelectionModel().addListSelectionListener(new SelectionListener(devTable_));
      devTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      scrollPane.setViewportView(devTable_);
      //
   }

   public boolean enterPage(boolean next) {
      DevTableModel tm = (DevTableModel)devTable_.getModel();
      tm.setData(model_);
      return true;
  }

   public boolean exitPage(boolean next) {
      // define labels in hardware and syhcronize device data with microscope model
      try {
         model_.applySetupLabelsToHardware(core_);
         model_.loadDeviceDataFromHardware(core_);
      } catch (Exception e) {
         handleError(e.getMessage());
         return false;
      }
      return true;
   }
   
   public void refresh() {
   }

   public void loadSettings() {
   }

   public void saveSettings() {
   }
}
