/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import gui.GUI;
import java.util.ArrayList;

/**
 *
 * @author Henry
 */
public class MultipleAcquisitionManager {
   
   private ArrayList<FixedAreaAcquisitionSettings> acquisitions_ = new ArrayList<FixedAreaAcquisitionSettings>();
   private GUI gui_;
   
   public MultipleAcquisitionManager(GUI gui) {
      gui_ = gui;
      acquisitions_.add(new FixedAreaAcquisitionSettings());
   }
      
   public FixedAreaAcquisitionSettings getAcquisition(int index) {
      return acquisitions_.get(index);
   }
   
   public int getSize() {
      return acquisitions_.size();
   }
   
   public String getAcquisitionName(int index) {
      return acquisitions_.get(index).name_;
   }
   
   public boolean moveUp(int index) {
      if (index > 0) {
         acquisitions_.add(index-1, acquisitions_.remove(index));
         return true;
      }
      return false;
   }
   
   public boolean moveDown(int index) {      
      if (index < acquisitions_.size() - 1) {
         acquisitions_.add(index+1, acquisitions_.remove(index));
         return true;
      }
      return false;
   }
   
   public void addNew() {
      acquisitions_.add(new FixedAreaAcquisitionSettings());
   }
   
   public void remove(int index) {
      if (index != -1 && acquisitions_.size() > 1) {
         acquisitions_.remove(index);
         if (index == acquisitions_.size()) {
            index--;
         }
      }
   }
   
}
