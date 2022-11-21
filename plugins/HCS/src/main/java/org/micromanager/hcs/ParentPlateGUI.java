package org.micromanager.hcs;

import java.awt.geom.Point2D;
import org.micromanager.PositionList;

public interface ParentPlateGUI {
   public void updatePointerXYPosition(double x, double y, String wellLabel, String siteLabel);

   public void updateStagePositions(double x, double y, double z, String wellLabel,
                                    String siteLabel);

   public String getXYStageName();

   public boolean useThreePtAF();

   public PositionList getThreePointList();

   public Double getThreePointZPos(double x, double y);

   public void displayError(String errTxt);

   public Point2D.Double getOffset();

   public Point2D.Double applyOffset(Point2D.Double pt);
}
