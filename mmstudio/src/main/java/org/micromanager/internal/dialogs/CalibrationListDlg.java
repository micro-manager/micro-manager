///////////////////////////////////////////////////////////////////////////////
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


package org.micromanager.internal.dialogs;

import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import org.micromanager.Studio;
import org.micromanager.events.ShutdownCommencingEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.*;

/*
 * Dialog for listing pixel size configuration presets and the corresponding
 * pixel size for each.
 */
public final class CalibrationListDlg extends JDialog {
   private static final long serialVersionUID = 1L;
   private static final String TITLE = "Calibration Editor";
   public static final String PIXEL_SIZE_GROUP = "ConfigPixelSize";

   private JTable calTable_;
   private SpringLayout springLayout;
   private CMMCore core_;
   private CalibrationList calibrationList_;
   private Studio studio_;
   private ConfigDialog configDialog_;
   private boolean disposed_ = false;

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

      @Override
      public int getRowCount() {
         return calibrationList.size();
      }
      
      @Override
      public int getColumnCount() {
         return COLUMN_NAMES.length;
      }

      @Override
      public String getColumnName(int columnIndex) {
         return COLUMN_NAMES[columnIndex];
      }

      @Override
      public Object getValueAt(int rowIndex, int columnIndex) {
         Calibration cal = calibrationList.get(rowIndex);
         switch (columnIndex) {
            case 0:
               return cal.getLabel();
            case 1:
               return cal.getPixelSizeUm().toString();
            default:
               return null;
         }
      }

