package com.imaging100x.hcs;

import org.micromanager.navigation.PositionList;

public class WellPositionList {
   String label_;
   PositionList sites_;
   
   public WellPositionList() {
      sites_ = new PositionList();
   }
   
   String getLabel() {
      return label_;
   }
   
   public void setLabel(String lab) {
      label_ = lab;
   }
   
   public PositionList getSitePositions() {
      return sites_;
   }

   public void setSitePositions(PositionList pl) {
      sites_ = pl;
   }
}
