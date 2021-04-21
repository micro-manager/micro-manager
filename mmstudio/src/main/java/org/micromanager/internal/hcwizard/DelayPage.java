///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, December 2, 2006
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
// CVS:          $Id: DelayPage.java 3761 2010-01-14 02:38:08Z arthur $
//
package org.micromanager.internal.hcwizard;

import net.miginfocom.swing.MigLayout;
import org.micromanager.internal.hcwizard.DevicesPage.DeviceTable_TableModel;
import org.micromanager.internal.utils.DaytimeNighttime;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ReportingUtils;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;

/** Wizard page to set device delays. */
public final class DelayPage extends PagePanel {
  private static final long serialVersionUID = 1L;

  class DelayTableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;

    public final String[] COLUMN_NAMES = new String[] {"Name", "Adapter", "Delay [ms]"};

    MicroscopeModel model_;
    ArrayList<Device> devices_;

    public DelayTableModel(MicroscopeModel model) {
      devices_ = new ArrayList<Device>();
      Device allDevices[] = model.getDevices();
      for (int i = 0; i < allDevices.length; i++) {
        if (allDevices[i].usesDelay()) devices_.add(allDevices[i]);
      }
      model_ = model;
    }

    public void setMicroscopeModel(MicroscopeModel mod) {
      Device allDevices[] = mod.getDevices();
      for (int i = 0; i < allDevices.length; i++) {
        if (allDevices[i].usesDelay()) devices_.add(allDevices[i]);
      }
      model_ = mod;
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
      if (columnIndex == 0) return devices_.get(rowIndex).getName();
      else if (columnIndex == 1) return devices_.get(rowIndex).getAdapterName();
      else return new Double(devices_.get(rowIndex).getDelay());
    }

    public Device getDevice(int rowIndex) {
      return devices_.get(rowIndex);
    }

    public void setValueAt(Object value, int row, int col) {
      if (col == 2) {
        try {
          devices_.get(row).setDelay(Double.parseDouble((String) value));
          fireTableCellUpdated(row, col);
        } catch (Exception e) {
          ReportingUtils.logError(e);
        }
      }
    }

    public boolean isCellEditable(int nRow, int nCol) {
      if (nCol == 2) return true;
      else return false;
    }

    public void refresh() {
      Device allDevices[] = model_.getDevices();
      for (int i = 0; i < allDevices.length; i++) {
        if (allDevices[i].usesDelay()) devices_.add(allDevices[i]);
      }
      this.fireTableDataChanged();
    }
  }

  private JTable deviceTable_;
  /** Create the panel */
  public DelayPage() {
    super();
    title_ = "Set delays for devices without synchronization capabilities";
    setLayout(new MigLayout("fill"));

    JTextArea help =
        createHelpText(
            "Set how long to wait for the device to act before \u00b5Manager will move on (for example, waiting for a shutter to open before an image is snapped). Many devices will determine this automatically; refer to the help for more information.");
    add(help, "spanx, growx, wrap");
    final JScrollPane scrollPane = new JScrollPane();
    add(scrollPane, "grow");

    deviceTable_ = new DaytimeNighttime.Table();
    deviceTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    InputMap im = deviceTable_.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "none");
    scrollPane.setViewportView(deviceTable_);
    GUIUtils.setClickCountToStartEditing(deviceTable_, 1);
    GUIUtils.stopEditingOnLosingFocus(deviceTable_);

    JButton helpButton = new JButton("Help");
    helpButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            DelayTableModel model = (DelayTableModel) deviceTable_.getModel();
            String library = model.getDevice(deviceTable_.getSelectedRow()).getLibrary();
            try {
              ij.plugin.BrowserLauncher.openURL(DevicesPage.WEBSITE_ROOT + library);
            } catch (IOException e1) {
              ReportingUtils.showError(e1);
            }
          }
        });
    add(helpButton, "aligny top, wrap");
  }

  public boolean enterPage(boolean next) {
    rebuildTable();
    return true;
  }

  public boolean exitPage(boolean next) {
    CellEditor ce = deviceTable_.getCellEditor();
    if (ce != null) {
      deviceTable_.getCellEditor().stopCellEditing();
    }
    // apply delays to hardware
    try {
      model_.applyDelaysToHardware(core_);
    } catch (Exception e) {
      ReportingUtils.logError(e);
      if (next) return false; // refuse to go to the next page
    }
    return true;
  }

  private void rebuildTable() {
    TableModel tm = deviceTable_.getModel();
    DelayTableModel tmd;
    if (tm instanceof DeviceTable_TableModel) {
      tmd = (DelayTableModel) deviceTable_.getModel();
      tmd.refresh();
    } else {
      tmd = new DelayTableModel(model_);
      deviceTable_.setModel(tmd);
    }
    tmd.fireTableStructureChanged();
    tmd.fireTableDataChanged();
  }

  public void refresh() {
    rebuildTable();
  }

  public void loadSettings() {}

  public void saveSettings() {}
}
