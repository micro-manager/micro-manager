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
import org.micromanager.Studio;
import org.micromanager.display.DataViewer;
import org.micromanager.display.internal.displaywindow.DisplayController;
import org.micromanager.display.internal.event.DataViewerWillCloseEvent;
import org.micromanager.events.internal.MouseMovesStageStateChangeEvent;
import org.micromanager.internal.MMStudio;

/**
 * This class handles setting up and disabling mouse and keyboard
 * shortcuts for stage motion.
 */
public final class UiMovesStageManager {
   private final HashMap<DisplayController, CenterAndDragListener> displayToDragListener_;
   private final HashMap<DisplayController, ZWheelListener> displayToWheelListener_;
   private final HashMap<DisplayController, XYZKeyListener> displayToKeyListener_;
   private final Studio studio_;
   private final XYNavigator xyNavigator_;
   private final ZNavigator zNavigator_;

   public UiMovesStageManager(Studio studio) {
      studio_ = studio;
      xyNavigator_ = new XYNavigator(studio_);
      zNavigator_ = new ZNavigator(studio_);
      displayToDragListener_ = new HashMap<>();
      displayToWheelListener_ = new HashMap<>();
      displayToKeyListener_ = new HashMap<>();
      // Calling code has to register us for studio_ events
   }

   /**
    * Keep track of enabling/disabling click-to-move for this display.
    * @param display Display to which we will listen for events
    */
   public void activate(final DisplayController display) {
      CenterAndDragListener dragListener = null;
      ZWheelListener wheelListener = null;
      XYZKeyListener keyListener = null;
      if (((MMStudio) studio_).getMMMenubar().getToolsMenu().getMouseMovesStage()) {
         dragListener = new CenterAndDragListener(studio_, xyNavigator_);
         display.registerForEvents(dragListener);
         studio_.events().registerForEvents(dragListener);
         wheelListener = new ZWheelListener(studio_, zNavigator_);
         display.registerForEvents(wheelListener);
         studio_.events().registerForEvents(wheelListener);
         keyListener = new XYZKeyListener(studio_, xyNavigator_, zNavigator_);
         display.registerForEvents(keyListener);
         // EVALUATE: Should keys always be active? I like the ability to switch
         // them off so that the user can use the ImageJ shortcuts
         studio_.events().registerForEvents(keyListener);
      }
      displayToDragListener_.put(display, dragListener);
      displayToWheelListener_.put(display, wheelListener);
      displayToKeyListener_.put(display, keyListener);
   }

   public void deActivate(final DataViewer displayToDeActivate) {
      synchronized (displayToDragListener_) {
         for (DisplayController display : displayToDragListener_.keySet()) {
            if (display.equals(displayToDeActivate)) {
               // Deactivate listener for this display.
               if (null != displayToDragListener_.get(display)) {
                  display.unregisterForEvents(displayToDragListener_.get(display));
               }
               displayToDragListener_.remove(display);
            }
         }
      }
      
      synchronized (displayToWheelListener_) {
         for (DisplayController display : displayToWheelListener_.keySet()) {
            if (display.equals(displayToDeActivate)) {
               if (displayToWheelListener_.get(display) != null) {
                  display.unregisterForEvents(displayToWheelListener_.get(display));
               }
               displayToWheelListener_.remove(display);
            }
         }
      }

      synchronized (displayToKeyListener_) {
         for (DisplayController display : displayToKeyListener_.keySet()) {
            if (display.equals(displayToDeActivate)) {
               if (displayToKeyListener_.get(display) != null) {
                  display.unregisterForEvents(displayToKeyListener_.get(display));
               }
               displayToKeyListener_.remove(display);
            }
         }
      }
   }

   @Subscribe
   public void onDataViewerWillCloseEvent(DataViewerWillCloseEvent e) {
      deActivate(e.getDataViewer());
   }

   public XYNavigator getXYNavigator() {
      return xyNavigator_;
   }

   public ZNavigator getZNavigator() {
      return zNavigator_;
   }

}