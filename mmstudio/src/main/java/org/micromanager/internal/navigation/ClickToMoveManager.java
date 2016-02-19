///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

import java.util.HashMap;

import mmcorej.CMMCore;

import org.micromanager.display.DisplayDestroyedEvent;
import org.micromanager.display.internal.DefaultDisplayWindow;
import org.micromanager.events.internal.MouseMovesStageEvent;
import org.micromanager.internal.MMStudio;

/**
 * This class handles setting up and disabling click-to-move.
 */
public class ClickToMoveManager {
   private final HashMap<DefaultDisplayWindow, CenterAndDragListener> displayToListener_;
   private final CMMCore core_;
   private final MMStudio studio_;

   private static ClickToMoveManager staticInstance_;

   public ClickToMoveManager(MMStudio studio, CMMCore core) {
      studio_ = studio;
      core_ = core;
      displayToListener_ = new HashMap<DefaultDisplayWindow, CenterAndDragListener>();
      staticInstance_ = this;
      studio_.events().registerForEvents(this);
   }

   /**
    * Keep track of enabling/disabling click-to-move for this display.
    */
   public void activate(DefaultDisplayWindow display) {
      CenterAndDragListener listener = null;
      display.registerForEvents(this);
      if (studio_.getIsClickToMoveEnabled()) {
         listener = new CenterAndDragListener(core_, studio_, display);
      }
      displayToListener_.put(display, listener);
   }

   @Subscribe
   public void onMouseMovesStage(MouseMovesStageEvent event) {
      try {
         for (DefaultDisplayWindow display : displayToListener_.keySet()) {
            if (event.getIsEnabled()) {
               // Create listeners for each display.
               activate(display);
            }
            else {
               // Deactivate listeners for each display.
               CenterAndDragListener listener = displayToListener_.get(display);
               if (listener != null) {
                  listener.stop();
               }
               // Still need to keep track of the display so we can reactivate
               // it later if necessary.
               displayToListener_.put(display, null);
            }
         }
      }
      catch (Exception e) {
         studio_.logs().logError(e, "Error updating mouse-moves-stage info");
      }
   }

   @Subscribe
   public void onDisplayDestroyed(DisplayDestroyedEvent event) {
      DefaultDisplayWindow display = (DefaultDisplayWindow) event.getDisplay();
      display.unregisterForEvents(this);
      displayToListener_.remove(display);
   }

   public static ClickToMoveManager getInstance() {
      return staticInstance_;
   }
}
