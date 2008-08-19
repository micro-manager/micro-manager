///////////////////////////////////////////////////////////////////////////////
// FILE:          CenterListener.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

// AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu May 21, 2008

// COPYRIGHT:    University of California, San Francisco, 2008

// LICENSE:      This file is distributed under the BSD license.
// License text is included with the source distribution.

// This file is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty
// of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

// IN NO EVENT SHALL THE COPYRIGHT OWNER OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.navigation;

import ij.*;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;

import javax.swing.JOptionPane;
import mmcorej.CMMCore;
import mmcorej.MMCoreJ;

/**
*/
public class CenterListener implements MouseListener {
   private CMMCore core_;
   private ImageCanvas canvas_;
   private static boolean isRunning_ = false;
   private boolean mirrorX_;
   private boolean mirrorY_;
   private boolean transposeXY_;
   private boolean correction_;


   public CenterListener(CMMCore core) {
      core_ = core;
   }

   public void start () {
      if (isRunning_)
         return;

      isRunning_ = true;

      // Get a handle to the AcqWindow
      if (WindowManager.getFrame("AcqWindow") != null) {
         ImagePlus img = WindowManager.getImage("AcqWindow");
         attach(img);
      }
      getOrientation();
   }

   public void stop() {
      if (canvas_ != null) {
         canvas_.removeMouseListener(this);
      }
      isRunning_ = false;
   }

   public boolean isRunning() {
      return isRunning_;
   }

   public void attach(ImagePlus img) {
      if (!isRunning_)
         return;
      ImageWindow win = img.getWindow();
      canvas_ = win.getCanvas();
      canvas_.addMouseListener(this);
      if (Toolbar.getInstance() != null)
         Toolbar.getInstance().setTool(Toolbar.RECTANGLE);
      // TODO: add to ImageJ Toolbar
      //int tool = Toolbar.getInstance().addTool("Test Tool");
      //Toolbar.getInstance().setTool(tool); 
      //images_.addElement(id);
   }

   public void getOrientation() {
      String camera = core_.getCameraDevice();
      if (camera == null) {
         JOptionPane.showMessageDialog(null, "This function does not work without a camera");
         return;
      }
      try {
         String tmp = core_.getProperty(camera, "TransposeCorrection");
         if (tmp.equals("0")) 
            correction_ = false;
         else 
            correction_ = true;
         tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorX());
         if (tmp.equals("0")) 
            mirrorX_ = false;
         else 
            mirrorX_ = true;
         tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorY());
         if (tmp.equals("0")) 
            mirrorY_ = false;
         else 
            mirrorY_ = true;
         tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_SwapXY());
         if (tmp.equals("0")) 
            transposeXY_ = false;
         else 
            transposeXY_ = true;
      } catch(Exception exc) {
         exc.printStackTrace();
         JOptionPane.showMessageDialog(null, "Exception encountered. Please report to the Micro-Manager support team");
         return;
      }
   }

   // Mouse listener implementation
   public void mousePressed(MouseEvent e) {}
   public void mouseReleased(MouseEvent e) {}
   public void mouseExited(MouseEvent e) {}

   public void mouseClicked(MouseEvent e) {
      if ((e.getModifiers() & Event.META_MASK) != 0) // right click: ignore
         return;

      // Get needed info from core
      getOrientation();
      String xyStage = core_.getXYStageDevice();
      if (xyStage == null)
         return;
      double pixSizeUm = core_.getPixelSizeUm();
      if (! (pixSizeUm > 0.0)) {
         JOptionPane.showMessageDialog(null, "Please provide pixel size calibration data before using this function");
         return;
      }
      int width = (int) core_.getImageWidth();
      int height = (int) core_.getImageHeight();

      // Get coordinates of event
      // TODO: correct for ImageJ magnification of the screen!
      int x = e.getX();
      int y = e.getY();
      int cX = canvas_.offScreenX(x);
      int cY = canvas_.offScreenY(y);

      // calculate needed relative movement
      double tmpXUm = ((0.5 * width) - cX) * pixSizeUm;
      double tmpYUm = ((0.5 * height) - cY) * pixSizeUm;

      double mXUm = tmpXUm;
      double mYUm = tmpYUm;
      // if camera does not correct image orientation, we'll correct for it here:
      if (!correction_) {
         // Order: swapxy, then mirror axis
         if (transposeXY_) {mXUm = tmpYUm; mYUm = tmpXUm;}
         if (mirrorX_) {mXUm = -mXUm;}
         if (mirrorY_) {mYUm = -mYUm;}
      }

      // Move the stage
      try {
         core_.setRelativeXYPosition(xyStage, mXUm, mYUm);
      } catch (Exception ex) {
         JOptionPane.showMessageDialog(null, ex.getMessage()); 
         return;
      }

   } 

   public void mouseEntered(MouseEvent e) {}
   public void mouseMoved(MouseEvent e) {}
}
