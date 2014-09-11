package org.micromanager.patternoverlay;

import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.ShapeRoi;

import java.awt.geom.GeneralPath;
import java.util.prefs.Preferences;


/**
 *  Since this creates an overlay, stored images will not be affected, and
 *  no persistent change is made to the actual image; rather, this adds another
 *  layer on top of the life view window.
 *
 *  @author Jon
 */
public class GridOverlay extends GenericOverlay {
   
   public GridOverlay(Preferences prefs, String prefPrefix) {
      super(prefs, prefPrefix);
   }

   @Override
   protected Overlay getOverlay(int width, int height) {
      
      // based on http://rsbweb.nih.gov/ij/plugins/download/Grid_Overlay.java
      
      int numPanels = size_/12 + 2;
      float dWidth  = (float) width / numPanels;
      float dHeight = (float) height / numPanels;
      
      GeneralPath path = new GeneralPath();
      float xoff = width/2 - (numPanels*dWidth/2);  // make sure we end up centered
      for (int iii = 0; iii < numPanels; ++iii) { // draw vertical lines
         path.moveTo(xoff, 0f);
         path.lineTo(xoff, height);
         xoff += dHeight;
      }
      float yoff = height/2 - (numPanels*dHeight/2);  // make sure we end up centered
      for (int iii = 0; iii < numPanels; ++iii) { // draw horizontal lines
         path.moveTo(0f, yoff);
         path.lineTo(width, yoff);
         yoff += dWidth;
      }
      
      Roi roi = new ShapeRoi(path);
      roi.setStrokeColor(color_);
      return new Overlay(roi);
   }


}

