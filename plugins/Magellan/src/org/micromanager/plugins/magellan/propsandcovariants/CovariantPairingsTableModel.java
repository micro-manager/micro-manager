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

import org.micromanager.plugins.magellan.acq.MultipleAcquisitionManager;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author Henry
 */
public class CovariantPairingsTableModel extends AbstractTableModel {
   
   CovariantPairingsManager manager_;
   
   public CovariantPairingsTableModel( ) {
       super();
      manager_ = CovariantPairingsManager.getInstance();
      manager_.registerCovariantPairingsTableModel(this);
   }
   
    @Override
   public String getColumnName(int index) {
      return index == 0 ? "Active" : "Independent Variable : Dependent Variable";
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

   public boolean isAnyPairingActive() {
      for (int i = 0; i < manager_.getNumPairings(); i++) {
         if (manager_.isPairActiveForCurrentAcq(i) ) {
            return true;
         }
      }
      return false;
   }
}
