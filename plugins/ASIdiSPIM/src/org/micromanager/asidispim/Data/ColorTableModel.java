///////////////////////////////////////////////////////////////////////////////
//FILE:          ColorTableModel.java
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

import javax.swing.table.AbstractTableModel;

import org.micromanager.utils.ReportingUtils;


/**
 * Representation of information in color table (akin to channel table) of 
 * diSPIM plugin.  Based on org.micromanager.utils.ChannelSpec.java.
 * Handles saving preferences to registry assuming column/row don't change.
 * @author Jon
 */
@SuppressWarnings("serial")
public class ColorTableModel extends AbstractTableModel {
   public static final String[] columnNames = {"Use?", "Preset", "PLC #"};
   public static final int NUM_COLORS = 4;
   public static final int PLOGIC_OFFSET = 5;
   public static final int columnIndex_useChannel = 0;
   public static final int columnIndex_config = 1;
   public static final int columnIndex_pLogicNum = 2;
   private ArrayList<ColorSpec> colors_;
   private final Prefs prefs_;
   private final String prefNode_;

   public ColorTableModel(Prefs prefs, String prefNode) {
      colors_ = new ArrayList<ColorSpec>();
      prefs_ = prefs;
      prefNode_ = prefNode;
      
      for (int i=0; i<NUM_COLORS; i++) {
         addNewColor(new ColorSpec(i+PLOGIC_OFFSET,
               prefs_.getBoolean(prefNode_ + "_" + i, Prefs.Keys.COLOR_USE_COLOR, false),
               prefs_.getString(prefNode_ + "_" + i, Prefs.Keys.COLOR_CONFIG, "")));
      }
      
   }//constructor

   @Override
   public int getColumnCount() { return columnNames.length; }

   @Override
   public String getColumnName(int column) { return columnNames[column]; }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   public Class getColumnClass(int column) { return getValueAt(0, column).getClass(); }

   @Override
   public int getRowCount() { return (colors_ == null) ? 0 : colors_.size(); }
   
   @Override
   public boolean isCellEditable(int rowIndex, int columnIndex) { return true; }

   @Override
   public void setValueAt(Object value, int rowIndex, int columnIndex) {
      ColorSpec color = colors_.get(rowIndex);
      switch (columnIndex) {
      case columnIndex_useChannel:
         if (value instanceof Boolean) {
            boolean val = (Boolean) value;
            color.useChannel = val;
            prefs_.putBoolean(prefNode_ + "_" + rowIndex, 
                  Prefs.Keys.COLOR_USE_COLOR, (Boolean) val);
         }
         break;
      case columnIndex_config:
         if (value instanceof String) {
            String val = (String) value;
            color.config = val;
            prefs_.putString(prefNode_+ "_" + rowIndex, 
                  Prefs.Keys.COLOR_CONFIG, val);
         }
         break;
      case columnIndex_pLogicNum:
         // no need to remember; is fixed for the rowIndex as implemented
         if (value instanceof Integer) {
            color.pLogicNum = (Integer) value;
         }
         break;
      }
      fireTableCellUpdated(rowIndex, columnIndex);
   }

   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {
      ColorSpec color = colors_.get(rowIndex);
      switch (columnIndex) {
      case columnIndex_useChannel:
         return color.useChannel;
      case columnIndex_config:
         return color.config;
      case columnIndex_pLogicNum:
         return color.pLogicNum;
      default: 
         ReportingUtils.logError("ColorTableModel getValuAt() didn't match");
         return null;
      }
   }
   
   public void addNewColor(ColorSpec color) {
      colors_.add(color);
   }

}

