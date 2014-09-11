package org.micromanager.patternoverlay;

import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.ShapeRoi;

import java.awt.geom.Ellipse2D;
import java.util.prefs.Preferences;


/**
 *  Since this creates an overlay, stored images will not be affected, and
 *  no persistent change is made to the actual image; rather, this adds another
 *  layer on top of the life view window.
 *
 *  @author Jon
 */
public class CircleOverlay extends GenericOverlay {
   
   public CircleOverlay(Preferences prefs, String prefPrefix) {
      super(prefs, prefPrefix);
   }

   @Override
   protected Overlay getOverlay(int width, int height) {
      
      double radius = java.lang.Math.min(width, height)/2 * size_/100;
      
      Ellipse2D.Double circle = new Ellipse2D.Double(width/2 - radius, height/2 - radius, 2*radius, 2*radius);
      
      Roi roi = new ShapeRoi(circle);
      roi.setStrokeColor(color_);
      return new Overlay(roi);
   }


}

