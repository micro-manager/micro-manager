/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tables;

import acq.MultipleAcquisitionManager;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author Henry
 */
public class MultipleAcquisitionTableModel extends AbstractTableModel {

   private static final String[] COLUMNS = {"Name", "Order","YYY"};
   private MultipleAcquisitionManager manager_;
   
   public MultipleAcquisitionTableModel(MultipleAcquisitionManager manager) {
      super();
      manager_ = manager;
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
         return manager_.getAcquisitionName(rowIndex);
      }
      return 0;
   }

   @Override
   public boolean isCellEditable(int rowIndex, int colIndex) {
      return false;
   }


}
