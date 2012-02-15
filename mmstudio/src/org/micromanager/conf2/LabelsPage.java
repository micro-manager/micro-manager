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
// CVS:          $Id: LabelsPage.java 7236 2011-05-17 18:52:12Z karlh $
//
package org.micromanager.conf2;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.prefs.Preferences;

import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;

import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * Wizard page to define labels for state devices.
 *
 */
public class LabelsPage extends PagePanel {
   private static final long serialVersionUID = 1L;
   private String labels_[] = new String[0];
   private Hashtable<String, String[]> originalLabels_ = new Hashtable<String, String[]>();
   ArrayList<Device> devices_ = new ArrayList<Device>();
   boolean originalLabelsStored_ = false;
   
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
            // first make sure that active edits are stored
            if (ltm.getColumnCount() > 0) {
               if (labelTable_.isEditing()) {
                  labelTable_.getDefaultEditor(String.class).stopCellEditing();
               }
            }
            // then switch to the new table
            String devName = (String) table.getValueAt(lsm.getMinSelectionIndex(), 0);
            ltm.setData(model_, devName);
         }
         ltm.fireTableStructureChanged();         
         labelTable_.getColumnModel().getColumn(0).setWidth(40);
      }
   }
   
   class LabelTableModel extends AbstractTableModel {
      private static final long serialVersionUID = 1L;
      public final String[] COLUMN_NAMES = new String[] {
            "State",
            "Label"
      };
      private Device curDevice_;

      public Device getCurrentDevice() {
         return curDevice_;
      }

      public void setData(MicroscopeModel model, String selDevice) {
         curDevice_ = model.findDevice(selDevice);
         String newLabels[] = new String[0];
         if (curDevice_ == null) {
            labels_ = newLabels;
            return;
         }
                  
         newLabels = new String[curDevice_.getNumberOfStates()];
         for (int i= 0; i<newLabels.length; i++)
            newLabels[i] = "State-" + i;
         
         for (int i=0; i<curDevice_.getNumberOfSetupLabels(); i++) {
            Label lab = curDevice_.getSetupLabel(i);
            newLabels[lab.state_] = lab.label_;
            if (labels_.length > lab.state_) {
               model_.updateLabelsInPreset(curDevice_.getName(), labels_[lab.state_], 
                       newLabels[lab.state_]);
            }
         }
         labels_ = newLabels;
      }
      
      public int getRowCount() {
         return labels_.length;
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
            return Integer.toString(rowIndex);
         else
            return labels_[rowIndex];
      }
      
      @Override
      public boolean isCellEditable(int nRow, int nCol) {
         if(nCol == 1)
            return true;
         else
            return false;
      }
      @Override
      public void setValueAt(Object value, int row, int col) {
         if (col == 1) {
            try {    
               String oldLabel = labels_[row];
               labels_[row] = (String) value;
               curDevice_.setSetupLabel(row, (String) value);
               fireTableCellUpdated(row, col);
               model_.updateLabelsInPreset(curDevice_.getName(), oldLabel, labels_[row]);
            } catch (Exception e) {
               ReportingUtils.showError(e);
            }
         }
      }
   }

   class DevTableModel extends AbstractTableModel {
      private static final long serialVersionUID = 1L;
      public final String[] COLUMN_NAMES = new String[] {
            "State devices"
      };
      
      public void setData(MicroscopeModel model) {
         // identify state devices
         Device devs[] = model.getDevices();
         devices_.clear();
         for (int i=0; i<devs.length; i++) {
            if (devs[i].isStateDevice()) {
               devices_.add(devs[i]);
            }
         }
         storeLabels();
      }
      
      public int getRowCount() {
         return devices_.size();
      }
      public int getColumnCount() {
         return COLUMN_NAMES.length;
      }
      @Override
      public String getColumnName(int columnIndex) {
         return COLUMN_NAMES[columnIndex];
      }
      public Object getValueAt(int rowIndex, int columnIndex) {
         return devices_.get(rowIndex).getName();
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
      "Select the device in the left-hand list and edit corresponding position labels in the right-hand list.\n\n" +
      "Use the 'Read' button to read label info from the hardware. This will override your changes!\n\n";
      setHelpFileName("conf_labels_page.html");
      prefs_ = prefs;
      setLayout(null);

      final JScrollPane labelsScrollPane = new JScrollPane();
      labelsScrollPane.setBounds(182, 30, 269, 482);
      add(labelsScrollPane);

      labelTable_ = new JTable();
      labelTable_.setModel(new LabelTableModel());
      labelTable_.setAutoCreateColumnsFromModel(false);
      labelTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      InputMap im = labelTable_.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      im.put( KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, 0 ), "none" );
      labelsScrollPane.setViewportView(labelTable_);
      GUIUtils.setClickCountToStartEditing(labelTable_,1);
      GUIUtils.stopEditingOnLosingFocus(labelTable_);

      final JScrollPane devScrollPane = new JScrollPane();
      devScrollPane.setBounds(10, 30, 162, 482);
      add(devScrollPane);

      devTable_ = new JTable();
      DevTableModel m = new DevTableModel();
      devTable_.setModel(m);
      devTable_.getSelectionModel().addListSelectionListener(new SelectionListener(devTable_));
      devTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      devScrollPane.setViewportView(devTable_);

      final JButton readButton = new JButton();
      readButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            // read labels from hardware
            readFromHardware();
         }
      });
      readButton.setText("Read");
      readButton.setBounds(457,30,93,23);
      add(readButton);

      final JButton resetButton = new JButton();
      resetButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            // read labels from hardware
            resetLabels();
         }
      });
      resetButton.setText("Reset");
      resetButton.setBounds(457,53,93,23);
      add(resetButton);  
   }


   public void readFromHardware() {
      LabelTableModel labelTableModel = (LabelTableModel) labelTable_.getModel();
      Device selectedDevice = labelTableModel.getCurrentDevice();
      if (selectedDevice != null) {
         try {
            selectedDevice.getSetupLabelsFromHardware(core_);
            labelTableModel.setData(model_, selectedDevice.getName());
            labelTableModel.fireTableStructureChanged();
         } catch (Exception e) {
            ReportingUtils.logError(e);
         }
      }
   }

   public void resetLabels() {
      LabelTableModel labelTableModel = (LabelTableModel) labelTable_.getModel();
      Device selectedDevice = labelTableModel.getCurrentDevice();
      if (selectedDevice != null) {
         for (int j=0; j<devices_.size(); j++) {
            if (selectedDevice == devices_.get(j)) {
               String orgLabs[] = originalLabels_.get(devices_.get(j).getName());
               if (orgLabs != null) {
                  for (int k=0; k<labels_.length; k++) {
                     selectedDevice.setSetupLabel(k, orgLabs[k]);
                     labels_[k] = new String(orgLabs[k]);
                  }
                  labelTableModel.fireTableStructureChanged();
               }
            }
         }
      }
   }

   public void storeLabels() {
      // Store the initial list of labels for the reset button
      // originalLabels_ = new String[devices_.size()][0];
      for (int j=0; j<devices_.size(); j++) {
         Device dev = devices_.get(j);
         if (!originalLabels_.containsKey(dev.getName())) {
            String labels[] = new String[dev.getNumberOfStates()];
            for (int i=0; i<dev.getNumberOfStates(); i++) {
               Label lab = dev.getSetupLabel(i);
               if (lab != null)
                  labels[i] = lab.label_;
               else
                  labels[i] = new String("State-" + i);
            }
            originalLabels_.put(dev.getName(), labels);
         }
      }
   }

   public boolean enterPage(boolean next) {
      DevTableModel tm = (DevTableModel)devTable_.getModel();
      tm.setData(model_);
      try {
         try{
         model_.loadStateLabelsFromHardware(core_);
         }catch(Throwable t){
            ReportingUtils.logError(t);}

         // default the selection to the first row
         if( devTable_.getSelectedRowCount() < 1 )
         {
            TableModel m2 = devTable_.getModel();
            if( 0 < m2.getRowCount())
               devTable_.setRowSelectionInterval(0, 0);
         }


      } catch (Exception e) {
         ReportingUtils.showError(e);
         return false;
      }

      return true;
  }

   public boolean exitPage(boolean next) {
      // define labels in hardware and synchronize device data with microscope model
      try {
         if (labelTable_.isEditing())
            labelTable_.getDefaultEditor(String.class).stopCellEditing();
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
