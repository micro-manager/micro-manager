/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package surfacesandregions;

import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.event.ListDataListener;

/**
 *
 * @author Henry
 */
class RegionComboBoxModel extends DefaultComboBoxModel {

   private RegionManager manager_;
   private int selectedIndex_ = -1;
   
   public RegionComboBoxModel(RegionManager manager ) {
      manager_ = manager;
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
      for (int i = 0; i < manager_.getNumberOfRegions(); i++ ) {
         if (manager_.getRegion(i).getName().equals(anItem)) {
            selectedIndex_ = i;
            break;
         }
      }
   }

   @Override
   public Object getSelectedItem() {
      return selectedIndex_ == -1 ? null : manager_.getRegion(selectedIndex_).getName();
   }

   @Override
   public int getSize() {
      return manager_.getNumberOfRegions();
   }

   @Override
   public String getElementAt(int index) {
      return manager_.getRegion(index).getName();
   }

   public void update() {
      super.fireContentsChanged(manager_, -1, -1);
   }
}
