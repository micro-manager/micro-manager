/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package propsandcovariants;

import acq.MultipleAcquisitionManager;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author Henry
 */
public class CovariantPairingsTableModel extends AbstractTableModel {
   
   CovariantPairingsManager manager_;
   
   public CovariantPairingsTableModel( ) {
      manager_ = CovariantPairingsManager.getInstance();
      manager_.registerCovariantPairingsTableModel(this);
   }
   
    @Override
   public String getColumnName(int index) {
      return index == 0 ? "Active" : "Pairing";
   }

   @Override
   public int getRowCount() {
      return manager_.getNumPairings();
   }

   @Override
   public int getColumnCount() {
      return 2;
   }
   
   @Override
   public Class getColumnClass(int column) {
      return column == 0 ? Boolean.class : String.class;
   }

   @Override
   public boolean isCellEditable(int rowIndex, int colIndex) {
      return colIndex == 0;
   }

   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {    
      if (rowIndex == -1) {
         return null;
      } else if (columnIndex == 0) {
         return manager_.isPairActiveForCurrentAcq(rowIndex);
      }  else {
         return manager_.getPair(rowIndex);
      }
   }

   @Override
   public void setValueAt(Object value, int row, int col) {
      if (col == 0) {
         manager_.enablePairingForCurrentAcq(row, (Boolean) value);
      } 
   }
}
