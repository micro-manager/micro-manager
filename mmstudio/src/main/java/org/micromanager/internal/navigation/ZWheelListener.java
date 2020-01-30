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
import org.micromanager.internal.utils.ReportingUtils;

/**
*/
public final class ZWheelListener  {
   private static final double MOVE_INCREMENT = 0.20;
   private final Studio studio_;
   private final ExecutorService executorService_;
   private final ZStageMover zStageMover_;
   private final AtomicDouble moveMemory_;
   private Future<?> future_;

   public ZWheelListener(final Studio studio, 
         final ExecutorService executorService) {
      studio_ = studio;
      executorService_ = executorService;
      zStageMover_ = new ZStageMover();
      moveMemory_ = new AtomicDouble(0.0);
   }
   
      private class ZStageMover implements Runnable {

      String stage_;
      double pos_;

      public void setPosition(String stage, double pos) {
         stage_ = stage;
         pos_ = pos;
      }

      @Override
      public void run() {
         // Move the stage
        try {
           pos_ += moveMemory_.getAndSet(0.0);
           studio_.core().setRelativePosition(stage_, pos_);
           double z = studio_.core().getPosition(stage_);
           studio_.events().post(
                   new StagePositionChangedEvent(stage_, z));
        } catch (Exception ex) {
           ReportingUtils.showError(ex);
        }
      }
   }

   /**
    * Receives mouseWheel events from the display manager and moves the z stage
    * The ZStage is moved using an executor service. To avoid piling up
    * movement requests, requests are only submitted when the previous
    * task was done.  Requested movements are remembered and added 
    * to the latest request.
    * 
    * @param e DisplayMouseWheelEvent containing a MouseWheel event
    */
   @Subscribe
   public void mouseWheelMoved(DisplayMouseWheelEvent e) {
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

         // if the executor is busy, wait for the next move command, and pool
         // the distance we want to move
         if (future_ == null || future_.isDone()) {
            zStageMover_.setPosition(zStage, moveUm);
            future_ = executorService_.submit(zStageMover_);
         } else {
            // moveMemory_ is added to the requested movement in the zStageMover
            moveMemory_.addAndGet(moveUm);
         }
      }
   }
   
  
}
