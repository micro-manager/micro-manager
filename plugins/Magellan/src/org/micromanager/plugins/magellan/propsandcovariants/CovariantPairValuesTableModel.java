///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.plugins.magellan.propsandcovariants;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;

/**
 *
 * @author Henry
 */
public class CovariantPairValuesTableModel extends AbstractTableModel{

   private CovariantPairing pair_;
   
   public CovariantPairValuesTableModel( ) {
        super();
        CovariantPairingsManager manager = CovariantPairingsManager.getInstance();
        manager.registerCovariantValuesTableModel(this);
    }

   public void setPair(CovariantPairing pair) {
      pair_ = pair;
      fireTableDataChanged();
      //update column headings
      fireTableStructureChanged();
   } 
   
   public CovariantPairing getPairing() {
      return pair_;
   }
   
   @Override
   public String getColumnName(int index) {
      if (pair_ == null) {
         return index == 0 ? " " : " ";
      }
      return index == 0 ? pair_.getIndependentName(true) : pair_.getDependentName(true);
   }

   @Override
   public int getRowCount() {
      return pair_ == null ? 0 : pair_.getNumPairings();
   }

   @Override
   public int getColumnCount() {
      return 2;
   }

   @Override
   public boolean isCellEditable(int rowIndex, int colIndex) {
      return true;
   }

   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {    
      return pair_ == null ? null : pair_.getValue(columnIndex, rowIndex);     
   }

   @Override
   public void setValueAt(Object value, int row, int col) {
      if (value instanceof String) {
         pair_.setValue(col, row, (String) value);
      } else {
         pair_.setValue(col, row, (CovariantValue) value);
      }
      //account for resorting of values
      fireTableDataChanged();
   }

   public void updateColumnNames(TableColumnModel columnModel) {
      columnModel.getColumn(0).setHeaderValue(getColumnName(0));
      columnModel.getColumn(1).setHeaderValue(getColumnName(1));      
   }

   
}
