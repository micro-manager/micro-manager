package com.imaging100x.hcs;


public interface ParentPlateGUI {
   public void updatePointerXYPosition(double x, double y, String wellLabel, String siteLabel);
   public void updateStageXYPosition(double x, double y, String wellLabel, String siteLabel);
   public String getXYStageName();
}
