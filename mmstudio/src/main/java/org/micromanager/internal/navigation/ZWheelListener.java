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
import java.awt.event.MouseWheelEvent;
import org.micromanager.Studio;
import org.micromanager.display.internal.event.DisplayMouseWheelEvent;
import org.micromanager.internal.dialogs.StageControlFrame;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Classs that translates changes in the mouse wheel into Z stage movements.
 */
public final class ZWheelListener {
   private static final double MOVE_INCREMENT = 0.20;
   private final Studio studio_;
   private final ZNavigator zNavigator_;
   private final MutablePropertyMapView settings_;

   public ZWheelListener(final Studio studio, final ZNavigator zNavigator) {
      studio_ = studio;
      zNavigator_ = zNavigator;
      settings_ = studio_.profile().getSettings(StageControlFrame.class);
   }


   /**
    * Receives mouseWheel events from the display manager and moves the z stage.
    * ZStageMovements are funneled through zNavigator, which runs separate
    * executors for each zStage, and combines movement requests if they come in
    * too fast.
    *
    * <p>The step size depends on modifier keys held during the scroll:
    * <ul>
    *   <li>No modifier: optimal Z step from the core ({@code getPixelSizeOptimalZUm}),
    *       falling back to 2 &times; pixel size, or {@value MOVE_INCREMENT} &micro;m if
    *       no pixel size is configured. This matches the "Use:" value shown in the
    *       MDA dialog Z-stack section.</li>
    *   <li>Ctrl: fine step size configured in the Stage Control dialog for the
    *       current focus device.</li>
    *   <li>Shift: medium step size configured in the Stage Control dialog for the
    *       current focus device.</li>
    * </ul>
    *
    * @param e DisplayMouseWheelEvent containing a MouseWheel event
    */
   @Subscribe
   public void mouseWheelMoved(DisplayMouseWheelEvent e) {
      synchronized (this) {
         String zStage = studio_.core().getFocusDevice();
         if (zStage == null || zStage.equals("")) {
            return;
         }

         MouseWheelEvent mwe = e.getEvent();
         double moveIncrement;

         if (mwe.isControlDown()) {
            moveIncrement = settings_.getDouble(
                  StageControlFrame.SMALL_MOVEMENT_Z + zStage, 1.1);
         } else if (mwe.isShiftDown()) {
            moveIncrement = settings_.getDouble(
                  StageControlFrame.MEDIUM_MOVEMENT_Z + zStage, 11.1);
         } else {
            double optimalZ = 0.0;
            try {
               optimalZ = studio_.core().getPixelSizeOptimalZUm(true);
            } catch (Exception ex) {
               studio_.logs().logError(ex, "Failed to get optimal Z step");
            }
            if (optimalZ > 0.0) {
               moveIncrement = optimalZ;
            } else {
               double pixSizeUm = studio_.core().getPixelSizeUm(true);
               moveIncrement = pixSizeUm > 0.0 ? 2.0 * pixSizeUm : MOVE_INCREMENT;
            }
         }

         int move = mwe.getWheelRotation();
         zNavigator_.setPosition(zStage, move * moveIncrement);
      }
   }


}