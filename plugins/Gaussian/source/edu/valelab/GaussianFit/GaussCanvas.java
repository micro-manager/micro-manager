/*
 * Listener that can be attached to ImageJ Image windows to alter their behavior
 * 
 * Copyright UCSF, 2012.  BSD license
 */
package edu.valelab.GaussianFit;

import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Toolbar;
import ij.plugin.tool.PlugInTool;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 *
 * @author Nico Stuurman
 */
public class GaussCanvas extends ImageCanvas {
   DataCollectionForm.MyRowData rowData_;
   int renderMode_;
   ImagePlus originalIP_;
   ImageWindow iw_;
   double myMag_;
   double originalMag_;
   SpotDataFilter sf_;
   

   public GaussCanvas(ImagePlus sp, DataCollectionForm.MyRowData rowData,
           int renderMode, double initialMag, SpotDataFilter sf) {
      super(sp);
      rowData_ = rowData;
      renderMode_ = renderMode;
      originalIP_ = sp;
      originalMag_ = myMag_ = initialMag;
      sf_ = sf;
   }

   public void setImageWindow(ImageWindow iw) {
      iw_ = iw;
   }

   @Override
   public void mouseReleased(MouseEvent me) {
      if (Toolbar.getToolId() != Toolbar.MAGNIFIER) {
         super.mouseReleased(me);
         return;
      }
      
      // TODO: override zoomin, zoomout functions instead
      double mag = this.getMagnification() * myMag_;
      myMag_ = mag;
      Rectangle vis = this.getBounds();
      Dimension d = this.getSize();
      Rectangle vis2 = this.getBounds();
      Point a = this.getLocation();
      Point c = this.getCursorLoc();

      System.out.println("mag : " + mag);

      if (mag > 1) {
         Rectangle roi = null;
         if (d.width >= (mag * rowData_.width_)) {
            roi = new Rectangle(0, 0, (int) (mag * rowData_.width_),
                    (int) (mag * rowData_.height_));
         } else {
            int xCenter = (int) (c.x * mag);
            int yCenter = (int) (c.y * mag);
            int halfWidth = d.width / 2;
            int halfHeight = d.height / 2;
            if (xCenter - halfWidth < 0) {
               xCenter = halfWidth;
            }
            if (xCenter + halfWidth > (int) (mag * rowData_.width_)) {
               xCenter = (int) (mag * rowData_.width_) - halfWidth;
            }
            if (yCenter - halfHeight < 0) {
               yCenter = halfHeight;
            }
            if (yCenter + halfHeight > (int) (mag * rowData_.height_)) {
               yCenter = (int) (mag * rowData_.height_) - halfHeight;
            }

            roi = new Rectangle(xCenter - halfWidth, yCenter - halfHeight,
                    d.width, d.height);

         }
         //ImageRenderer.renderData(iw_, rowData_, renderMode_, mag, roi, sf_);

      } else {
         //iw_.setImage(originalIP_);
         // myMag_ = originalMag_;
      }

   }

   

   
}
