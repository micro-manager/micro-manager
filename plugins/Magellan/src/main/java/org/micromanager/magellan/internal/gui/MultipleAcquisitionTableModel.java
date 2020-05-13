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

package org.micromanager.magellan.internal.gui;

import javax.swing.table.AbstractTableModel;
import org.micromanager.magellan.internal.magellanacq.MagellanAcquisitionsManager;

/**
 *
 * @author Henry
 */
public class MultipleAcquisitionTableModel extends AbstractTableModel {

   private static final String[] COLUMNS = {"Name","Description","Status"};
   private MagellanAcquisitionsManager manager_;
   private GUI gui_;
   
   public MultipleAcquisitionTableModel(MagellanAcquisitionsManager manager, GUI gui) {
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
      return manager_.getNumberOfAcquisitions();
   }

   @Override
   public int getColumnCount() {
      return COLUMNS.length;
   }

   
   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {
      if (columnIndex == 0) {
         return manager_.getAcquisitionSettingsName(rowIndex);
      } else if (columnIndex == 1) {
         return manager_.getAcquisitionDescription(rowIndex);
      } else {
         return manager_.getAcqStatus(rowIndex);
      }
   }

   @Override
   public void setValueAt(Object value, int row, int col) {
       if (col == 0) {
         manager_.getAcquisitionSettings(row).name_ = (String) value;
      }
      gui_.storeCurrentAcqSettings();
   }
   
   @Override
   public boolean isCellEditable(int rowIndex, int colIndex) {
      return colIndex == 0 ? true : false;
   }


}
