package org.micromanager.positionlist;

import com.google.common.eventbus.EventBus;

import java.util.prefs.Preferences;

import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

/**
 * Model holding axis data, used to determine which axis will be recorded
 */
class AxisTableModel extends AbstractTableModel {
   private static final long serialVersionUID = 1L;
   private boolean isEditable_ = true;
   private AxisList axisList_;
   private JTable axisTable_;
   private EventBus bus_;
   private Preferences prefs_;
   public final String[] COLUMN_NAMES = new String[] {
         "Use",
         "Stage name"
   };
  
   public AxisTableModel(AxisList list, JTable table, EventBus bus, 
         Preferences prefs) {
      axisList_ = list;
      axisTable_ = table;
      bus_ = bus;
      prefs_ = prefs;
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
      if (state) {
         for (int i=0; i < getRowCount(); i++) {
            
         }
      }
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
      if (columnIndex == 0) { // I.e. action was in the column with checkboxes
         axisList_.get(rowIndex).setUse((Boolean) value);
         prefs_.putBoolean(axisList_.get(rowIndex).getAxisName(), 
               (Boolean) value); 
         bus_.post(new MoversChangedEvent());
      }
      fireTableCellUpdated(rowIndex, columnIndex);
      axisTable_.clearSelection();
   }
}
