/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquiremultipleregions;

import java.util.ArrayList;


/**
 *
 * @author kthorn
 */
class RegionList {
    private final ArrayList<Region> regions_;
    
   public RegionList() {
      regions_ = new ArrayList<Region>();
   }
   /**
    * Adds a new position to the list.
    * @param region 
    */
   public void addRegion(Region region) {
      regions_.add(region);
   }
  
   public void removeRegion(int idx) {
      if (idx >= 0 && idx < regions_.size())
         regions_.remove(idx);
   }
   
   public Region getRegion(int idx) {
      if (idx < 0 || idx >= regions_.size())
         return null;
      
      return regions_.get(idx);
   }
   
   public int getNumberOfRegions() {
      return regions_.size();
   }
   
   public boolean isFileNameUnique(String directory, String filename) {
       //test if directory / filename already exists in regionlist
       for (int i=0; i<regions_.size(); i++) {
           Region r = regions_.get(i);
           if (directory.equals(r.directory) && filename.equals(r.filename))
                   return false;           
       }
       return true;
   }
   
}