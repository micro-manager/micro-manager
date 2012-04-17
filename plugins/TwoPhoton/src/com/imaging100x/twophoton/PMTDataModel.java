//
// Two-photon plugin module for micro-manager
//
// COPYRIGHT:     Nenad Amodaj 2011, 100X Imaging Inc 2009
//
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  
//                
// AUTHOR:        Nenad Amodaj

package com.imaging100x.twophoton;

import java.util.Vector;

import javax.swing.JOptionPane;
import javax.swing.table.AbstractTableModel;

import mmcorej.CMMCore;

public class PMTDataModel extends AbstractTableModel {
   private static final long serialVersionUID = 1L;
   private String[] columnNames_ = {"PMT", "Voltage"};
   private Vector<String> names_;
   private CMMCore core_;
   public static final String[] availableNames_ = {"Violet", "Blue", "Green", "Yellow", "Red", "FarRed"};
      
   public PMTDataModel() {
      names_ = new Vector<String>(availableNames_.length);
      for (int i=0; i < availableNames_.length; i++)
         names_.add(availableNames_[i]);
   }

   public void setValueAt(Object value, int row, int col) {
      if (col == 1) {
         
         // update value in the core
         try {
            core_.setProperty(names_.get(row), "Volts", (String)value);
         } catch (Exception e) {
            handleError(e);
         }
         
         fireTableCellUpdated(row, col);
      }
   }

   public int getColumnCount() {
      return columnNames_.length;
   }

   public int getRowCount() {
      return names_.size();
   }

   public Object getValueAt(int rowIndex, int columnIndex) {
      if (columnIndex == 0) {
         return names_.get(rowIndex);
      } else if (columnIndex == 1) {
         return new Double(getPMTSetting(rowIndex));
      } else
         return null;
   }
   
   public double getMinValue(int idx) {
      if (core_ == null)
         return 0.0;
      
      try {
         return core_.getPropertyLowerLimit(names_.get(idx), "Volts");
      } catch (Exception e) {
         handleError(e);
         return 0.0;
      }
   }
   
   public double getMaxValue(int idx) {
      if (core_ == null)
         return 1.0;
      
      try {
         return core_.getPropertyUpperLimit(names_.get(idx), "Volts");
      } catch (Exception e) {
         handleError(e);
         return 0.0;
      }
   }

   public double getPMTSetting(int rowIndex) {
      if (core_ == null)
         return 0.0;
      
      try {
         String val = core_.getProperty(names_.get(rowIndex), "Volts");
         return Double.parseDouble(val);
      } catch (Exception e) {
         handleError(e);
      }
      return 0.0;
   }
   
   public String getColumnName(int column) {
      return columnNames_[column];
   }

   public boolean isCellEditable(int nRow, int nCol) {
      if (nCol == 1)
         return true;
      else
         return false;
   }
   
   public void setCore(CMMCore c) {
      core_ = c;
      names_.clear();
      for (int i=0; i < availableNames_.length; i++) {
         try {
            core_.getProperty(availableNames_[i], "Volts");
            names_.add(availableNames_[i]);
         } catch (Exception e) {
            // do nothing
         }
      }
      fireTableStructureChanged();
   }
   
   private void handleError(Exception e) {
      e.printStackTrace();
      JOptionPane.showMessageDialog(null, e.getMessage());
   }
   
   public void refresh() {
      fireTableDataChanged();
   }
   
   public PMTSetting[] getPMTSettings() {
      PMTSetting settings[] = new PMTSetting[names_.size()];
      for (int i=0; i<names_.size(); i++) {
         PMTSetting s = new PMTSetting();
         s.name = names_.get(i);
         s.volts = getPMTSetting(i);
         settings[i] = s;
      }
      return settings;
   }
   
   public void setCurrentDepthList(int listIdx) {
      for (int i=0; i<names_.size(); i++) {
         try {
            core_.setProperty(names_.get(i), "ListIndex", Integer.toString(listIdx));
            
         } catch (Exception e) {
            handleError(e);
         }
      }      
   }
}
