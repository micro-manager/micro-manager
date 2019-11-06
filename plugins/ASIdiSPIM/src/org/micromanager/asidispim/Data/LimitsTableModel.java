///////////////////////////////////////////////////////////////////////////////
//FILE:          LimitsTableModel.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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
package org.micromanager.asidispim.Data;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import org.micromanager.utils.ReportingUtils;


/**
 * Representation of information in limits table of 
 * diSPIM plugin.  Based on org.micromanager.utils.ChannelSpec.java.
 * Handles saving preferences to registry assuming column/row don't change.
 * @author Jon
 */
@SuppressWarnings("serial")
public class LimitsTableModel extends AbstractTableModel {
   public static final String[] columnNames = {"Use?", "X Coeff", "Y Coeff", "Z Coeff", "Max Sum [\u00B5m]", "Invert?"}; //, "Status"};
   public static final int columnIndex_UseLimit = 0;
   public static final int columnIndex_xCoeff = 1;
   public static final int columnIndex_yCoeff = 2;
   public static final int columnIndex_zCoeff = 3;
   public static final int columnIndex_Sum = 4;
   public static final int columnIndex_Invert = 5;
//   public static final int columnIndex_Status = 6;
   
   private final ArrayList<LimitsSpec> limits_;
   private LimitsSpec[] usedLimits_;
   private final Prefs prefs_;
   private final String prefNode_;


   public LimitsTableModel(Prefs prefs, String prefNode) {
      limits_ = new ArrayList<LimitsSpec>();
      prefs_ = prefs;
      prefNode_ = prefNode;
      
      // initialize the table based on prefs
      int nrRows = prefs_.getInt(prefNode_, Prefs.Keys.NRLIMITROWS, 0);
      for (int i=0; i<nrRows; ++i) {
         String prefNodeInit = prefNode_ + "_" + i;
         addLimitToTable(new LimitsSpec(
               prefs_.getBoolean(prefNodeInit, Prefs.Keys.LIMITS_USE, true),
               prefs_.getFloat(prefNodeInit, Prefs.Keys.LIMITS_XCOEFF, 0.0f),
               prefs_.getFloat(prefNodeInit, Prefs.Keys.LIMITS_YCOEFF, 0.0f),
               prefs_.getFloat(prefNodeInit, Prefs.Keys.LIMITS_ZCOEFF, 0.0f),
               prefs_.getFloat(prefNodeInit, Prefs.Keys.LIMITS_SUM, 0.0f),
               prefs_.getBoolean(prefNodeInit, Prefs.Keys.LIMITS_INVERT, false)
               ));
      }
      
      updateUsedLimits();
      
   } //constructor
   
   /**
    * add a new empty limit
    */
   public final void addNewEmptyLimit() {
      addLimitToTable(new LimitsSpec(true, 0.0f, 0.0f, 0.0f, 0.0f, false));
      prefs_.putInt(prefNode_, Prefs.Keys.NRLIMITROWS, limits_.size());
   }
   
   /**
    *  Removes the specified row from the limits table 
    * @param i - 0-based row number of limit to be removed
   */
   public void removeLimit(int row) {
      limits_.remove(row);
      final int newSize = limits_.size();
      prefs_.putInt(prefNode_, Prefs.Keys.NRLIMITROWS, newSize);
      // need to rewrite prefs for all rows below this in table; do by removing and re-adding all of them
      for (int i=row; i<newSize; ++i) {
         LimitsSpec limit = limits_.get(row);
         limits_.remove(row);
         addLimitToTable(limit);
      }
   }
   
   @Override
   public int getColumnCount() {
      return columnNames.length;
   }

