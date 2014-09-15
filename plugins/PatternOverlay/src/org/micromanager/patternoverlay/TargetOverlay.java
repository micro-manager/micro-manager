package org.micromanager.patternoverlay;

import ij.gui.Roi;
import ij.gui.ShapeRoi;

import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.util.prefs.Preferences;


/**
 * Three concentric circles with diameters of relative size 1, 3, and 5
 * 
 * @author Jon
 */
public class TargetOverlay extends GenericOverlay {
   
   public TargetOverlay(Preferences prefs, String prefPrefix) {
      super(prefs, prefPrefix);
   }

   @Override
   protected Roi getRoi(int width, int height) {
      
      double radius = java.lang.Math.min(width, height)/2 * size_/100;
      
      GeneralPath path = new GeneralPath();
      Ellipse2D.Double circle = new Ellipse2D.Double(width/2 - radius, height/2 - radius, 2*radius, 2*radius);
      path.append(circle, false);
      radius = radius*3/5;
      Ellipse2D.Double circle2 = new Ellipse2D.Double(width/2 - radius, height/2 - radius, 2*radius, 2*radius);
      path.append(circle2, false);
      radius = radius/3;
      Ellipse2D.Double circle3 = new Ellipse2D.Double(width/2 - radius, height/2 - radius, 2*radius, 2*radius);
      path.append(circle3, false);
      
      Roi roi = new ShapeRoi(path);
      roi.setStrokeColor(color_);
      return roi;
   }


}

