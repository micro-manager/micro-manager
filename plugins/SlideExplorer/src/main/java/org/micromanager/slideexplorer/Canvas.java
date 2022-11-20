package org.micromanager.slideexplorer;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.Toolbar;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

public class Canvas extends ImageCanvas {

   protected static final long serialVersionUID = 1L;
   private Display display_;
   private boolean wasDragged_ = false;
   private Frame win_;

   Canvas(Display display, ImagePlus imgp) {
      super(imgp);
      display_ = display;
      setShowAllROIs(true);
   }

   protected void scroll(int sx, int sy) {
      int ox = xSrcStart + sx;  //convert to offscreen coordinates
      int oy = ySrcStart + sy;
      int newx = xSrcStart + (xMouseStart - ox);
      int newy = ySrcStart + (yMouseStart - oy);
      srcRect.x = newx;
      srcRect.y = newy;
      imp.draw();
      Thread.yield();
   }

   public void setDimensions(int width, int height) {
      imageWidth = width;
      imageHeight = height;
   }

   public void paint(Graphics g) {
      if (IJ.isMacOSX()) {

         final int l = Math.max(0, srcRect.x - xSrcStart);
         final int t = Math.max(0, srcRect.y - ySrcStart);
         final int r = Math.min(0, srcRect.x - xSrcStart + srcRect.width);
         final int b = Math.min(0, srcRect.y - ySrcStart + srcRect.height);

         final Color prevColor = g.getColor();
         g.setColor(Color.BLACK);
         if (l > 0) {
            g.fillRect(0, 0, l, dstHeight);
         }
         if (r < dstWidth) {
            g.fillRect(r, 0, dstWidth, dstHeight);
         }
         if (t > 0) {
            g.fillRect(0, 0, dstWidth, t);
         }
         if (b < dstHeight) {
            g.fillRect(0, b, dstWidth, dstHeight);
         }
         g.setColor(prevColor);
      }
      super.paint(g);
   }

   public void mouseDragged(MouseEvent e) {
      super.mouseDragged(e);
      wasDragged_ = true;
   }

   public void mouseReleased(MouseEvent e) {
      super.mouseReleased(e);
      int clicks = e.getClickCount();
      if (Toolbar.getToolId() == Toolbar.HAND || IJ.spaceBarDown()) {
         if (clicks == 0 || clicks == 1) { // End of a drag.
            if (wasDragged_) {
               wasDragged_ = false;
               updateAfterPan();
               //display_.survey();
            } else {
               if (clicks == 1) {
                  Point offScreenPos = onScreenToOffScreen(e.getPoint());
                  display_.onClick(offScreenPos);
               }
            }
         }


      } else if (!IJ.spaceBarDown()
            && ((Toolbar.getToolId() == Toolbar.POINT)
            || (Toolbar.getToolId() == Toolbar.RECTANGLE)
            || (Toolbar.getToolId() == Toolbar.FREEROI)
            || (Toolbar.getToolId() == Toolbar.POLYGON))) {
         display_.roiDrawn();
      }
   }

   public void updateAfterPan() {
      display_.pan(srcRect, false);
   }

   public void fitToWindow() {


      Window win = (Window) imp.getWindow();
      if (win == null) {
         return;
      }

      Rectangle bounds = win.getBounds();
      int width;
      int height;

      final Rectangle oldSrcRect = (Rectangle) this.getSrcRect().clone();

      if (!win.isFullscreen()) {
         Insets insets = win.getInsets();
         width = bounds.width;
         height = bounds.height - 40;
         this.setBounds(0, 0, width, height);
      } else {
         width = bounds.width;
         height = bounds.height - 40;
         this.setBounds(0, 0, width, height);
      }

      display_.updateDimensions();

      setDrawingSize(width, height);
      setDimensions(width, height);

      Rectangle panRect = new Rectangle(oldSrcRect.x - (srcRect.width - oldSrcRect.width) / 2,
            oldSrcRect.y - (srcRect.height - oldSrcRect.height) / 2, width, height);
      display_.pan(panRect, true);
      getParent().doLayout();

   }


   public void zoomOut(int x, int y) {
      display_.zoomOut(new Point(x, y));
   }

   public void zoomIn(int x, int y) {
      display_.zoomIn(new Point(x, y));
   }

   public Point onScreenToOffScreen(Point onScreen) {
      return new Point(offScreenX(onScreen.x), offScreenY(onScreen.y));
   }

   public double getMagnification() {
      return magnification;
   }

}



