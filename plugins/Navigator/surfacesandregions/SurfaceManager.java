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

   
   public SurfaceInterpolater getCurrentSurface() {
      return (SurfaceInterpolater) super.getCurrentSuregion();
   }
   
   public void addNewSurface(String name) {
      suregions_.put(name, new SurfaceInterpolater());
      selectedItem_ = name;
      super.updateListeners();
   }
   
//   public SurfaceInterpolater getSurface(int index) {
//      return (SurfaceInterpolater) suregions_.get(super.getElementAt(index));
//   }

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
