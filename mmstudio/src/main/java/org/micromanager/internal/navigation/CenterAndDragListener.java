///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, 2018
//
// COPYRIGHT:    Regents of the University of California, 2018
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.navigation;

import com.google.common.eventbus.Subscribe;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import org.micromanager.Studio;
import org.micromanager.display.internal.event.DisplayMouseEvent;
import org.micromanager.events.internal.MouseMovesStageStateChangeEvent;

/**
 * @author OD, nico
 *
 */
public final class CenterAndDragListener  {

   private final Studio studio_;
   private XYNavigator xyNavigator_;
   private boolean active_;
   private int lastX_, lastY_;

   public CenterAndDragListener(final Studio studio, final XYNavigator xyNavigator) {
      studio_ = studio;
      xyNavigator_ = xyNavigator;
      active_ = false;
   }
   


   /**
    * Handles mouse events and does the actual stage movement
    *
    * @param dme DisplayMouseEvent containing information about the mouse movement
    * TODO: this does not take into account multiple cameras in different displays
    * 
    */
   @Subscribe
   public void onDisplayMouseEvent(DisplayMouseEvent dme) {
      if (!active_) {
         return;
      }
      // only take action when the users selected the Hand tool
      if (dme.getToolId() != ij.gui.Toolbar.HAND) {
         return;
      }
      switch (dme.getEvent().getID()) {
         case MouseEvent.MOUSE_CLICKED:
            if (dme.getEvent().getClickCount() >= 2) {
               // double click, center the stage
               Rectangle location = dme.getLocation();

               int width = (int) studio_.core().getImageWidth();
               int height = (int) studio_.core().getImageHeight();

               // Calculate the center point of the event
               final Point center = new Point(
                       location.x + location.width / 2,
                       location.y + location.height / 2);

               // calculate needed relative movement in pixels
               double tmpXPixels = (0.5 * width) - center.x;
               double tmpYPixels = (0.5 * height) - center.y;

               xyNavigator_.moveSampleOnDisplayPixels(tmpXPixels, tmpYPixels);
            }
            break;
         case MouseEvent.MOUSE_PRESSED:
            // record start position for a drag
            // Calculate the center point of the event
            final Point center = new Point(
                    dme.getLocation().x + dme.getLocation().width / 2,
                    dme.getLocation().y + dme.getLocation().height / 2);
            lastX_ = center.x;
            lastY_ = center.y;
            break;
         case MouseEvent.MOUSE_DRAGGED:
            // compare to start position and move stage

            // Get coordinates of event
            final Point center2 = new Point(
                    dme.getLocation().x + dme.getLocation().width / 2,
                    dme.getLocation().y + dme.getLocation().height / 2);

            // calculate needed relative movement
            double tmpXUm = center2.x - lastX_;
            double tmpYUm = center2.y - lastY_;
            lastX_ = center2.x;
            lastY_ = center2.y;

            xyNavigator_.moveSampleOnDisplayPixels(tmpXUm, tmpYUm);

            break;
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