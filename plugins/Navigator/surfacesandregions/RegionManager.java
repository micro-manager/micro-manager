package surfacesandregions;

import imagedisplay.DisplayPlus;
import java.util.ArrayList;

/**
 *
 * class to keep track of the surfaces/regions
 */
public class RegionManager {

   private ArrayList<MultiPosGrid> regions_ = new ArrayList<MultiPosGrid>();
   private ArrayList<SurfaceRegionComboBoxModel> comboBoxModels_ = new ArrayList<SurfaceRegionComboBoxModel>();
   private RegionTableModel tableModel_;
   private static RegionManager singletonInstance_;
   
   public RegionManager() {
      singletonInstance_ = this;
   }
   
   public static RegionManager getInstance() {
      return singletonInstance_;
   }
   
   public MultiPosGrid getRegion(int index) {
      if (index < 0 || index >= regions_.size()) {
         return null;
      }
      return regions_.get(index);
   }

   public RegionTableModel createGridTableModel() {
      tableModel_ = new RegionTableModel(this);
      return tableModel_;
   }

   public void addToModelList(SurfaceRegionComboBoxModel model) {
      comboBoxModels_.add(model);
   }
   
   public void removeFromModelList(SurfaceRegionComboBoxModel model) {
      comboBoxModels_.remove(model);
   }

   public void deleteAll() {
      regions_.clear();
      for (SurfaceRegionComboBoxModel combo : comboBoxModels_) {
         combo.setSelectedIndex(-1);
      }
      updateRegionTableAndCombos();
   }
   
   public void delete(int index) {
      regions_.remove(index);
      for (SurfaceRegionComboBoxModel combo : comboBoxModels_) {
         if (index == 0 && regions_.isEmpty()) {
            combo.setSelectedIndex(-1); //set selectionto null cause no surfaces left
         } else if (combo.getSelectedIndex() == 0) {
            //do noting, so selection stays at top of list
         } else if (index <= combo.getSelectedIndex()) {
            combo.setSelectedIndex(combo.getSelectedIndex() - 1); //decrment selection so combo stays on same object
         }
      }
      updateRegionTableAndCombos();
   }
   
   public void addNewRegion(MultiPosGrid region) {
      regions_.add(region);
      updateRegionTableAndCombos();
   }
   
   public int getNumberOfRegions() {
      return regions_.size();
   }
  
   public String getNewName() {
      String base = "New Region";
      int index = 1;
      String potentialName = base + " " + index;
      while (true) {
         boolean uniqueName = true;
         for (MultiPosGrid region : regions_) {
            if (region.getName().equals(potentialName)) {
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

   public void drawRegionOverlay(MultiPosGrid region) {
      DisplayPlus.redrawRegionOverlay(region); //redraw overlay for all displays showing this surface
   }
   
   public void updateRegionTableAndCombos() {
      for (SurfaceRegionComboBoxModel m : comboBoxModels_) {
         m.update();
      }
      tableModel_.fireTableDataChanged();
   }
}