      @Override
      public void setValueAt(Object value, int rowIndex, int columnIndex) {
         Calibration cal = calibrationList.get(rowIndex);
         if (columnIndex == 1) {
            try {
               double val = Double.parseDouble(value.toString());
               core_.setPixelSizeUm(cal.getLabel(), val);
               cal.setPixelSizeUm(val);
               ((MMStudio) studio_).setConfigChanged(true);
               studio_.app().refreshGUI();
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

      @Override
      public boolean isCellEditable(int rowIndex, int columnIndex) {
         return columnIndex == 1;
      }

      private void handleException (Exception e) {
         String errText = "Exception occurred: " + e.getMessage();
         JOptionPane.showMessageDialog(null, errText);
      }
   }


   /**
    * Create the dialog
    * @param core - The Micro-Manager core object
    */
   public CalibrationListDlg(CMMCore core) {
      super();
      core_ = core;
      super.setTitle("Pixel Size Calibration");
      springLayout = new SpringLayout();
      super.getContentPane().setLayout(springLayout);

      super.setMinimumSize(new Dimension(263, 239));

      setBounds(100, 100, 365, 495);
      WindowPositioning.setUpBoundsMemory(this, this.getClass(), null);

      Rectangle r = super.getBounds();
      r.x +=1;
      
      final JScrollPane scrollPane = new JScrollPane();
      super.getContentPane().add(scrollPane);
      springLayout.putConstraint(SpringLayout.SOUTH, scrollPane, -16, 
              SpringLayout.SOUTH, super.getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, scrollPane, 15, 
              SpringLayout.NORTH, super.getContentPane());

      calibrationList_ = new CalibrationList(core_);
      calibrationList_.getCalibrationsFromCore();

      // Create table with tooltip to show what is in the pixel size configurtaion
      calTable_ = new DaytimeNighttime.Table() {
         private static final long serialVersionUID = -5870707914970187465L;

         @Override
         public String getToolTipText(MouseEvent e) {
            String tip = "";
            java.awt.Point p = e.getPoint();
            int rowIndex = rowAtPoint(p);
            if (rowIndex < 0) {
               return "";
            }
            CalTableModel ptm = (CalTableModel)calTable_.getModel();
            String label = (String)ptm.getValueAt(rowIndex, 0);
            try {
               if (core_.isPixelSizeConfigDefined(label)) {
                  Configuration cfg = core_.getPixelSizeConfigData(label);
                  tip = cfg.getVerbose();
               }
            } catch (Exception ex) {
               handleException(ex);
            }
            return tip;
         }
      };
      
      calTable_.setFont(new Font("", Font.PLAIN, 10));
      CalTableModel model = new CalTableModel();
      model.setData(calibrationList_);
      calTable_.setModel(model);
      calTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      Integer activeIndex = calibrationList_.getActiveCalibration();
      if (activeIndex != null) {
         calTable_.setRowSelectionInterval(activeIndex, activeIndex);
      }
      scrollPane.setViewportView(calTable_);

      final JButton newButton = new JButton();
      newButton.setFont(new Font("", Font.PLAIN, 10));
      newButton.addActionListener((ActionEvent arg0) -> {
         addNewCalibration();
      });
      newButton.setText("New");
      super.getContentPane().add(newButton);
      springLayout.putConstraint(SpringLayout.SOUTH, newButton, 40, 
              SpringLayout.NORTH, super.getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, newButton, 17, 
              SpringLayout.NORTH, super.getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, newButton, -9, 
              SpringLayout.EAST, super.getContentPane());

      final JButton editButton = new JButton();
      editButton.setFont(new Font("", Font.PLAIN, 10));
      editButton.addActionListener((ActionEvent arg0) -> {
         editCalibration();
      });
      editButton.setText("Edit");
      super.getContentPane().add(editButton);
      springLayout.putConstraint(SpringLayout.SOUTH, editButton, 65, 
              SpringLayout.NORTH, super.getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, editButton, 42, 
              SpringLayout.NORTH, super.getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, editButton, -9, 
              SpringLayout.EAST, super.getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, editButton, -109, 
              SpringLayout.EAST, super.getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, newButton, 0, 
              SpringLayout.WEST, editButton);

      final JButton removeButton = new JButton();
      removeButton.setFont(new Font("", Font.PLAIN, 10));
      removeButton.addActionListener((ActionEvent arg0) -> {
         removeCalibration();
      });
     
      removeButton.setText("Remove");
      super.getContentPane().add(removeButton);
      springLayout.putConstraint(SpringLayout.SOUTH, removeButton, 88, 
              SpringLayout.NORTH, super.getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, removeButton, 65, 
              SpringLayout.NORTH, super.getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, removeButton, -9, 
              SpringLayout.EAST, super.getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, removeButton, -109, 
              SpringLayout.EAST, super.getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, newButton, 0, 
              SpringLayout.WEST, removeButton);

      final JButton removeAllButton = new JButton();
      removeAllButton.setFont(new Font("", Font.PLAIN, 10));
      removeAllButton.addActionListener((ActionEvent arg0) -> {
         removeAllCalibrations();
      });
      removeAllButton.setText("Remove All");
      super.getContentPane().add(removeAllButton);
      springLayout.putConstraint(SpringLayout.SOUTH, removeAllButton, 111, 
              SpringLayout.NORTH, super.getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, removeAllButton, 88, 
              SpringLayout.NORTH, super.getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, removeAllButton, 100, 
              SpringLayout.WEST, removeButton);
      springLayout.putConstraint(SpringLayout.WEST, removeAllButton, 0, 
              SpringLayout.WEST, removeButton);
      final JButton closeButton = new JButton();
      closeButton.setFont(new Font("", Font.PLAIN, 10));
      closeButton.addActionListener((ActionEvent arg0) -> {
         dispose();
      });

      closeButton.setText("Close");
      super.getContentPane().add(closeButton);
      springLayout.putConstraint(SpringLayout.SOUTH, closeButton, 0, 
              SpringLayout.SOUTH, scrollPane);
      springLayout.putConstraint(SpringLayout.NORTH, closeButton, -23, 
              SpringLayout.SOUTH, scrollPane);
      springLayout.putConstraint(SpringLayout.EAST, scrollPane, -5, 
              SpringLayout.WEST, closeButton);
      springLayout.putConstraint(SpringLayout.WEST, scrollPane, 10, 
              SpringLayout.WEST, super.getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, closeButton, -5, 
              SpringLayout.EAST, super.getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, closeButton, 0, 
              SpringLayout.WEST, removeButton);
   }

   public void addNewCalibration() {
      if (configDialog_ != null) {
         configDialog_.setVisible(true);
         configDialog_.toFront();
         return;
      }
      String name = "Res" + calibrationList_.size();
      if (editPreset(name, "0.00", true)) {
         // Clear calibrationlist and re-read from core
         calibrationList_.getCalibrationsFromCore();
         CalTableModel ptm = (CalTableModel)calTable_.getModel();
         ptm.fireTableDataChanged();
         ((MMStudio) studio_).setConfigChanged(true);
         studio_.app().refreshGUI();
      }
   }

   public void editCalibration() {
      if (configDialog_ != null) {
         configDialog_.setVisible(true);
         configDialog_.toFront();
         return;
      }
      CalTableModel ptm = (CalTableModel)calTable_.getModel();
      int idx = calTable_.getSelectedRow();
      if (idx < 0) {
         handleError("A Pixel Size Calibration must be selected first.");
         return;
      }
      String size = (String)ptm.getValueAt(idx, 1);
      String label = (String)ptm.getValueAt(idx, 0);
      if (editPreset(label, size, false)) {
         calibrationList_.getCalibrationsFromCore();
         ptm.fireTableDataChanged();
         ((MMStudio) studio_).setConfigChanged(true);
         studio_.app().refreshGUI();
      }
   }

   public void updateCalibrations() {
      refreshCalibrations();
      ((MMStudio) studio_).setConfigChanged(true);
      studio_.app().refreshGUI();
   }
   
   public void refreshCalibrations() {
      CalTableModel ptm = (CalTableModel)calTable_.getModel();
      calibrationList_.getCalibrationsFromCore();
      ptm.fireTableDataChanged();
      int row = ptm.getCurrentPixelConfigRow();
      if (row >= 0) {
         calTable_.setRowSelectionInterval(row, row);
      }
   }

   public void setParentGUI(Studio parent) {
      studio_ = parent;
      studio_.events().registerForEvents(this);
   }
   
   @Override
   public void dispose() {
      if (!disposed_) {
         if (studio_ != null && !disposed_) {
            studio_.events().unregisterForEvents(this);
            MMStudio mmStudio = (MMStudio) studio_;
            if (mmStudio.getIsConfigChanged()) {
               Object[] options = {"Yes", "No"};
               int userFeedback = JOptionPane.showOptionDialog(null,
                       "Save Changed Pixel Configuration?", "Micro-Manager",
                       JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                       null, options, options[0]);
               if (userFeedback == JOptionPane.YES_OPTION) {
                  mmStudio.promptToSaveConfigPresets();
               }
            }
         }
         if (configDialog_ != null) {
            configDialog_.dispose();
         }
         super.dispose();
         disposed_ = true;
      }
   }
    
   /**
    * @param event indicating that shutdown is happening
    */
   @Subscribe
   public void onShutdownCommencing(ShutdownCommencingEvent event) {
      this.dispose();
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
         studio_.app().refreshGUI();
         ((MMStudio) studio_).setConfigChanged(true);
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
         studio_.app().refreshGUI();
         ((MMStudio) studio_).setConfigChanged(true);
      }
   }

   public boolean editPreset(String calibrationName, String pixelSize, boolean newConfig) {
      int result = JOptionPane.showConfirmDialog(this,
            "Devices will move to the settings being edited. Please make sure there is no danger of collision.",
            TITLE,
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
      if (result == JOptionPane.OK_OPTION) {
         if (calibrationList_.size() == 0) {
            configDialog_ = new PixelConfigEditor(calibrationName, 
                    this, pixelSize, true);
         } else {
            configDialog_ = new PixelPresetEditor(calibrationName, 
                    this, pixelSize, newConfig);
         }
         configDialog_.setVisible(true);
      }
      return false;
   }
   
   public void endedEditingPreset(ConfigDialog dialog) {
      if (dialog.equals(configDialog_)) {
         configDialog_ = null;
      }
   }
   
   public Studio getStudio() {
      return studio_;
   }

   private void handleException (Exception e) {
      ReportingUtils.showError(e, this);
   }

   private void handleError (String errText) {
      ReportingUtils.showError(errText, this);
   }
}
