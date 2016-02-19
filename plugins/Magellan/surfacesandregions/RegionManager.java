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
package surfacesandregions;

import imagedisplay.DisplayPlus;
import java.util.ArrayList;
import propsandcovariants.CovariantPairingsManager;

/**
 *
 * class to keep track of the surfaces/regions
 */
public class RegionManager {

   private ArrayList<MultiPosRegion> regions_ = new ArrayList<MultiPosRegion>();
   private ArrayList<SurfaceRegionComboBoxModel> comboBoxModels_ = new ArrayList<SurfaceRegionComboBoxModel>();
   private RegionTableModel tableModel_;
   private static RegionManager singletonInstance_;
   
   public RegionManager() {
      singletonInstance_ = this;
   }
   
   public static RegionManager getInstance() {
      return singletonInstance_;
   }
   
   public int getIndex(SurfaceInterpolator surface) {
      return regions_.indexOf(surface);
   }
   
   public MultiPosRegion getRegion(int index) {
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
   
   public void addNewRegion(MultiPosRegion region) {
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
         for (MultiPosRegion region : regions_) {
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

   /**
    * redraw overlay for all displays showing this surface
    * @param region 
    */
   public void drawRegionOverlay(MultiPosRegion region) {
      DisplayPlus.redrawRegionOverlay(region);
   }
   
   public void updateRegionTableAndCombos() {
      for (SurfaceRegionComboBoxModel m : comboBoxModels_) {
         m.update();
      }
      tableModel_.fireTableDataChanged();
      CovariantPairingsManager.getInstance().surfaceorRegionNameChanged();
   }
}
