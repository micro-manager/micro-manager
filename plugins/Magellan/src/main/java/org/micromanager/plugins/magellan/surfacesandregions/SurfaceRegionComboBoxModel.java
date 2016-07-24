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

package org.micromanager.plugins.magellan.surfacesandregions;

import javax.swing.DefaultComboBoxModel;

/**
 *
 * @author Henry
 */
public class SurfaceRegionComboBoxModel extends DefaultComboBoxModel {
   
   private RegionManager rManager_;
   private SurfaceManager sManager_;
   private int selectedIndex_ = -1;

   public SurfaceRegionComboBoxModel(SurfaceManager sManager, RegionManager rManager)  {
      sManager_ = sManager;
      rManager_ = rManager;
   }

   public int getSelectedIndex() {
      return selectedIndex_;
   }

   public void setSelectedIndex(int selectedIndex) {
      selectedIndex_ = selectedIndex;
   }
   
   @Override
   public void setSelectedItem(Object anItem) {
     selectedIndex_ = -1;
     int offset = 0;
      if (rManager_ != null) {
         offset = rManager_.getNumberOfRegions();
         for (int i = 0; i < rManager_.getNumberOfRegions(); i++) {
            if (rManager_.getRegion(i).equals(anItem)) {
               selectedIndex_ = i;
               return;
            }
         }
      }
      if (sManager_ != null) {
         for (int i = 0; i < sManager_.getNumberOfSurfaces(); i++) {
            if (sManager_.getSurface(i).equals(anItem)) {
               selectedIndex_ = i + offset;
               return;
            }
         }
      }
   }

   @Override
   public Object getSelectedItem() {
      return getElementAt(selectedIndex_);
   }

   @Override
   public int getSize() {
      return (rManager_ != null ? rManager_.getNumberOfRegions() : 0) + (sManager_ != null ? sManager_.getNumberOfSurfaces() : 0); 
   }

   @Override
   public Object getElementAt(int index) {
      if (index == -1) {
         return null;
      }
      if (rManager_ == null) {
         //surfaces only
         return sManager_.getSurface(index);
      } else if (sManager_ == null) {
         return rManager_.getRegion(index);
      } else {
         //regions first, then surfaces
         if (index >= rManager_.getNumberOfRegions()) {
            return sManager_.getSurface(index - rManager_.getNumberOfRegions());
         } else {
            return rManager_.getRegion(index);
         }
      }
   }

   public void update() {
      if (sManager_ != null) {
         super.fireContentsChanged(sManager_, -1, -1);
      }
      if (rManager_ != null) {
         super.fireContentsChanged(rManager_, -1, -1);
      }
   }
   
}
