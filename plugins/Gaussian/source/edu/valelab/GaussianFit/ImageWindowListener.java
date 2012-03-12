/*
 * Listener that can be attached to ImageJ Image windows to alter their behavior
 * 
 * Copyright UCSF, 2012.  BSD license
 */
package edu.valelab.GaussianFit;

import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.gui.Toolbar;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 *
 * @author Nico Stuurman
 */
public class ImageWindowListener implements MouseListener {
   ImageWindow iw_;
   DataCollectionForm.MyRowData rowData_;
   int renderMode_;
   ImagePlus originalIP_;
   double myMag_;
   double originalMag_;
   
   public ImageWindowListener(ImageWindow iw, DataCollectionForm.MyRowData rowData,
           int renderMode, double initialMag) {
      iw_ = iw;
      rowData_ = rowData;
      renderMode_ = renderMode;
      originalIP_ = iw_.getImagePlus(); 
      originalMag_ = myMag_ = initialMag;
   }

   public void mouseClicked(MouseEvent me) {
      if (Toolbar.getToolId() == Toolbar.MAGNIFIER) {


         double mag = iw_.getCanvas().getMagnification() * myMag_;
         myMag_ = mag;
         Rectangle vis = iw_.getCanvas().getBounds();
         Dimension d = iw_.getCanvas().getSize();
         Rectangle vis2 = iw_.getBounds();
         Point a = iw_.getCanvas().getLocation();
         Point c = iw_.getCanvas().getCursorLoc();
         
         System.out.println("mag : " + mag);
         
         if (mag > 1) {
            Rectangle roi = null;
            if (d.width >= (mag * rowData_.width_) ) {
               roi = new Rectangle (0, 0, (int) (mag * rowData_.width_), 
                       (int) (mag * rowData_.height_) );
            } else {
               int xCenter = (int) (c.x * mag);
               int yCenter = (int) (c.y * mag);
               int halfWidth = d.width / 2;
               int halfHeight = d.height / 2;
               if (xCenter - halfWidth < 0) 
                  xCenter = halfWidth;
               if (xCenter + halfWidth > (int) (mag * rowData_.width_))
                  xCenter = (int) (mag * rowData_.width_) - halfWidth;
               if (yCenter - halfHeight < 0) 
                  yCenter = halfHeight;
               if (yCenter + halfHeight > (int) (mag * rowData_.height_))
                  yCenter = (int) (mag * rowData_.height_) - halfHeight;
                       
               roi = new Rectangle (xCenter - halfWidth, yCenter - halfHeight,
                       d.width, d.height);
               
            }
            // ImageRenderer.renderData(iw_, rowData_, renderMode_, mag, roi);

         }
         else {
            //iw_.setImage(originalIP_);
            //myMag_ = originalMag_;
         }

      }
         
   }

   public void mousePressed(MouseEvent me) {
      // throw new UnsupportedOperationException("Not supported yet.");
   }

   public void mouseReleased(MouseEvent me) {
      // throw new UnsupportedOperationException("Not supported yet.");
   }

   public void mouseEntered(MouseEvent me) {
      // throw new UnsupportedOperationException("Not supported yet.");
   }

   public void mouseExited(MouseEvent me) {
      // throw new UnsupportedOperationException("Not supported yet.");
   }
   
}
