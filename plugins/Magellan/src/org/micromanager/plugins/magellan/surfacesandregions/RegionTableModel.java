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

package org.micromanager.plugins.magellan.surfacesandregions;

import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.table.AbstractTableModel;
import org.micromanager.plugins.magellan.surfacesandregions.MultiPosRegion;
import org.micromanager.plugins.magellan.surfacesandregions.RegionManager;

/**
 *
 * @author Henry
 */
class RegionTableModel extends AbstractTableModel implements ListDataListener {

   private final String[] COLUMNS = {"Name", "XY Device", "# Rows", "# Cols", "Width (um)", "Height (um)"};
   private RegionManager manager_;
   
   public RegionTableModel(RegionManager manager) {
      manager_ = manager;
   }
   
   @Override
   public int getRowCount() {
      return manager_.getNumberOfRegions();
   }
   
   @Override
   public String getColumnName(int index) {
      return COLUMNS[index];
   }

   @Override
   public int getColumnCount() {
      return COLUMNS.length;
   }

   @Override
   public boolean isCellEditable(int rowIndex, int colIndex) {
      if (colIndex == 0) {
         return true;
      }
      return false;
   }
   
   @Override 
   public void setValueAt(Object value, int row, int col) {
      if (col == 0) {
         manager_.getRegion(row).rename((String) value);
      }
   }
   
   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {
      MultiPosRegion region = manager_.getRegion(rowIndex);
      if (columnIndex == 0) {
         return manager_.getRegion(rowIndex).getName();
      } else if (columnIndex == 1) {
         return manager_.getRegion(rowIndex).getXYDevice();
      } else if (columnIndex == 2) {
         return region.numRows();
      } else if (columnIndex == 3) {
         return region.numCols();
      } else if (columnIndex == 4) {
         return region.getWidth_um();
      } else {
         return region.getHeight_um();
      }
   }

   @Override
   public void intervalAdded(ListDataEvent e) {
      this.fireTableDataChanged();
   }

   @Override
   public void intervalRemoved(ListDataEvent e) {
      this.fireTableDataChanged();
   }

   @Override
   public void contentsChanged(ListDataEvent e) {
      this.fireTableDataChanged();
   }
   
}
