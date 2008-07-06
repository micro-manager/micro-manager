///////////////////////////////////////////////////////////////////////////////
// FILE:          ZWheelListener.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

// AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu May 30, 2008

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
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import javax.swing.JOptionPane;
import mmcorej.CMMCore;

/**
*/
public class ZWheelListener implements MouseWheelListener {
   private CMMCore core_;
   private ImageCanvas canvas_;
   private static boolean isRunning_ = false;
   private static final double moveIncrement_ = 0.10;

   public ZWheelListener(CMMCore core) {
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
   }

   public void stop() {
      if (canvas_ != null) {
         canvas_.removeMouseWheelListener(this);
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
      canvas_.addMouseWheelListener(this);
      // TODO:  add to ImageJ Toolbar
      //int tool = Toolbar.getInstance().addTool("Test Tool");
      //Toolbar.getInstance().setTool(tool); 
      //images_.addElement(id);
   }

   public void mouseWheelMoved(MouseWheelEvent e) {
      // Get needed info from core
      String zStage = core_.getFocusDevice();
      if (zStage == null)
         return;
 
      double moveIncrement = moveIncrement_;
      double pixSizeUm = core_.getPixelSizeUm();
      if (pixSizeUm > 0.0) {
         moveIncrement = pixSizeUm;
      }
      // Get coordinates of event
      int move = e.getWheelRotation();

      // Move the stage
      try {
         core_.setRelativePosition(zStage, move * moveIncrement);
      } catch (Exception ex) {
         JOptionPane.showMessageDialog(null, ex.getMessage()); 
         return;
      }

   } 

}
