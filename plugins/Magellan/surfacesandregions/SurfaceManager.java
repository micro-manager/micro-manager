package surfacesandregions;

import propsandcovariants.SurfaceData;
import clojure.test.junit$suite_attrs;
import imagedisplay.DisplayPlus;
import java.util.ArrayList;
import java.util.LinkedList;
import org.micromanager.MMStudio;
import propsandcovariants.CovariantPairingsManager;

/**
 *
 * @author Henry
 */
public class SurfaceManager {
  
   private ArrayList<SurfaceInterpolator> surfaces_ = new ArrayList<SurfaceInterpolator>();
   private ArrayList<SurfaceRegionComboBoxModel> comboBoxModels_ = new ArrayList<SurfaceRegionComboBoxModel>();
   private SurfaceTableModel tableModel_;
   private static SurfaceManager singletonInstance_;
   
   public SurfaceManager() {
      singletonInstance_ = this;
   }
   
   public static SurfaceManager getInstance() {
      return singletonInstance_;
   }
   
   public SurfaceInterpolator getSurfaceNamed(String name) {
      for (SurfaceInterpolator s : surfaces_) {
         if (s.getName().equals(name)) {
            return s;
         }
      }
      return null;
   }
   
   public int getIndex(SurfaceInterpolator surface) {
      return surfaces_.indexOf(surface);
   }
   
   public SurfaceInterpolator getSurface(int index) {
      if (index < 0 || index>= surfaces_.size() ) {
         return null;
      } 
      return surfaces_.get(index);
   }
   
   public SurfaceTableModel createSurfaceTableModel() {
      tableModel_ = new SurfaceTableModel(this);
      return tableModel_;
   }
   
   public void addToModelList(SurfaceRegionComboBoxModel model) {
      comboBoxModels_.add(model);
   }

   public void removeFromModelList(SurfaceRegionComboBoxModel model) {
      comboBoxModels_.remove(model);
   }

   public void deleteAll() {
      for (SurfaceInterpolator s: surfaces_) {
         s.shutdown();
      }
      surfaces_.clear();
      for (SurfaceRegionComboBoxModel combo : comboBoxModels_) {
         combo.setSelectedIndex(-1);
      }
      updateSurfaceTableAndCombos();
   }
   
   public void delete(int index) {
      SurfaceInterpolator s = surfaces_.remove(index);
      s.shutdown();
      for (SurfaceRegionComboBoxModel combo : comboBoxModels_) {
         if (index == 0 && surfaces_.isEmpty()) {
            combo.setSelectedIndex(-1); //set selectionto null cause no surfaces left
         } else if (combo.getSelectedIndex() == 0) {
            //do noting, so selection stays at top of list
         } else if (index <= combo.getSelectedIndex()) {
            combo.setSelectedIndex(combo.getSelectedIndex() - 1); //decrment selection so combo stays on same object
         }
      }
      updateSurfaceTableAndCombos();
   }
   
   public void addNewSurface() {
//      surfaces_.add(new SurfaceInterpolatorSibson(this));
      surfaces_.add(new SurfaceInterpolatorSimple(this, MMStudio.getInstance().getCore().getXYStageDevice(), MMStudio.getInstance().getCore().getFocusDevice()));
      updateSurfaceTableAndCombos();
   }
   
   public int getNumberOfSurfaces() {
      return surfaces_.size();
   }
  
   public String getNewName() {
      String base = "New Surface";
      int index = 1;
      String potentialName = base + " " + index;
      while (true) {
         boolean uniqueName = true;
         for (SurfaceInterpolator surface : surfaces_) {
            if (surface.getName().equals(potentialName)) {
               index++;
               potentialName = base + " " + index;
               uniqueName = false;
            }
         }
         if (uniqueName) {
            break;
         }
      }
      return potentialName;
   }

   public void drawSurfaceOverlay(SurfaceInterpolator surface) {
      DisplayPlus.redrawSurfaceOverlay(surface); //redraw overlay for all displays showing this surface
   }
   
   public void updateSurfaceTableAndCombos() {
      for (SurfaceRegionComboBoxModel m : comboBoxModels_) {
         m.update();
      }
      tableModel_.fireTableDataChanged();
      CovariantPairingsManager.getInstance().surfaceorRegionNameChanged();
   }

   /**
    * Generate surface data for all available surfaces
    * @return 
    */
   public ArrayList<SurfaceData> getSurfaceData() {
      ArrayList<SurfaceData> stats = new ArrayList<SurfaceData>();
      for (SurfaceInterpolator surface : surfaces_) {
         stats.addAll(surface.getData());
      }
      return stats;
   }

    void surfaceRenamed() {
        //update covariants that use this surface
        CovariantPairingsManager.getInstance().updatePairingNames();
    }
}
