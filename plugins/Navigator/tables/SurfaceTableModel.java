/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tables;

import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.table.AbstractTableModel;
import surfacesandregions.MultiPosRegion;
import surfacesandregions.RegionManager;
import surfacesandregions.SurfaceInterpolator;
import surfacesandregions.SurfaceManager;

/**
 *
 * @author Henry
 */
public class SurfaceTableModel extends AbstractTableModel implements ListDataListener {

   private final String[] COLUMNS = {"Name", "XY padding (µm)", "Z padding (µm)", "# Positions", "Width (µm)", "Height (µm)"};
   private SurfaceManager manager_;
   
   public SurfaceTableModel(SurfaceManager manager) {
      manager_ = manager;
      manager.addListDataListener(this);
   }
   
   @Override
   public int getRowCount() {
      return manager_.getSize();
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
         manager_.renameSuregion(row, (String) value);
      } else if (col == 1) {
         manager_.getSurface(row).setXYPadding(Double.parseDouble((String) value));
         manager_.updateListeners();
      } else if (col == 2) {
         manager_.getSurface(row).setZPadding(Double.parseDouble((String) value));
         manager_.updateListeners();
      }
   }
   
   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {
      SurfaceInterpolator surface = manager_.getSurface(rowIndex);
      if (columnIndex == 0) {
         return manager_.getElementAt(rowIndex);
      } else if (columnIndex == 1) {
         return surface.getXYPadding();
      } else if (columnIndex == 2) {
         return surface.getZPadding();
      } else if (columnIndex == 3) {
         return surface.getNumPositions();
      } else if (columnIndex == 4) {
         return surface.getWidth_um();
      } else {
         return surface.getHeight_um();
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
