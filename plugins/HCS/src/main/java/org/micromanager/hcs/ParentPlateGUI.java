package org.micromanager.hcs;

import java.awt.geom.Point2D;
import org.micromanager.PositionList;

/**
 * Interface for the High Content screening GUI.
 */
public interface ParentPlateGUI {
   void updatePointerXYPosition(double x, double y, String wellLabel, String siteLabel);

   void updateStagePositions(double x, double y, double z, String wellLabel,
                                    String siteLabel);

   String getXYStageName();

   boolean useThreePtAF();

   PositionList getThreePointList();

   Double getThreePointZPos(double x, double y);

   void displayError(String errTxt);

   Point2D.Double getOffset();

   Point2D.Double applyOffset(Point2D.Double pt);
}
