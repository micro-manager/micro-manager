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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
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
import javax.swing.table.TableModel;

import mmcorej.MMCoreJ;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.PropertyItem;
import org.micromanager.utils.ReportingUtils;

/**
 * Wizard page to define labels for state devices.
 *
 */
public class LabelsPage extends PagePanel {
   private static final long serialVersionUID = 1L;
   private boolean initialized_ = false;
   private String labels_[] = new String[0];
   private String deviceLabels_[][] = new String[0][0];
   ArrayList<Device> devices_ = new ArrayList<Device>();
   
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
         labels_ = new String[0];
         if (curDevice_ == null) {
            return;
         }
         
         PropertyItem p = curDevice_.findProperty(MMCoreJ.getG_Keyword_Label());
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
               labels_[row] = (String) value;
               curDevice_.setSetupLabel(row, (String) value);
               fireTableCellUpdated(row, col);
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
         if (!initialized_) {
            storeLabels();
            initialized_ = true;
         }
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
      labelsScrollPane.setBounds(186, 10, 269, 254);
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
      devScrollPane.setBounds(10, 10, 162, 255);
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
      readButton.setBounds(469,10,93,23);
      add(readButton);

      final JButton resetButton = new JButton();
      resetButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            // read labels from hardware
            resetLabels();
         }
      });
      resetButton.setText("Reset");
      resetButton.setBounds(469,43,93,23);
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
               labels_ = deviceLabels_[j];
               for (int k=0; k<labels_.length; k++)
                  selectedDevice.setSetupLabel(k, labels_[k]);
               labelTableModel.fireTableStructureChanged();
            }
         }
      }
   }

   public void storeLabels() {
      // Store the initial list of labels for the reset button
      String labels[] = new String[0];
      Device devs[] = model_.getDevices();
      devices_.clear();
      for (int i=0; i<devs.length; i++) {
         if (devs[i].isStateDevice()) {
            devices_.add(devs[i]);
         }
      }
      deviceLabels_ = new String[devices_.size()][0];
      for (int j=0; j<devices_.size(); j++) {
         labels = new String[devices_.get(j).getNumberOfStates()];
         for (int i= 0; i<labels.length; i++)
            labels[i] = new String("State-" + i);
         
         for (int i=0; i<devices_.get(j).getNumberOfSetupLabels(); i++) {
            Label lab = devices_.get(j).getSetupLabel(i);
            labels[lab.state_] = lab.label_;
         }
         deviceLabels_[j] = new String[labels.length];
         for (int k=0; k<labels.length; k++)
            deviceLabels_[j][k]=labels[k];
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

      resetLabels();
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
