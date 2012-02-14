///////////////////////////////////////////////////////////////////////////////
//FILE:          CalibrationListDlg.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

//AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu, June 2008
//              Based on PositionListDlg.java by Nenad Amodaj

//COPYRIGHT:    University of California, San Francisco, 2008

//LICENSE:      This file is distributed under the BSD license.
//License text is included with the source distribution.

//This file is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty
//of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

//IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.


package org.micromanager;

import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpringLayout;
import javax.swing.table.AbstractTableModel;

import mmcorej.CMMCore;
import mmcorej.Configuration;

import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.Calibration;
import org.micromanager.utils.CalibrationList;
import org.micromanager.utils.MMDialog;
import org.micromanager.utils.ReportingUtils;

public class CalibrationListDlg extends MMDialog {
   private static final long serialVersionUID = 1L;
   private static final String TITLE = "Calibration Editor";
   public static final String PIXEL_SIZE_GROUP = "ConfigPixelSize";

   private JTable calTable_;
   private SpringLayout springLayout;
   private CMMCore core_;
   //private MMOptions opts_;
   private Preferences prefs_;
   //private GUIColors guiColors_;
   private CalibrationList calibrationList_;
   private ScriptInterface parentGUI_;

   private class CalTableModel extends AbstractTableModel {
      private static final long serialVersionUID = 1L;
      public final String[] COLUMN_NAMES = new String[] {
            "Label",
            "Pixel Size [um]"
      };
      private CalibrationList calibrationList;

      public void setData(CalibrationList cl) {
         calibrationList = cl;
      }

      public int getRowCount() {
         return calibrationList.size();
      }
      
      public int getColumnCount() {
         return COLUMN_NAMES.length;
      }

      public String getColumnName(int columnIndex) {
         return COLUMN_NAMES[columnIndex];
      }

      public Object getValueAt(int rowIndex, int columnIndex) {
         Calibration cal = calibrationList.get(rowIndex);
         if (columnIndex == 0) {
            return cal.getLabel();
         } else if (columnIndex == 1) {
            return cal.getPixelSizeUm().toString();
         } else
            return null;
      }

      public void setValueAt(Object value, int rowIndex, int columnIndex) {
         Calibration cal = calibrationList.get(rowIndex);
         if (columnIndex == 1) {
            try {
               double val = Double.parseDouble((String)value.toString());
               core_.setPixelSizeUm(cal.getLabel(), val);
               cal.setPixelSizeUm(val);
               parentGUI_.setConfigChanged(true);
               parentGUI_.refreshGUI();
            } catch (Exception e) {                                                              
               handleException(e);                                                               
            }  
         }
      }

      public int getCurrentPixelConfigRow() {
         try {
            String curConfig = core_.getCurrentPixelSizeConfig();
            for (int i = 0; i < this.getRowCount(); ++i) {
               if (this.getValueAt(i, 0).equals(curConfig)) {
                  return i;
               }
            }
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
         return -1;
      }

      public boolean isCellEditable(int rowIndex, int columnIndex) {
         if (columnIndex == 1)
            return true;
         else
            return false;
      }

      private void handleException (Exception e) {
         String errText = "Exception occurred: " + e.getMessage();
         JOptionPane.showMessageDialog(null, errText);
      }
   }


