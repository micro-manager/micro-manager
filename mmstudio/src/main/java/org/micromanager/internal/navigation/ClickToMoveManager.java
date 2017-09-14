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
import ij.IJ;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
//import org.micromanager.display.DisplayDestroyedEvent;
import org.micromanager.display.internal.displaywindow.DisplayController;
import org.micromanager.events.internal.MouseMovesStageEvent;
import org.micromanager.internal.MMStudio;

/**
 * This class handles setting up and disabling click-to-move and keyboard
 * shortcuts for stage motion.
 */
public final class ClickToMoveManager {
   private final HashMap<DisplayController, CenterAndDragListener> displayToDragListener_;
   private final HashMap<DisplayController, KeyAdapter> displayToKeyListener_;
   private final CMMCore core_;
   private final MMStudio studio_;

   private static ClickToMoveManager staticInstance_;

   public ClickToMoveManager(MMStudio studio, CMMCore core) {
      studio_ = studio;
      core_ = core;
      displayToDragListener_ = new HashMap<DisplayController, CenterAndDragListener>();
      displayToKeyListener_ = new HashMap<DisplayController, KeyAdapter>();
      staticInstance_ = this;
      studio_.events().registerForEvents(this);
   }

   /**
    * Keep track of enabling/disabling click-to-move for this display.
    */
   public void activate(DisplayController display) {
      CenterAndDragListener dragListener = null;
      KeyAdapter keyListener = null;
      display.registerForEvents(this);
      if (studio_.getIsClickToMoveEnabled()) {
         dragListener = new CenterAndDragListener(core_, studio_, display);
         keyListener = new StageShortcutListener();
      }
      // TODO
      // display.getCanvas().removeKeyListener(IJ.getInstance());
      // display.getCanvas().addKeyListener(keyListener);
      displayToDragListener_.put(display, dragListener);
      displayToKeyListener_.put(display, keyListener);
   }

   @Subscribe
   public void onMouseMovesStage(MouseMovesStageEvent event) {
      try {
         for (DisplayController display : displayToDragListener_.keySet()) {
            if (event.getIsEnabled()) {
               // Create listeners for each display.
               activate(display);
            }
            else {
               // Deactivate listeners for each display.
               CenterAndDragListener listener = displayToDragListener_.get(display);
               if (listener != null) {
                  listener.stop();
               }
               KeyAdapter keyListener = displayToKeyListener_.get(display);
               if (keyListener != null) {
                  // TODO
                  // display.getCanvas().removeKeyListener(keyListener);
                  // display.getCanvas().addKeyListener(IJ.getInstance());
               }
               // Still need to keep track of the display so we can reactivate
               // it later if necessary.
               displayToDragListener_.put(display, null);
               displayToKeyListener_.put(display, null);
            }
         }
      }
      catch (Exception e) {
         studio_.logs().logError(e, "Error updating mouse-moves-stage info");
      }
   }

   // TOD
   /*
   @Subscribe
   public void onDisplayDestroyed(DisplayDestroyedEvent event) {
      DisplayController display = (DisplayController) event.getDisplay();
      display.unregisterForEvents(this);
      displayToDragListener_.remove(display);
      displayToKeyListener_.remove(display);
   }
   */

   public static ClickToMoveManager getInstance() {
      return staticInstance_;
   }

   /**
    * Listens for certain key presses and moves the stage.
    */
   private class StageShortcutListener extends KeyAdapter {
      @Override
      public void keyPressed(KeyEvent e) {
         double dx = 0;
         double dy = 0;
         double dz = 0;
         switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT:
               dx = -1;
               break;
            case KeyEvent.VK_RIGHT:
               dx = 1;
               break;
            case KeyEvent.VK_UP:
               dy = 1;
               break;
            case KeyEvent.VK_DOWN:
               dy = -1;
               break;
            case KeyEvent.VK_1:
            case KeyEvent.VK_U:
            case KeyEvent.VK_PAGE_UP:
               dz = -1;
               break;
            case KeyEvent.VK_2:
            case KeyEvent.VK_J:
            case KeyEvent.VK_PAGE_DOWN:
               dz = 1;
               break;
            default:
               break;
         }
         if (e.isControlDown()) {
            // Fine motion (1px, or .2um in Z)
            dz *= .2;
         }
         else if (e.isShiftDown()) {
            // Full-field motion.
            dx *= core_.getImageWidth();
            dy *= core_.getImageHeight();
            dz *= 2;
         }
         else if (e.isAltDown()) {
            // Half-field motion (default in Z).
            dx *= core_.getImageWidth() / 2.0;
            dy *= core_.getImageHeight() / 2.0;
            dz += .2;
         }
         else {
            // No modifiers: 10px at a time, or .6um in Z
            dx *= 10.0;
            dy *= 10.0;
            dz *= .6;
         }

         if (dz != 0) {
            moveZ(dz);
         }
         if (dx != 0 || dy != 0) {
            moveXY(dx, dy);
         }
      }

      private void moveZ(double dz) {
         String device = core_.getFocusDevice();
         try {
            if (device == null || core_.deviceBusy(device)) {
               return;
            }
            int direction = core_.getFocusDirection(device);
            if (direction != 0) {
               dz *= direction;
            }
            core_.setRelativePosition(device, dz);
         }
         catch (Exception e) {
            studio_.logs().logError(e, "Error moving in Z");
         }
      }

      private void moveXY(double dx, double dy) {
         String stage = core_.getXYStageDevice();
         String camera = core_.getCameraDevice();
         try {
            if (stage == null || core_.deviceBusy(stage)) {
               return;
            }
            double pixelSize = core_.getPixelSizeUm();
            if (pixelSize <= 0) {
               studio_.logs().showError("Please perform pixel calibration before using keyboard shortcuts to move the stage.");
            }
            // Note shouldCorrect means "the camera is not doing correction
            // for us".
            boolean shouldCorrect = core_.getProperty(camera,
                  MMCoreJ.getG_Keyword_Transpose_Correction()).equals("0");
            boolean mirrorX = !core_.getProperty(camera,
                  MMCoreJ.getG_Keyword_Transpose_MirrorX()).equals("0");
            boolean mirrorY = !core_.getProperty(camera,
                  MMCoreJ.getG_Keyword_Transpose_MirrorY()).equals("0");
            boolean transpose = !core_.getProperty(camera,
                  MMCoreJ.getG_Keyword_Transpose_SwapXY()).equals("0");

            if (shouldCorrect) {
               if (transpose) {
                  double tmp = dx;
                  dx = dy;
                  dy = tmp;
               }
               if (mirrorX) {
                  dx *= -1;
               }
               if (mirrorY) {
                  dy *= -1;
               }
            }
            core_.setRelativeXYPosition(stage, dx, dy);
         }
         catch (Exception e) {
            studio_.logs().logError(e, "Error moving in XY");
         }
      }
   }
}
