/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.slideexplorer;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.awt.geom.Point2D;
import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.StagePosition;

/**
 *
 * @author arthur
 */
public class MultiStagePositionMosaic extends MultiStagePosition {
   ArrayList<Point2D.Double> subPositions;
   Rectangle bounds;

   MultiStagePositionMosaic(String xystage, double x, double y) {
      super();
      StagePosition sp = new StagePosition();
      sp.stageName = xystage;
      sp.x = x;
      sp.y = y;
      this.add(sp);
   }
}
