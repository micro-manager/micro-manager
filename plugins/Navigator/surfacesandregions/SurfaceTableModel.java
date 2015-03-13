/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package surfacesandregions;

import javax.swing.table.AbstractTableModel;

/**
 *
 * @author Henry
 */
class SurfaceTableModel extends AbstractTableModel  {

   private final String[] COLUMNS = {"Name", "XY padding (µm)", "Z padding (µm)", "# Positions", "Width (µm)", "Height (µm)"};
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
      if (colIndex == 0 || colIndex == 1|| colIndex == 2) {
         return true;
      }
      return false;
   }
   
   @Override   
   public void setValueAt(Object value, int row, int col) {
      if (col == 0) {
         manager_.getSurface(row).rename((String) value);
      } else if (col == 1) {
         manager_.getSurface(row).setXYPadding(Double.parseDouble((String) value));
      } else if (col == 2) {
         manager_.getSurface(row).setZPadding(Double.parseDouble((String) value));         
      }
   }
   
   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {
      SurfaceInterpolator surface = manager_.getSurface(rowIndex);
      if (columnIndex == 0) {
         return manager_.getSurface(rowIndex).getName();
      } else if (columnIndex == 1) {
         return surface.getXYPadding();
      } else if (columnIndex == 2) {
         return surface.getZPadding();
      } else if (columnIndex == 3) {
         int numPositions = surface.getNumPositions();
         return numPositions == -1 ? "" : numPositions;
      } else if (columnIndex == 4) {
         double width = surface.getWidth_um();         
         return width == 0 ? "" : width;
      } else {
         double height =  surface.getHeight_um();
         return height == 0 ? "" : height;
      }
   }
   
}
