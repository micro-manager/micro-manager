package org.micromanager.magellan.api;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import org.micromanager.acqj.api.Acquisition;
import org.micromanager.magellan.internal.magellanacq.MagellanAcquisitionsManager;
import org.micromanager.magellan.internal.gui.GUI;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.magellan.internal.surfacesandregions.MultiPosGrid;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceGridManager;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceInterpolator;

/**
 * Master Magellan API
 * 
 * @author henrypinkard
 */
public class MagellanAPI {

   MagellanAcquisitionsManager acqManager_;

   public MagellanAPI() {
      //make sure Magellan GUI showing
      (new Magellan()).onPluginSelected();
      acqManager_ = MagellanAcquisitionsManager.getInstance();
   }

   public Acquisition createAcquisition(int index) {
      return acqManager_.createAcquisition(index);
   }
   
   public MagellanAcquisitionSettingsAPI getAcquisitionSettings(int index) {
      return acqManager_.getAcquisitionSettings(index);
   }
   
   public void createAcquisitionSettings() {
      acqManager_.addNew();
   }
   
   public void removeAcquisitionSettings(int index) {
      acqManager_.remove(index);
   }
   
   public MultiPosGrid createGrid(String name, int nRows, int nCols, double centerX, double centerY) {
      MultiPosGrid grid = SurfaceGridManager.getInstance().addNewGrid(nRows, nCols, new Point2D.Double(centerX, centerY));
      SurfaceGridManager.getInstance().rename(grid, name);
      return grid;
   }

   public MultiPosGrid getGrid(String name) {
      return SurfaceGridManager.getInstance().getGridNamed(name);
   }

   public SurfaceInterpolator createSurface(String name) {
      SurfaceInterpolator surf = SurfaceGridManager.getInstance().addNewSurface();
      SurfaceGridManager.getInstance().rename(surf, name);
      return surf;
   }

   public SurfaceInterpolator getSurface(String name) {
      return SurfaceGridManager.getInstance().getSurfaceNamed(name);
   }


   public void deleteAllGridsAndSurfaces() {
      SurfaceGridManager.getInstance().deleteAll();
   }
   


}
