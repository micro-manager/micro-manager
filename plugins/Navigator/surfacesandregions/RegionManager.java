package surfacesandregions;

import java.util.LinkedList;
import java.util.TreeMap;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.event.ListDataListener;

/**
 *
 * class to keep track of the surfaces/regions
 */
public class RegionManager extends SurfaceOrRegionManager {

   public MultiPosRegion getCurrentRegion() {
      return (MultiPosRegion) super.getCurrentSuregion();
   }
   
   public MultiPosRegion getRegion(int index) {
      return (MultiPosRegion) suregions_.get(super.getElementAt(index));
   }

   public void deleteRegion(String name) {
      boolean needNewSelection = name.equals(selectedItem_);
      suregions_.remove(name);
      if (needNewSelection) {
         if (suregions_.keySet().size() > 0) {
            selectedItem_ = suregions_.keySet().iterator().next();
         } else {
            selectedItem_ = null;
         }
      }
      super.updateListeners();
   }
   
   public void deleteAll() {
      suregions_.clear();
      selectedItem_ = null;
      super.updateListeners();
   }
   
   public void addNewRegion(String name, MultiPosRegion region) {
      suregions_.put(name,region);
      selectedItem_ = name;
      super.updateListeners();
   }
   
   @Override
   public String getNewName() {
      String base = "New Grid";
      int index = 1;
      while (true) {
         String potentialName = base + " " + index;
         if (!suregions_.keySet().contains(potentialName)) {
            return potentialName;
         }
         index++;
      }
   }
}