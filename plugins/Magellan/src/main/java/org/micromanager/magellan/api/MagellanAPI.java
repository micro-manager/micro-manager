package org.micromanager.magellan.api;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import org.micromanager.acqj.api.Acquisition;
import org.micromanager.magellan.internal.magellanacq.MagellanAcquisitionsManager;
import org.micromanager.magellan.internal.gui.GUI;
import org.micromanager.magellan.internal.surfacesandregions.MultiPosGrid;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceGridManager;

/**
 * Master Magellan API
 * 
 * @author henrypinkard
 */
public class MagellanAPI {

   public Acquisition createAcquisition(int index) {
      return MagellanAcquisitionsManager.getInstance().createAcquisition(index);
   }
   
   public MagellanAcquisitionSettingsAPI getAcquisitionSettings(int index) {
      return MagellanAcquisitionsManager.getInstance().getAcquisitionSettings(index);
   }
   
   public void createAcquisitionSettings() {
      MagellanAcquisitionsManager.getInstance().addNew();
   }
   
   public void removeAcquisitionSettings(int index) {
      MagellanAcquisitionsManager.getInstance().remove(index);
   }
   
   public void createGrid(String name, int nRows, int nCols, double centerX, double centerY) {
      MultiPosGrid grid = SurfaceGridManager.getInstance().addNewGrid(nRows, nCols, new Point2D.Double(centerX, centerY));
      SurfaceGridManager.getInstance().rename(grid, name);
   }
   
   public void deleteAllGridsAndSurfaces() {
      SurfaceGridManager.getInstance().deleteAll();
   }
   
   

}
