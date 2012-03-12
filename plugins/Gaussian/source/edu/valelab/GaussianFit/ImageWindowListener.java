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

         /*
         double mag = iw_.getCanvas().getMagnification();
         Rectangle vis = iw_.getCanvas().getBounds();
         Dimension d = iw_.getCanvas().getSize();
         Rectangle vis2 = iw_.getBounds();
         Point a = iw_.getCanvas().getLocation();
         Point c = iw_.getCanvas().getCursorLoc();
         int height = iw_.getHeight();
         
         System.out.println("mag : " + mag);
         
         if (mag > 1) {
            ImageRenderer.renderData(iw_, rowData_, renderMode_, 1, vis2);
         }
         else {
            iw_.setImage(originalIP_);
            myMag_ = originalMag_;
         }
         */
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
