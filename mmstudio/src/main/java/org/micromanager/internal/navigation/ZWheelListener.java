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
import com.google.common.util.concurrent.AtomicDouble;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.micromanager.Studio;
import org.micromanager.display.internal.event.DisplayMouseWheelEvent;
import org.micromanager.events.StagePositionChangedEvent;
import org.micromanager.events.internal.DefaultEventManager;
import org.micromanager.events.internal.MouseMovesStageStateChangeEvent;
import org.micromanager.internal.utils.ReportingUtils;

/**
*/
public final class ZWheelListener  {
   private static final double MOVE_INCREMENT = 0.20;
   private final Studio studio_;
   private ZNavigator zNavigator_;
   private boolean active_;

   public ZWheelListener(final Studio studio, final ZNavigator zNavigator) {
      studio_ = studio;
      zNavigator_ = zNavigator;
      active_ = false;
   }
   

   /**
    * Receives mouseWheel events from the display manager and moves the z stage.
    * ZStageMovements are funneled through zNavigator, which runs separate
    * executors for each zStage, and combines movement requests if they come in
    * too fast.
    * 
    * @param e DisplayMouseWheelEvent containing a MouseWheel event
    */
   @Subscribe
   public void mouseWheelMoved(DisplayMouseWheelEvent e) {
      if (!active_) {
         return;
      }
      synchronized (this) {
         // Get needed info from core
         String zStage = studio_.core().getFocusDevice();
         if (zStage == null || zStage.equals("")) {
            return;
         }

         double moveIncrement = MOVE_INCREMENT;
         double pixSizeUm = studio_.core().getPixelSizeUm(true);
         if (pixSizeUm > 0.0) {
            moveIncrement = 2 * pixSizeUm;
         }
         // Get coordinates of event
         int move = e.getEvent().getWheelRotation();
         double moveUm = move * moveIncrement;

         zNavigator_.setPosition(zStage, moveUm);
      }
   }

   @Subscribe
   public void onActiveChange (MouseMovesStageStateChangeEvent mouseMovesStageStateChangeEvent) {
      if (mouseMovesStageStateChangeEvent.getIsEnabled()) {
         active_ = true;
      } else {
         active_ = false;
      }
   }

}