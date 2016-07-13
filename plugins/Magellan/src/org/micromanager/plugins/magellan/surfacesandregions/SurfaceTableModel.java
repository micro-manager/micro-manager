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

import javax.swing.table.AbstractTableModel;
import org.micromanager.plugins.magellan.misc.Log;
import org.micromanager.plugins.magellan.misc.NumberUtils;

/**
 *
 * @author Henry
 */
class SurfaceTableModel extends AbstractTableModel  {

   private final String[] COLUMNS = {"Name", "XY Device", "Z Device", "XY padding (um)", "# Positions"};
   private SurfaceManager manager_;
   
   public SurfaceTableModel(SurfaceManager manager) {
      manager_ = manager;
   }
   
   @Override
   public int getRowCount() {
      return manager_.getNumberOfSurfaces();
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
      if (colIndex == 0 || colIndex == 1) {
         return true;
      }
      return false;
   }
   
   @Override   
   public void setValueAt(Object value, int row, int col) {
      if (col == 0) {
         try {
            manager_.renameSurface(row,(String) value);
         } catch (Exception ex) {
            Log.log("Surface name already taken",true);
         }
      } else if (col == 1) {
         manager_.getSurface(row).setXYPadding(NumberUtils.parseDouble((String) value));
      }  
   }
   
   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {
      SurfaceInterpolator surface = manager_.getSurface(rowIndex);
      if (columnIndex == 0) {
         return manager_.getSurface(rowIndex).getName();
      } else if (columnIndex == 1) {
         return manager_.getSurface(rowIndex).getXYDevice();
      } else if (columnIndex == 2) {
         return manager_.getSurface(rowIndex).getZDevice();
      } else if (columnIndex == 3) {
         return surface.getXYPadding();
      } else {
         int numPositions = surface.getNumPositions();
         return numPositions == -1 ? "" : numPositions;
      }
   }
   
}
