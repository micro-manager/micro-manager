/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package surfacesandregions;

/**
 *
 * @author Henry
 */
public class SurfaceManager extends SurfaceOrRegionManager {
   
   public SurfaceInterpolator getCurrentSurface() {
       if (selectedIndex_ == -1) {
         return null;
      }
      return (SurfaceInterpolator) suregions_.get(suregionNames_.get(selectedIndex_));
   }
   
   public void addNewSurface(String name) {
      suregions_.put(name,new SurfaceInterpolator());
      suregionNames_.add(name);
      selectedIndex_ = suregionNames_.size() - 1;
      super.updateListeners();
   }
   
   public SurfaceInterpolator getSurface(int index) {
      return (SurfaceInterpolator) suregions_.get(super.getElementAt(index));
   }
  
   @Override
   public String getNewName() {
      String base = "New Surface";
      int index = 1;
      while (true) {
         String potentialName = base  + " " + index;
         if (!suregions_.keySet().contains(potentialName)) {
            return potentialName;
         }
         index ++;
      }
   }
   
}
