package org.micromanager.patternoverlay;

import ij.gui.Roi;
import ij.gui.ShapeRoi;

import java.awt.geom.GeneralPath;
import java.util.prefs.Preferences;


/**
 *  creates crosshair overlay
 *
 *  @author Matthijs
 *  @author Jon
 */
public class CrosshairOverlay extends GenericOverlay {
   
   public CrosshairOverlay(Preferences prefs, String prefPrefix) {
      super(prefs, prefPrefix);
   }

   @Override
   protected Roi getRoi(int width, int height) {
      
      // based on http://micro-manager.3463995.n2.nabble.com/Reference-lines-in-live-view-tt7583130.html#a7583134
      
      int dWidth  = (int) (size_/100f * width)  / 2;
      int dHeight = (int) (size_/100f * height) / 2;
      
      GeneralPath path = new GeneralPath();
      path.moveTo(width / 2 - dWidth, height / 2 - dHeight);
      path.lineTo(width / 2 + dWidth, height / 2 + dHeight);
      path.moveTo(width / 2 + dWidth, height / 2 - dHeight);
      path.lineTo(width / 2 - dWidth, height / 2 + dHeight);
      
      Roi roi = new ShapeRoi(path);
      roi.setStrokeColor(color_);
      return roi;
   }


}

