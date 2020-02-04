package org.micromanager.magellan.api;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import org.micromanager.magellan.acq.MagellanAcquisitionsManager;
import org.micromanager.magellan.gui.GUI;
import org.micromanager.magellan.surfacesandregions.MultiPosGrid;
import org.micromanager.magellan.surfacesandregions.SurfaceGridManager;

/**
 *
 * @author henrypinkard
 */
public class MagellanAPI {

   public static MagellanAPI instance_;
   private static GUI gui_;

   public MagellanAPI() {
      instance_ = this;
   }

   public MagellanAcquisitionAPI createAcquisition() {
      return MagellanAcquisitionsManager.getInstance().addNew();
   }
   
   public void deleteAcquisition(int index) {
      MagellanAcquisitionsManager.getInstance().remove(index);
   }

   public List<MagellanAcquisitionAPI> getAcquisitions() {
      ArrayList<MagellanAcquisitionAPI> acqList = new ArrayList<MagellanAcquisitionAPI>();
      for (int i = 0; i < MagellanAcquisitionsManager.getInstance().getNumberOfAcquisitions(); i++) {
         acqList.add(MagellanAcquisitionsManager.getInstance().getAcquisition(i));
      }
      return acqList;
   }
   
   public void createGrid(String name, int nRows, int nCols, double centerX, double centerY) {
      MultiPosGrid grid = SurfaceGridManager.getInstance().addNewGrid(nRows, nCols, new Point2D.Double(centerX, centerY));
      SurfaceGridManager.getInstance().rename(grid, name);
   }
   
   public void deleteAllGridsAndSurfaces() {
      SurfaceGridManager.getInstance().deleteAll();
   }
   
   

}
