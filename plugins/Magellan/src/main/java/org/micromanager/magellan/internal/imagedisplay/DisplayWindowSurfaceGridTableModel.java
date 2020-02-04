/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.imagedisplay;

import com.google.common.eventbus.Subscribe;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.TreeMap;
import javax.swing.table.AbstractTableModel;
import org.micromanager.magellan.internal.imagedisplay.MagellanDisplayController;
import org.micromanager.magellan.internal.imagedisplay.events.DisplayClosingEvent;
import org.micromanager.magellan.internal.misc.Log;
import org.micromanager.magellan.internal.misc.NumberUtils;
import org.micromanager.magellan.internal.surfacesandregions.MultiPosGrid;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceGridListener;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceGridManager;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceInterpolator;
import org.micromanager.magellan.internal.surfacesandregions.XYFootprint;

/**
 *
 * @author henrypinkard
 */
 class DisplayWindowSurfaceGridTableModel extends AbstractTableModel implements SurfaceGridListener {

   private final String[] COLUMNS = {"Show", "Type", "Name"};
   //maybe, "Z Device"
   private volatile HashMap<XYFootprint, Boolean> showSurfaceOrGridMap = new HashMap<XYFootprint, Boolean>();

   private SurfaceGridManager manager_ = SurfaceGridManager.getInstance();
   private MagellanDisplayController display_;
   
   public DisplayWindowSurfaceGridTableModel(MagellanDisplayController disp) {
      display_ = disp;
      display_.registerForEvents(this);
      manager_.registerSurfaceGridListener(this);
      for (int i = 0; i < manager_.getNumberOfGrids() + manager_.getNumberOfSurfaces(); i++) {
         showSurfaceOrGridMap.put(manager_.getSurfaceOrGrid(i), Boolean.TRUE);
      }
   }
   
   @Subscribe
   public void onDisplayClose(DisplayClosingEvent e) {
      manager_.unregisterSurfaceGridListener(this);
      display_.unregisterForEvents(this);
      display_ = null;
   }

   public boolean isSurfaceOrGridVisible(int index) {
      return showSurfaceOrGridMap.get(manager_.getSurfaceOrGrid(index));
   }
   
   @Override
   public int getRowCount() {
      return manager_.getNumberOfSurfaces() + manager_.getNumberOfGrids();
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
      if (colIndex == 0 || colIndex == 2) {
         return true;
      } else if (colIndex == 3 && manager_.getSurfaceOrGrid(rowIndex) instanceof SurfaceInterpolator) {
         return true; // only surfaces have XY padding
      }
      return false;
   }

   @Override
   public void setValueAt(Object value, int row, int col) {
      if (col == 0) {
         showSurfaceOrGridMap.put(manager_.getSurfaceOrGrid(row), !showSurfaceOrGridMap.get(manager_.getSurfaceOrGrid(row)));
         //redraw to refelect change in visibility
         display_.redrawOverlay();
      } else if (col == 2) {
         try {
            manager_.rename(row, (String) value);
         } catch (Exception ex) {
            Log.log("Name already taken by existing Surface/Grid", true);
         }
      }
   }

   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {

      XYFootprint surfaceOrGird = manager_.getSurfaceOrGrid(rowIndex);
      if (columnIndex == 0) {
         return showSurfaceOrGridMap.get(surfaceOrGird);
      } else if (columnIndex == 1) {
         return manager_.getSurfaceOrGrid(rowIndex) instanceof SurfaceInterpolator ? "Surface" : "Grid";
      } else {
         return manager_.getSurfaceOrGrid(rowIndex).getName();
      }
   }

   @Override
   public Class getColumnClass(int columnIndex) {
      if (columnIndex == 0) {
         return Boolean.class;
      } else if (columnIndex == 1) {
         return String.class;
      } else {
         return String.class;
      }
   }

   @Override
   public void SurfaceOrGridChanged(XYFootprint f) {
      this.fireTableDataChanged();
   }

   @Override
   public void SurfaceOrGridDeleted(XYFootprint f) {
      showSurfaceOrGridMap.remove(f);
      this.fireTableDataChanged();
   }

   @Override
   public void SurfaceOrGridCreated(XYFootprint f) {
      showSurfaceOrGridMap.put(f, Boolean.TRUE);
      this.fireTableDataChanged();
   }

   @Override
   public void SurfaceOrGridRenamed(XYFootprint f) {
      this.fireTableDataChanged();
   }

   @Override
   public void SurfaceInterpolationUpdated(SurfaceInterpolator s) {
      //nothing to do
   }

   public SurfaceInterpolator addNewSurface() {
      return manager_.addNewSurface();
   }

   public MultiPosGrid newGrid(int rows, int cols, Point2D.Double center) {
      return manager_.addNewGrid(rows, cols, center);
   }
}
