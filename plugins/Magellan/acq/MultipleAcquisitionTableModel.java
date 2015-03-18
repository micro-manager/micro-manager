/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import acq.MultipleAcquisitionManager;
import gui.GUI;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author Henry
 */
public class MultipleAcquisitionTableModel extends AbstractTableModel {

   private static final String[] COLUMNS = {"Order","Name","Status"};
   private MultipleAcquisitionManager manager_;
   private GUI gui_;
   
   public MultipleAcquisitionTableModel(MultipleAcquisitionManager manager, GUI gui) {
      super();
      manager_ = manager;
      gui_ = gui;
   }
   
   @Override
   public String getColumnName(int index) {
      return COLUMNS[index];
   }

   @Override
   public int getRowCount() {
      return manager_.getSize();
   }

   @Override
   public int getColumnCount() {
      return COLUMNS.length;
   }

   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {
      if (columnIndex == 0) {
         return manager_.getGroupIndex(rowIndex) + 1;
      } else if (columnIndex == 1) {
         return manager_.getAcquisitionName(rowIndex);
      } else {
         return manager_.getAcqStatus(rowIndex);
      }
   }

   @Override
   public void setValueAt(Object value, int row, int col) {
       if (col == 1) {
         manager_.getAcquisition(row).name_ = (String) value;
         gui_.refreshAcquisitionSettings(); // update name as shown in acq settings

      }
   }
   
   @Override
   public boolean isCellEditable(int rowIndex, int colIndex) {
      return colIndex == 1 ? true : false;
   }


}
