/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.lightsheetcontrol;

    import java.util.ArrayList;

/**
 *
 * @author kthorn
 */
class ReferencePointList {

    private final ArrayList<ReferencePoint> referencePoints_;
    
   public ReferencePointList() {
      referencePoints_ = new ArrayList<ReferencePoint>();
   }
   /**
    * Adds a new position to the list.
    * @param region 
    */
   public void addPoint(ReferencePoint RP) {
      referencePoints_.add(RP);
   }
  
   public void removePoint(int idx) {
      if (idx >= 0 && idx < referencePoints_.size())
         referencePoints_.remove(idx);
   }
   
   public ReferencePoint getPoint(int idx) {
      if (idx < 0 || idx >= referencePoints_.size())
         return null;
      
      return referencePoints_.get(idx);
   }
   
   public int getNumberOfPoints() {
      return referencePoints_.size();
   }
   
   public void replacePoint(int idx, ReferencePoint RP) {
        referencePoints_.set(idx, RP);
   }
    
}
