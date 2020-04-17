///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// COPYRIGHT:    University of California, San Francisco
//
// LICENSE:      This file is distributed under the BSD license.
// License text is included with the source distribution.
//
// This file is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty
// of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
// IN NO EVENT SHALL THE COPYRIGHT OWNER OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.internal.positionlist;

import com.google.common.eventbus.EventBus;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import org.micromanager.internal.MMStudio;

/**
 * Model holding axis data, used to determine which axis will be recorded
 */
class AxisTableModel extends AbstractTableModel {
   private static final long serialVersionUID = 1L;
   private boolean isEditable_ = true;
   private final AxisList axisList_;
   private final JTable axisTable_;
   private final EventBus bus_;
   public final String[] COLUMN_NAMES = new String[] {
         "Use",
         "Stage name"
   };
  
   public AxisTableModel(AxisList list, JTable table, EventBus bus) {
      axisList_ = list;
      axisTable_ = table;
      bus_ = bus;
      // restore the usage settings from our previous session
      for (int i = 0; i < axisList_.getNumberOfPositions(); i++) {
         axisList_.get(i).setUse(MMStudio.getInstance().profile().
                 getSettings(AxisTableModel.class).
                 getBoolean(axisList_.get(i).getAxisName(), true ) );
      }
   }

   @Override
   public int getRowCount() {
      return axisList_.getNumberOfPositions();
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
      AxisData aD = axisList_.get(rowIndex);
      if (aD != null) {
         if (columnIndex == 0) {
            return aD.getUse();
         } else if (columnIndex == 1) {
            return aD.getAxisName();
         }
      }
      return null;
   }
   
   @Override
   public Class<?> getColumnClass(int c) {
      return getValueAt(0, c).getClass();
   }
   
   public void setEditable(boolean state) {
      isEditable_ = state;
   }
   
   @Override
   public boolean isCellEditable(int rowIndex, int columnIndex) {
      if (columnIndex == 0) {
         return isEditable_;
      }
      return false;
   }
   
   @Override
   public void setValueAt(Object value, int rowIndex, int columnIndex) {
      if (columnIndex == 0) { // i.e. action was in the column with checkboxes
         axisList_.get(rowIndex).setUse((Boolean) value);
         // store new choice to profile
         MMStudio.getInstance().profile().getSettings(AxisTableModel.class).
                 putBoolean(axisList_.get(rowIndex).getAxisName(),
                            (Boolean) value);
         bus_.post(new MoversChangedEvent());
      }
      fireTableCellUpdated(rowIndex, columnIndex);
      axisTable_.clearSelection();
   }
}
