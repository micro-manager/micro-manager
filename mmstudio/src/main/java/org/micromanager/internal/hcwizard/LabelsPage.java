///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------
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
package org.micromanager.internal.hcwizard;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Hashtable;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import net.miginfocom.swing.MigLayout;
import org.micromanager.internal.utils.DaytimeNighttime;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ReportingUtils;

/** Wizard page to define labels for state devices. */
public final class LabelsPage extends PagePanel {
  private static final long serialVersionUID = 1L;
  private String labels_[] = new String[0];
  private Hashtable<String, String[]> originalLabels_ = new Hashtable<String, String[]>();
  ArrayList<Device> devices_ = new ArrayList<Device>();
  boolean originalLabelsStored_ = false;

  public final class SelectionListener implements ListSelectionListener {
    JTable table;

    // It is necessary to keep the table since it is not possible
    // to determine the table from the event's source
    SelectionListener(JTable table) {
      this.table = table;
    }

    public void valueChanged(ListSelectionEvent e) {
      if (e.getValueIsAdjusting()) return;

      ListSelectionModel lsm = (ListSelectionModel) e.getSource();
      LabelTableModel ltm = (LabelTableModel) labelTable_.getModel();

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
    public final String[] COLUMN_NAMES = new String[] {"State", "Label"};
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
      for (int i = 0; i < newLabels.length; i++) newLabels[i] = "State-" + i;

      Label sLabels[] = curDevice_.getAllSetupLabels();
      for (int i = 0; i < sLabels.length; i++) {
        newLabels[sLabels[i].state_] = sLabels[i].label_;
        if (labels_.length > sLabels[i].state_) {
          model_.updateLabelsInPreset(
              curDevice_.getName(), labels_[sLabels[i].state_], newLabels[sLabels[i].state_]);
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
      if (columnIndex == 0) return Integer.toString(rowIndex);
      else return labels_[rowIndex];
    }

    @Override
    public boolean isCellEditable(int nRow, int nCol) {
      if (nCol == 1) return true;
      else return false;
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
    public final String[] COLUMN_NAMES = new String[] {"State devices"};

    public void setData(MicroscopeModel model) {
      // identify state devices
      Device devs[] = model.getDevices();
      devices_.clear();
      for (int i = 0; i < devs.length; i++) {
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
  /** Create the panel */
  public LabelsPage() {
    super();
    title_ = "Define position labels for state devices";
    setLayout(new MigLayout("fill"));

    JTextArea help =
        createHelpText(
            "Some devices, such as filter wheels and objective turrets, have discrete positions that can have names assigned to them. For example, position 1 of a filter wheel could be the DAPI channel, position 2 the FITC channel, etc. Assign names to positions here.");
    add(help, "spanx, growx, wrap");
    final JScrollPane devScrollPane = new JScrollPane();
    add(devScrollPane, "growy, width 200!");

    devTable_ = new DaytimeNighttime.Table();
    DevTableModel m = new DevTableModel();
    devTable_.setModel(m);
    devTable_.getSelectionModel().addListSelectionListener(new SelectionListener(devTable_));
    devTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    devScrollPane.setViewportView(devTable_);

    final JScrollPane labelsScrollPane = new JScrollPane();
    add(labelsScrollPane, "growy, width 400!");

    labelTable_ = new DaytimeNighttime.Table();
    labelTable_.setModel(new LabelTableModel());
    labelTable_.setAutoCreateColumnsFromModel(false);
    labelTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    InputMap im = labelTable_.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "none");
    labelsScrollPane.setViewportView(labelTable_);
    GUIUtils.setClickCountToStartEditing(labelTable_, 1);
    GUIUtils.stopEditingOnLosingFocus(labelTable_);

    final JButton readButton = new JButton("Read");
    readButton.setToolTipText("When possible, reads position names from the device adapter.");
    readButton.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent arg0) {
            readFromHardware();
          }
        });
    add(readButton, "split, flowy, aligny top");

    final JButton resetButton = new JButton("Reset");
    resetButton.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent arg0) {
            resetLabels();
          }
        });
    add(resetButton, "wrap");
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
      for (int j = 0; j < devices_.size(); j++) {
        if (selectedDevice == devices_.get(j)) {
          String orgLabs[] = originalLabels_.get(devices_.get(j).getName());
          if (orgLabs != null) {
            for (int k = 0; k < labels_.length; k++) {
              selectedDevice.setSetupLabel(k, orgLabs[k]);
              labels_[k] = orgLabs[k];
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
    for (int j = 0; j < devices_.size(); j++) {
      Device dev = devices_.get(j);
      if (!originalLabels_.containsKey(dev.getName())) {
        String labels[] = new String[dev.getNumberOfStates()];
        for (int i = 0; i < dev.getNumberOfStates(); i++) {
          Label lab = dev.getSetupLabelByState(i);
          if (lab != null) labels[i] = lab.label_;
          else labels[i] = "State-" + i;
        }
        originalLabels_.put(dev.getName(), labels);
      }
    }
  }

  public boolean enterPage(boolean next) {
    DevTableModel tm = (DevTableModel) devTable_.getModel();
    tm.setData(model_);
    try {
      try {
        model_.loadStateLabelsFromHardware(core_);
      } catch (Throwable t) {
        ReportingUtils.logError(t);
      }

      // default the selection to the first row
      if (devTable_.getSelectedRowCount() < 1) {
        TableModel m2 = devTable_.getModel();
        if (0 < m2.getRowCount()) devTable_.setRowSelectionInterval(0, 0);
      }

    } catch (Exception e) {
      ReportingUtils.showError(e);
      return false;
    }

    return true;
  }

  public boolean exitPage(boolean toNextPage) {
    // define labels in hardware and synchronize device data with microscope model
    try {
      if (labelTable_.isEditing()) labelTable_.getDefaultEditor(String.class).stopCellEditing();
      model_.applySetupLabelsToHardware(core_);
      model_.loadDeviceDataFromHardware(core_);
    } catch (Exception e) {
      handleError(e.getMessage());

      // prevent from going to the next page if there is an error
      if (toNextPage) return false;
      else return true; // allow going back
    }
    return true;
  }

  public void refresh() {}

  public void loadSettings() {}

  public void saveSettings() {}
}
