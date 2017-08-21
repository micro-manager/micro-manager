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
   private Object selectedItem_ = null;

   public SurfaceRegionComboBoxModel(SurfaceManager sManager, RegionManager rManager)  {
      sManager_ = sManager;
      rManager_ = rManager;
   }

   @Override
   public Object getSelectedItem() {
      return selectedItem_;
   }


   @Override
   public void setSelectedItem(Object anItem) {
     selectedItem_ = anItem;
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
      if (rManager_ != null && index < rManager_.getNumberOfRegions()) {
         return rManager_.getRegion(index);         
      } else if (rManager_ == null) {
         return sManager_.getSurface(index);
      } else {
         return sManager_.getSurface(index - rManager_.getNumberOfRegions());
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
