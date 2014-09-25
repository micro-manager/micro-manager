/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package surfacesandregions;

import java.util.LinkedList;

/**
 *
 * @author Henry
 */
public class SurfaceManager extends SurfaceOrRegionManager {

//   private LinkedList<InterpolatorListener> interpolatorListeners_ = new LinkedList<InterpolatorListener>();
//
//   public void updateInterpolatorListeners(Interpolation interp) {
//      for (InterpolatorListener il : interpolatorListeners_) {
//         il.interpolationUpdated(interp);
//      }
//   }
//
//   public void removeInterpolatorListener(InterpolatorListener il) {
//      interpolatorListeners_.remove(il);
//   }
//
//   public void addInterpolatorListener(InterpolatorListener il) {
//      interpolatorListeners_.add(il);
//   }

   public SurfaceInterpolator getCurrentSurface() {
      if (selectedIndex_ == -1) {
         return null;
      }
      return (SurfaceInterpolator) suregions_.get(suregionNames_.get(selectedIndex_));
   }
   
   public void addNewSurface(String name) {
      suregions_.put(name,new SurfaceInterpolator(this));
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
