/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package imagedisplay;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.macro.MacroRunner;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;

/**
 *
 * @author henrypinkard
 */
public class NoZoomCanvas extends ImageCanvas {


   public NoZoomCanvas(ImagePlus imp) {
      super(imp);
      super.hideZoomIndicator(true);
   }
   
   @Override
   public void setSize(Dimension d) {
      super.setSize(d);
   }

   @Override
   public void setPreferredSize(Dimension d) {
      super.setPreferredSize(d);
   }
   
//   /**
//    * This padding causes us to avoid erroneously showing the zoom indicator,
//    * and ensures there's enough space to draw the border.
//    */
//   @Override
//   public Dimension getPreferredSize() {
//      return new Dimension(dstWidth + 2, dstHeight + 2);
//   }

   //Disbale IJ zoom
   @Override
   public void zoomOut(int x, int y) {}

   @Override
   public void unzoom() {}

   @Override
   public void zoom100Percent() {}

   @Override
   public void zoomIn(int sx, int sy) {}

   @Override
   public void setMagnification(double magnification) {}
   
}
