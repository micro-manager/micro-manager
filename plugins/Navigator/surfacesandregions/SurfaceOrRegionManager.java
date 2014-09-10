package surfacesandregions;

import java.awt.Component;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.TreeMap;
import javax.swing.DefaultComboBoxModel;
import javax.swing.MutableComboBoxModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

/**
 *
 * class to keep track of the surfaces/regions
 */
public abstract class SurfaceOrRegionManager implements MutableComboBoxModel<String> {
   
   protected ArrayList<String> suregionNames_ = new ArrayList<String>();
   protected TreeMap<String, Object> suregions_ = new TreeMap<String, Object>();
   protected int selectedIndex_ = -1;
   protected LinkedList<ListDataListener> dataListeners_ = new LinkedList<ListDataListener>();

   public void renameCurrentSuregion(String newName) {
      if (selectedIndex_ == -1) {
         return;
      }
      renameSuregion(selectedIndex_, newName);
   }
   
   public void renameSuregion(int index, String newName) {
      if (index >= suregionNames_.size()) {
         return;
      }
      suregionNames_.add(index, newName);
      String oldName = suregionNames_.remove(selectedIndex_ + 1);
      suregions_.put(newName, suregions_.remove(oldName));
      updateListeners();
   }
   
   public boolean containsSuregionNamed(String name) {
      return suregionNames_.contains(name);
   }
   
   public void removeSuregion(String name) {
      suregions_.remove(name);
      suregionNames_.remove(name);
      updateListeners();
   }
   
   public abstract String getNewName();

   public void deleteAll() {
      suregions_.clear();
      suregionNames_.clear();
      selectedIndex_ = -1;
      updateListeners();
   }
   
   public void delete(int index) {
      boolean needNewSelection = selectedIndex_ == index;
      suregions_.remove(suregionNames_.get(index));
      suregionNames_.remove(index);
      if (needNewSelection) {
         if (suregionNames_.size() == 0) {
            selectedIndex_ = -1;
         } else if (selectedIndex_ >= suregionNames_.size()) {
            selectedIndex_--;
         }
      }
      updateListeners();
   }

   @Override
   public void setSelectedItem(Object anItem) {
      selectedIndex_ = suregionNames_.indexOf(anItem);
      updateListeners();  
   }

   @Override
   public String getSelectedItem() {
      if (selectedIndex_ == -1) {
         return null;
      }
      return suregionNames_.get(selectedIndex_);
   }

   @Override
   public int getSize() {
      return suregionNames_.size();
   }

   @Override
   public String getElementAt(int index) {
      return suregionNames_.get(index);
   } 
   
   public void updateListeners() {
      for (ListDataListener l : dataListeners_ ) {
         l.contentsChanged(new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, 0, suregions_.keySet().size()));
      }
   }

   @Override
   public void removeElement(Object obj) {
      suregions_.remove(obj);
      suregionNames_.remove(obj);
   }
   
   @Override
   public void removeElementAt(int index) {
      suregions_.remove(suregionNames_.get(index));
      suregionNames_.remove(index);
   }

   @Override
   public void addListDataListener(ListDataListener l) {
      dataListeners_.add(l);
   }

   @Override
   public void removeListDataListener(ListDataListener l) {
      dataListeners_.remove(l);
   }
   
      @Override
   public void addElement(String item) {
      throw new UnsupportedOperationException("Not supported");
   }

   @Override
   public void insertElementAt(String item, int index) {
      throw new UnsupportedOperationException("Not supported");
   }
}