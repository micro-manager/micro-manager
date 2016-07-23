///////////////////////////////////////////////////////////////////////////////
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

package org.micromanager.internal.navigation;

import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;

import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;


import org.micromanager.internal.MMStudio;
import mmcorej.CMMCore;
import org.micromanager.internal.interfaces.LiveModeListener;
import org.micromanager.internal.utils.ReportingUtils;

/**
*/
public class ZWheelListener implements MouseWheelListener, LiveModeListener {
   private CMMCore core_;
   private MMStudio studio_;
   private ImageCanvas canvas_;
   private static boolean isRunning_ = false;
   private static final double moveIncrement_ = 0.20;

   public ZWheelListener(CMMCore core, MMStudio gui) {
      core_ = core;
      studio_ = gui;
   }

   public void start () {
      // Get a handle to the AcqWindow
      if (WindowManager.getCurrentWindow() != null) {
         start(WindowManager.getCurrentWindow());
      }
   }
   
   public void start(ImageWindow win) {
      if (isRunning_) {
         stop(); 
      }

	  isRunning_ = true;
	  if (win != null) {
		  attach(win);
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

   public void attach(ImageWindow win) {
      if (!isRunning_)
         return;
      canvas_ = win.getCanvas();
      canvas_.addMouseWheelListener(this);
   }

   @Override
   public void mouseWheelMoved(MouseWheelEvent e) {
	  synchronized(this) {
		  // Get needed info from core
		  String zStage = core_.getFocusDevice();
		  if (zStage == null || zStage.equals(""))
			  return;
 
		  double moveIncrement = moveIncrement_;
		  double pixSizeUm = core_.getPixelSizeUm();
		  if (pixSizeUm > 0.0) {
			  moveIncrement = 2 * pixSizeUm;
		  }
		  // Get coordinates of event
		  int move = e.getWheelRotation();
		  
		  // Move the stage
		  try {
			  core_.setRelativePosition(zStage, move * moveIncrement);
		  } catch (Exception ex) {
			  ReportingUtils.showError(ex);
			  return;
		  }
        studio_.updateZPosRelative(move * moveIncrement);
	  }
   } 
   
   public void liveModeEnabled(boolean enabled) {
      if (enabled) {
         start();
      } else {
         stop();
      }
   }
}
