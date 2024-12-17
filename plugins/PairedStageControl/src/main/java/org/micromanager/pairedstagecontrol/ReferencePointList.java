/**
 *
 * @author kthorn
 */

package org.micromanager.pairedstagecontrol;

import java.io.Serializable;
import java.util.ArrayList;

public class ReferencePointList implements Serializable {
   private static final long serialVersionUID = 1;

   private final ArrayList<ReferencePoint> referencePoints_;
    
   public ReferencePointList() {
      referencePoints_ = new ArrayList<>();
   }

   /**
    * Adds a new position to the list.
    *
    * @param rp ReferencePoint to be added.
    */
   public void addPoint(ReferencePoint rp) {
      referencePoints_.add(rp);
   }
  
   public void removePoint(int idx) {
      if (idx >= 0 && idx < referencePoints_.size()) {
         referencePoints_.remove(idx);
      }
   }
   
   public ReferencePoint getPoint(int idx) {
      if (idx < 0 || idx >= referencePoints_.size()) {
         return null;
      }
      
      return referencePoints_.get(idx);
   }
   
   public int getNumberOfPoints() {
      return referencePoints_.size();
   }
   
   public void replacePoint(int idx, ReferencePoint rp) {
      referencePoints_.set(idx, rp);
   }


}