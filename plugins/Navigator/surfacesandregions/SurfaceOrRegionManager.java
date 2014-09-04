package surfacesandregions;

import java.awt.Component;
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
   
   protected TreeMap<String, Object> suregions_ = new TreeMap<String, Object>();
   protected String selectedItem_ = null;
   protected LinkedList<ListDataListener> dataListeners_ = new LinkedList<ListDataListener>();

   public void renameCurrentSuregion(String newName) {
      if (selectedItem_ == null) {
         return;
      }
      suregions_.put(newName, suregions_.remove(selectedItem_));
      selectedItem_ = newName;
      updateListeners();
   }
   
   public void renameSuregion(int index, String newName) {
      boolean renameSelected = getElementAt(index).equals(selectedItem_);
      suregions_.put(newName, suregions_.remove(getElementAt(index)));
      if (renameSelected) {
         selectedItem_ = newName;
      }
      updateListeners();
   }
   
   public boolean containsSuregionNamed(String name) {
      return suregions_.keySet().contains(name);
   }
   
   public void removeSuregion(String name) {
      suregions_.remove(name);
      updateListeners();
   }
   
   public Object getCurrentSuregion() {
      return selectedItem_ == null ? null : suregions_.get(selectedItem_);
   }
   
   public abstract String getNewName();
   
   @Override
   public void setSelectedItem(Object anItem) {
      selectedItem_ = (String) anItem;
      updateListeners();  
   }

   @Override
   public String getSelectedItem() {
      return selectedItem_;
   }

   @Override
   public int getSize() {
      return suregions_.keySet().size();
   }

   @Override
   public String getElementAt(int index) {
      LinkedList<String> keyList = new LinkedList<String>();
      keyList.addAll(suregions_.keySet());
      return keyList.get(index);
   } 
   
   public void updateListeners() {
      for (ListDataListener l : dataListeners_ ) {
         l.contentsChanged(new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, 0, suregions_.keySet().size()));
      }
   }

   @Override
   //subclass specific implementations of this behavior
   public void addElement(String item) {
   }

   @Override
   public void removeElement(Object obj) {
      suregions_.remove(obj);
   }

   @Override
   public void insertElementAt(String item, int index) {
      
   }

   @Override
   public void removeElementAt(int index) {
      suregions_.remove(getElementAt(index));
   }

   @Override
   public void addListDataListener(ListDataListener l) {
      dataListeners_.add(l);
   }

   @Override
   public void removeListDataListener(ListDataListener l) {
      dataListeners_.remove(l);
   }
}