   @Override
   public String getColumnName(int columnIndex) {
      return columnNames[columnIndex];
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   @Override
   public Class getColumnClass(int columnIndex) {
      return getValueAt(0, columnIndex).getClass();
   }

   @Override
   public int getRowCount() {
      return (limits_ == null) ? 0 : limits_.size();
   }
   
   @Override
   public boolean isCellEditable(int rowIndex, int columnIndex) {
      return true;
   }

   @Override
   public void setValueAt(Object value, int rowIndex, int columnIndex) {
      LimitsSpec limit = limits_.get(rowIndex);
      String prefNode = prefNode_ + "_" + rowIndex;
      switch (columnIndex) {
      case columnIndex_UseLimit:
         if (value instanceof Boolean) {
            boolean val = (Boolean) value;
            limit.use_ = val;
            prefs_.putBoolean(prefNode, Prefs.Keys.LIMITS_USE, (Boolean) val);
         }
         break;
      case columnIndex_xCoeff:
         if (value instanceof Double) {
            Double val = (Double) value;
            limit.xCoeff_ = val;
            prefs_.putFloat(prefNode, Prefs.Keys.LIMITS_XCOEFF, val.floatValue());
         }
         break;
      case columnIndex_yCoeff:
         if (value instanceof Double) {
            Double val = (Double) value;
            limit.yCoeff_ = val;
            prefs_.putFloat(prefNode, Prefs.Keys.LIMITS_YCOEFF, val.floatValue());
         }
         break;
      case columnIndex_zCoeff:
         if (value instanceof Double) {
            Double val = (Double) value;
            limit.zCoeff_ = val;
            prefs_.putFloat(prefNode, Prefs.Keys.LIMITS_ZCOEFF, val.floatValue());
         }
         break;
      case columnIndex_Sum:
         if (value instanceof Double) {
            Double val = (Double) value;
            limit.sum_ = val;
            prefs_.putFloat(prefNode, Prefs.Keys.LIMITS_SUM, val.floatValue());
         }
         break;
      case columnIndex_Invert:
         if (value instanceof Boolean) {
            boolean val = (Boolean) value;
            limit.invert_ = val;
            prefs_.putBoolean(prefNode, Prefs.Keys.LIMITS_INVERT, (Boolean) val);
         }
         break;
      }
      fireTableCellUpdated(rowIndex, columnIndex);
      updateUsedLimits();
   }

   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {
      LimitsSpec limit = limits_.get(rowIndex);
      switch (columnIndex) {
      case columnIndex_UseLimit:
         return limit.use_;
      case columnIndex_xCoeff:
         return limit.xCoeff_;
      case columnIndex_yCoeff:
         return limit.yCoeff_;
      case columnIndex_zCoeff:
         return limit.zCoeff_;
      case columnIndex_Sum:
         return limit.sum_;
      case columnIndex_Invert:
         return limit.invert_;
      default: 
         ReportingUtils.logError("LimitsTableModel getValueAt() didn't match");
         return null;
      }
   }
   
   /**
    * Add new row to the limit table and update the internal representation of the table.
    * Handles writing table values to prefs for retrieval in future but does not update pref with table size.
    * @param limit
    */
   private final void addLimitToTable(LimitsSpec limit) {
      String prefNode = prefNode_ + "_" + limits_.size();
      prefs_.putBoolean(prefNode, Prefs.Keys.LIMITS_USE, limit.use_);
      prefs_.putFloat(prefNode, Prefs.Keys.LIMITS_XCOEFF, (float) limit.xCoeff_);
      prefs_.putFloat(prefNode, Prefs.Keys.LIMITS_YCOEFF, (float) limit.yCoeff_);
      prefs_.putFloat(prefNode, Prefs.Keys.LIMITS_ZCOEFF, (float) limit.zCoeff_);
      prefs_.putFloat(prefNode, Prefs.Keys.LIMITS_SUM, (float) limit.sum_);
      prefs_.putBoolean(prefNode, Prefs.Keys.LIMITS_INVERT, limit.invert_);
      limits_.add(limit);
   }
   
   /**
    * Returns array of limits that are currently set be "used".
    * Returns them in order that they are in the table, omitting unused ones.
    * @return 
    */
   public LimitsSpec[] getUsedLimits() {
      return usedLimits_;
   }
   
   private void updateUsedLimits() {
      List<LimitsSpec> result = new ArrayList<LimitsSpec>();
      for (LimitsSpec ch : limits_) {
         if (ch.use_) {
            result.add(ch);
         }
      }
      usedLimits_ = result.toArray(new LimitsSpec[0]);
   }
   

}

