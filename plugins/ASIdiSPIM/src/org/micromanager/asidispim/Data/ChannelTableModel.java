///////////////////////////////////////////////////////////////////////////////
//FILE:          ChannelTableModel.java
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
 * Representation of information in channel table of 
 * diSPIM plugin.  Based on org.micromanager.utils.ChannelSpec.java.
 * Handles saving preferences to registry assuming column/row don't change.
 * @author Jon
 */
@SuppressWarnings("serial")
public class ChannelTableModel extends AbstractTableModel {
   public static final String[] columnNames = {"Use?", "Preset"};
   public static final int PLOGIC_OFFSET = 5;
   public static final int columnIndex_useChannel = 0;
   public static final int columnIndex_config = 1;
   private final ArrayList<ColorSpec> channels_;
   private final Prefs prefs_;
   private final String prefNode_;


   public ChannelTableModel(Prefs prefs, String prefNode) {
      channels_ = new ArrayList<ColorSpec>();
      prefs_ = prefs;
      prefNode_ = prefNode;
   } //constructor

   public void addChannel() {
      addNewChannel(new ColorSpec(
               prefs_.getBoolean(prefNode_ + "_" + channels_.size(), 
                       Prefs.Keys.COLOR_USE_COLOR, false),
               prefs_.getString(prefNode_ + "_" + channels_.size(), 
                       Prefs.Keys.COLOR_CONFIG, "")));
   }
   
   /**
    *  Removes the specified row from the channel table 
    * @param i - 0-based row number of channel to be removed
   */
   public void removeChannel(int i) {
      channels_.remove(i);
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
      return (channels_ == null) ? 0 : channels_.size();
   }
   
   @Override
   public boolean isCellEditable(int rowIndex, int columnIndex) {
      return true;
   }

   @Override
   public void setValueAt(Object value, int rowIndex, int columnIndex) {
      ColorSpec color = channels_.get(rowIndex);
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
      }
      fireTableCellUpdated(rowIndex, columnIndex);
   }

   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {
      ColorSpec color = channels_.get(rowIndex);
      switch (columnIndex) {
      case columnIndex_useChannel:
         return color.useChannel;
      case columnIndex_config:
         return color.config;
      default: 
         ReportingUtils.logError("ColorTableModel getValuAt() didn't match");
         return null;
      }
   }
   
   public final void addNewChannel(ColorSpec color) {
      channels_.add(color);
   }

}

