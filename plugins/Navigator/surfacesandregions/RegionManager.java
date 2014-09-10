package surfacesandregions;

/**
 *
 * class to keep track of the surfaces/regions
 */
public class RegionManager extends SurfaceOrRegionManager {

   public MultiPosRegion getCurrentRegion() {
      if (selectedIndex_ == -1) {
         return null;
      }
      return (MultiPosRegion) suregions_.get(suregionNames_.get(selectedIndex_));
   }
   
   public MultiPosRegion getRegion(int index) {
      return (MultiPosRegion) suregions_.get(super.getElementAt(index));
   }
   
   public void addNewRegion(String name, MultiPosRegion region) {
      suregions_.put(name,region);
      suregionNames_.add(name);
      selectedIndex_ = suregionNames_.size() - 1;
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