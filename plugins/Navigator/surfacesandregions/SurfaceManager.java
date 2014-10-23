package surfacesandregions;

import clojure.test.junit$suite_attrs;
import imagedisplay.DisplayPlus;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 *
 * @author Henry
 */
public class SurfaceManager {
  
   private ArrayList<SurfaceInterpolator> surfaces_ = new ArrayList<SurfaceInterpolator>();
   private ArrayList<SurfaceComboBoxModel> comboBoxModels_ = new ArrayList<SurfaceComboBoxModel>();
   private SurfaceTableModel tableModel_;
   private static SurfaceManager singletonInstance_;
   
   public SurfaceManager() {
      singletonInstance_ = this;
   }
   
   public static SurfaceManager getInstance() {
      return singletonInstance_;
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
   
   public SurfaceComboBoxModel createSurfaceComboBoxModel() {
      SurfaceComboBoxModel model = new SurfaceComboBoxModel(this);
      comboBoxModels_.add(model);
      return model;
   }
   
   public void deleteAll() {
      for (SurfaceInterpolator s: surfaces_) {
         s.shutdown();
      }
      surfaces_.clear();
      for (SurfaceComboBoxModel combo : comboBoxModels_) {
         combo.setSelectedIndex(-1);
      }
      updateSurfaceTableAndCombos();
   }
   
   public void delete(int index) {
      SurfaceInterpolator s = surfaces_.remove(index);
      s.shutdown();
      for (SurfaceComboBoxModel combo : comboBoxModels_) {
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
      surfaces_.add(new SurfaceInterpolatorSimple(this));
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
      for (SurfaceComboBoxModel m : comboBoxModels_) {
         m.update();
      }
      tableModel_.fireTableDataChanged();
   }
}
