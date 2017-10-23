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
import mmcorej.CMMCore;
import org.micromanager.display.internal.event.DisplayMouseWheelEvent;
import org.micromanager.events.StagePositionChangedEvent;
import org.micromanager.events.internal.DefaultEventManager;
import org.micromanager.internal.utils.ReportingUtils;

/**
*/
public final class ZWheelListener  {
   private final CMMCore core_;
   private static final double MOVE_INCREMENT = 0.20;

   public ZWheelListener(final CMMCore core) {
      core_ = core;
   }
      
   /**
    * Receives mouseWheel events from the display manager and moves the 
    * z stage
    * @param e DisplayMouseWheelEvent containing a MouseWheel event
    */

   @Subscribe
   public void mouseWheelMoved(DisplayMouseWheelEvent e) {
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
		  int move = e.getEvent().getWheelRotation();
		  
        // Move the stage
        try {
           core_.setRelativePosition(zStage, move * moveIncrement);

           double z = core_.getPosition(zStage);
           DefaultEventManager.getInstance().post(
                   new StagePositionChangedEvent(zStage, z));
        } catch (Exception ex) {
           ReportingUtils.showError(ex);
        }
	  }
   } 
   
  
}
