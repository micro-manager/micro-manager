package org.micromanager.hcs;

import org.micromanager.PositionList;

/**
 * A list of sites for a single well in a multi-well plate.
 * This class is used to store the list of sites for a single well in a multi-well plate.
 * It also stores the row and column of the well in the plate.
 */
public class WellPositionList {
   private String label_;

   private PositionList sites_;
   private int row_ = 0;
   private int col_ = 0;

   /**
    * Constructor.
    */
   public WellPositionList() {
      sites_ = new PositionList();
   }
   
   String getLabel() {
      return label_;
   }
   
   public void setLabel(String lab) {
      label_ = lab;
   }

   /**
    * The list of sites for this well.
    */
   public PositionList getSitePositions() {
      return sites_;
   }

   public void setSitePositions(PositionList pl) {
      sites_ = pl;
   }

   /**
    * The row of the well in the plate...
    */
   public int getRow() {
      return row_;
   }

   /**
    * The column of the well in the plate...
    */
   public int getColumn() {
      return col_;
   }

   /**
    * Set the row and column of the well in the plate.
    */
   public void setGridCoordinates(int r, int c) {
      row_ = r;
      col_ = c;
   }
}
