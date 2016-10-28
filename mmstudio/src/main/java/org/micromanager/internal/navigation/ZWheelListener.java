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

import com.google.common.eventbus.Subscribe;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import mmcorej.CMMCore;
import org.micromanager.display.DisplayWindow;
import org.micromanager.events.DisplayAboutToShowEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.interfaces.LiveModeListener;
import org.micromanager.internal.utils.ReportingUtils;

/**
*/
public final class ZWheelListener implements MouseWheelListener, LiveModeListener {
   private final CMMCore core_;
   private final MMStudio studio_;
   private ImageCanvas canvas_;
   private static boolean isRunning_ = false;
   private static boolean waitingForWindow_ = false;
   private static final double MOVE_INCREMENT = 0.20;

   public ZWheelListener(CMMCore core, MMStudio studio) {
      core_ = core;
      studio_ = studio;
   }
   /**
    * Even when the live mode manager starts live mode, the ZWheel Listener
    * can only attach to an existing window.  Therefore, listen for the formation
    * of a new display.  If the listener has been started and there was no window
    * 
    * @param datse Handle to the new display
    */
   @Subscribe
   public synchronized void onNewDisplay(DisplayAboutToShowEvent datse) {
      try {
      ImageWindow win = datse.getDisplay().getImagePlus().getWindow();
      if (waitingForWindow_) {
         start(win);
      }
      } catch (NullPointerException npe) {
         studio_.logs().logError(npe, "New display has no ImageJ Window in ZWheelListener");
      }
   }

   /**
    * We can only start if there is a live window
    * Otherwise, be ready for one and start in the OnNewDisplay event handler
    */
   public synchronized void start () {
      waitingForWindow_ = true;
      // Get a handle to the AcqWindow
      DisplayWindow dw = studio_.getSnapLiveManager().getDisplay();
      if (dw != null) {
         ImageWindow win = dw.getImagePlus().getWindow();
         start (win);
      }
   }
   
   public void start(ImageWindow win) {
      waitingForWindow_ = false;
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
      waitingForWindow_ = false;
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
 
		  double moveIncrement = MOVE_INCREMENT;
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
   
   @Override
   public void liveModeEnabled(boolean enabled) {
      if (enabled) {
         start();
      } else {
         stop();
      }
   }
}