   /**
    * Create the dialog
    */
   public CalibrationListDlg(CMMCore core) {
      super();
      addWindowListener(new WindowAdapter() {
         public void windowClosing(WindowEvent arg0) {
            savePosition();
         }
      });
      core_ = core;
      //opts_ = opts;
      //guiColors_ = new GUIColors();
      setTitle("Pixel Size calibration");
      springLayout = new SpringLayout();
      getContentPane().setLayout(springLayout);
      setBounds(100, 100, 362, 495);

      Preferences root = Preferences.userNodeForPackage(this.getClass());
      prefs_ = root.node(root.absolutePath() + "/CalibrationListDlg");
      setPrefsNode(prefs_);

      Rectangle r = getBounds();
      loadPosition(r.x, r.y, r.width, r.height);

      final JScrollPane scrollPane = new JScrollPane();
      getContentPane().add(scrollPane);
      springLayout.putConstraint(SpringLayout.SOUTH, scrollPane, -16, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, scrollPane, 15, SpringLayout.NORTH, getContentPane());

      calibrationList_ = new CalibrationList(core_);
      calibrationList_.getCalibrationsFromCore();

      // Create table with tooltip to show what is in the pixel size configurtaion
      calTable_ = new JTable() {
         private static final long serialVersionUID = -5870707914970187465L;

         public String getToolTipText(MouseEvent e) {
            String tip = "";
            java.awt.Point p = e.getPoint();
            int rowIndex = rowAtPoint(p);
            if (rowIndex < 0)
               return "";
            CalTableModel ptm = (CalTableModel)calTable_.getModel();
            String label = (String)ptm.getValueAt(rowIndex, 0);
            if (core_.isPixelSizeConfigDefined(label)) {
               try {
                  Configuration cfg = core_.getPixelSizeConfigData(label);
                  tip = cfg.getVerbose();
               } catch (Exception ex) {
                  handleException(ex);
               }
            }
            return tip;
         }
      };
      
      calTable_.setFont(new Font("", Font.PLAIN, 10));
      CalTableModel model = new CalTableModel();
      model.setData(calibrationList_);
      calTable_.setModel(model);
      calTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      scrollPane.setViewportView(calTable_);

      final JButton newButton = new JButton();
      newButton.setFont(new Font("", Font.PLAIN, 10));
      newButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            addNewCalibration();
         }
      });
      newButton.setText("New");
      getContentPane().add(newButton);
      springLayout.putConstraint(SpringLayout.SOUTH, newButton, 40, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, newButton, 17, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, newButton, -9, SpringLayout.EAST, getContentPane());

      final JButton editButton = new JButton();
      editButton.setFont(new Font("", Font.PLAIN, 10));
      editButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            editCalibration();
         }
      });
      editButton.setText("Edit");
      getContentPane().add(editButton);
      springLayout.putConstraint(SpringLayout.SOUTH, editButton, 65, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, editButton, 42, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, editButton, -9, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, editButton, -109, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, newButton, 0, SpringLayout.WEST, editButton);

      final JButton removeButton = new JButton();
      removeButton.setFont(new Font("", Font.PLAIN, 10));
      removeButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            removeCalibration();
         }
      });
      //removeButton.setIcon(SwingResourceManager.getIcon(PositionListDlg.class, "icons/cross.png"));
      removeButton.setText("Remove");
      getContentPane().add(removeButton);
      springLayout.putConstraint(SpringLayout.SOUTH, removeButton, 88, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, removeButton, 65, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, removeButton, -9, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, removeButton, -109, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, newButton, 0, SpringLayout.WEST, removeButton);

      final JButton removeAllButton = new JButton();
      removeAllButton.setFont(new Font("", Font.PLAIN, 10));
      removeAllButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            removeAllCalibrations();
         }
      });
      removeAllButton.setText("Remove All");
      getContentPane().add(removeAllButton);
      springLayout.putConstraint(SpringLayout.SOUTH, removeAllButton, 111, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, removeAllButton, 88, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, removeAllButton, 100, SpringLayout.WEST, removeButton);
      springLayout.putConstraint(SpringLayout.WEST, removeAllButton, 0, SpringLayout.WEST, removeButton);
      final JButton closeButton = new JButton();
      closeButton.setFont(new Font("", Font.PLAIN, 10));
      closeButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            savePosition();
            dispose();
         }
      });

      closeButton.setText("Close");
      getContentPane().add(closeButton);
      springLayout.putConstraint(SpringLayout.SOUTH, closeButton, 0, SpringLayout.SOUTH, scrollPane);
      springLayout.putConstraint(SpringLayout.NORTH, closeButton, -23, SpringLayout.SOUTH, scrollPane);
      springLayout.putConstraint(SpringLayout.EAST, scrollPane, -5, SpringLayout.WEST, closeButton);
      springLayout.putConstraint(SpringLayout.WEST, scrollPane, 10, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, closeButton, -5, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, closeButton, 0, SpringLayout.WEST, removeButton);


   }

   public void addNewCalibration() {
      String name = new String("Res" + calibrationList_.size());
      if (editPreset(name, "0.00")) {
         // Clear calibrationlist and re-read from core
         calibrationList_.getCalibrationsFromCore();
         CalTableModel ptm = (CalTableModel)calTable_.getModel();
         ptm.fireTableDataChanged();
         parentGUI_.setConfigChanged(true);
         parentGUI_.refreshGUI();
      }
   }

   public void editCalibration() {
      CalTableModel ptm = (CalTableModel)calTable_.getModel();
      int idx = calTable_.getSelectedRow();
      if (idx < 0) {
         handleError("A Pixel Size Calibration must be selected first.");
         return;
      }
      String size = (String)ptm.getValueAt(idx, 1);
      String label = (String)ptm.getValueAt(idx, 0);
      if (editPreset(label, size)) {
         calibrationList_.getCalibrationsFromCore();
         ptm.fireTableDataChanged();
         parentGUI_.setConfigChanged(true);
         parentGUI_.refreshGUI();
      }
   }

   public void updateCalibrations() {
      CalTableModel ptm = (CalTableModel)calTable_.getModel();
      calibrationList_.getCalibrationsFromCore();
      ptm.fireTableDataChanged();
      parentGUI_.setConfigChanged(true);
      parentGUI_.refreshGUI();
      int row = ptm.getCurrentPixelConfigRow();
      if (row >= 0)
          calTable_.setRowSelectionInterval(row, row);
   }

   public void setParentGUI(ScriptInterface parent) {
      parentGUI_ = parent;
   }

   public void removeCalibration() {
      CalTableModel ptm = (CalTableModel)calTable_.getModel();
      int idx = calTable_.getSelectedRow();
      String label = (String)ptm.getValueAt(idx, 0);
      int result = JOptionPane.showConfirmDialog(this, "Are you sure you want to remove calibration group " + label + "?",
            TITLE,
            JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);                                
      if (result == JOptionPane.OK_OPTION) {                                                            
         try {
            core_.deletePixelSizeConfig(label);                                                                      
         } catch (Exception e) {
            handleException(e);
         }
         calibrationList_.getCalibrationsFromCore();
         ptm.fireTableDataChanged();
         parentGUI_.refreshGUI();
         parentGUI_.setConfigChanged(true);
      }
   }

   public void removeAllCalibrations() {
      int result = JOptionPane.showConfirmDialog(this, "Are you absolutely sure you want to remove all calibrations? (No Undo possible!)",
            TITLE,
            JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
      if (result == JOptionPane.OK_OPTION) {
         try {
            for (int i = 0; i < calibrationList_.size(); i++) {
               core_.deletePixelSizeConfig(calibrationList_.get(i).getLabel());
            }
         } catch (Exception e) {
            handleException(e);
         }
         calibrationList_.getCalibrationsFromCore();
         CalTableModel ptm = (CalTableModel)calTable_.getModel();
         ptm.fireTableDataChanged();
         parentGUI_.refreshGUI();
         parentGUI_.setConfigChanged(true);
      }
   }

   public boolean editPreset(String calibrationName, String pixelSize) {
      CalibrationEditor dlg = new CalibrationEditor(calibrationName, pixelSize);
      dlg.setCore(core_);
      dlg.setVisible(true);
      if (dlg.isChanged())
         parentGUI_.setConfigChanged(true);

      return dlg.isChanged();
   }

   private void handleException (Exception e) {
      ReportingUtils.showError(e);
   }

   private void handleError (String errText) {
      ReportingUtils.showError(errText);
   }
}
