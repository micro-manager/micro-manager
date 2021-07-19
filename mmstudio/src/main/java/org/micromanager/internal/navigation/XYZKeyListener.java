///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

// COPYRIGHT:    University of California, San Francisco, 2008-2020

// LICENSE:      This file is distributed under the BSD license.
// License text is included with the source distribution.

// This file is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty
// of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

// IN NO EVENT SHALL THE COPYRIGHT OWNER OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.navigation;

import static org.micromanager.internal.dialogs.StageControlFrame.MEDIUM_MOVEMENT_Z;
import static org.micromanager.internal.dialogs.StageControlFrame.SELECTED_Z_DRIVE;
import static org.micromanager.internal.dialogs.StageControlFrame.SMALL_MOVEMENT_Z;
import static org.micromanager.internal.dialogs.StageControlFrame.X_MOVEMENTS;
import static org.micromanager.internal.dialogs.StageControlFrame.Y_MOVEMENTS;

import com.google.common.eventbus.Subscribe;
import java.awt.event.KeyEvent;
import mmcorej.CMMCore;
import org.micromanager.Studio;
import org.micromanager.display.internal.event.DisplayKeyPressEvent;
import org.micromanager.propertymap.MutablePropertyMapView;


/**
 * Moves XY and X stage based on keypresses.
 *
 * @author Nico Stuurman
 */
public final class XYZKeyListener  {
   private final CMMCore core_;
   private final MutablePropertyMapView settings_;
   private final ZNavigator zNavigator_;
   private final XYNavigator xyNavigator_;
   double[] xMovesMicron_;
   double[] yMovesMicron_;
   double[] zMovesMicron_;

   /**
    * The XYZKeyListener receives settings from the StageControl plugin
    * by means of the user profile.
    *
    * <p>Array sizes, etc. match those of the StageControl plugin, so changes
    * there may break things here.
    *
    * <p>Movement requests funnel to the single instance of the
    * XYNavigator.  The XYNavigator send the actual movement comman to the
    * XY stage using its own executor. Movements can be send even while
    * the stage is moving.  Once the stage stops moving, all (added)
    * movement requests will be combined into a single stage movement.
    *
    * @param studio Our beloved Micro-Manager Studio object
    * @param xyNavigator Object that manages communication to XY Stages
    * @param zNavigator Object that manages communication to Z Stages.
    */
   public XYZKeyListener(Studio studio, XYNavigator xyNavigator, ZNavigator zNavigator) {
      core_ = studio.getCMMCore();
      xyNavigator_ = xyNavigator;
      zNavigator_ = zNavigator;

      xMovesMicron_ = new double[] {1.0, core_.getImageWidth() / 4.0, core_.getImageWidth()};
      yMovesMicron_ = new double[] {1.0, core_.getImageHeight() / 4.0, core_.getImageHeight()};
      zMovesMicron_ = new double[] {1.0, 10.0};

      settings_ = studio.profile().getSettings(
           org.micromanager.internal.dialogs.StageControlFrame.class);
   }

   /**
    * Handles the event signalling that a key was pressed while the display had focus.
    *
    * @param dkpe information about the actual event.
    */
   @Subscribe
   public void keyPressed(DisplayKeyPressEvent dkpe) {
      final KeyEvent e = dkpe.getKeyEvent();
      boolean consumed = false;
      for (int i = 0; i < xMovesMicron_.length; ++i) {
         xMovesMicron_[i] = settings_.getDouble(X_MOVEMENTS[i], xMovesMicron_[i]);
      }
      for (int i = 0; i < yMovesMicron_.length; ++i) {
         yMovesMicron_[i] = settings_.getDouble(Y_MOVEMENTS[i], yMovesMicron_[i]);
      }
      zMovesMicron_[0] = settings_.getDouble(SMALL_MOVEMENT_Z, 1.1);
      zMovesMicron_[1] = settings_.getDouble(MEDIUM_MOVEMENT_Z, 11.1);

      switch (e.getKeyCode()) {
         case KeyEvent.VK_LEFT:
         case KeyEvent.VK_RIGHT:
         case KeyEvent.VK_UP:
         case KeyEvent.VK_DOWN:
            //XY step
            double xMicron = xMovesMicron_[1];
            double yMicron = yMovesMicron_[1];
            if (e.isControlDown()) {
               xMicron = xMovesMicron_[0];
               yMicron = yMovesMicron_[0];
            } else if (e.isShiftDown()) {
               xMicron = xMovesMicron_[2];
               yMicron = yMovesMicron_[2];
            }
            switch (e.getKeyCode()) {
               case KeyEvent.VK_LEFT:
                  xyNavigator_.moveSampleOnDisplayUm(-xMicron, 0);
                  consumed = true;
                  break;
               case KeyEvent.VK_RIGHT:
                  xyNavigator_.moveSampleOnDisplayUm(xMicron, 0);
                  consumed = true;
                  break;
               case KeyEvent.VK_UP:
                  xyNavigator_.moveSampleOnDisplayUm(0, yMicron);
                  consumed = true;
                  break;
               case KeyEvent.VK_DOWN:
                  xyNavigator_.moveSampleOnDisplayUm(0, -yMicron);
                  consumed = true;
                  break;
               default:
                  break;
            }
            break;
         case KeyEvent.VK_1:
         case KeyEvent.VK_U:
         case KeyEvent.VK_PAGE_UP:
         case KeyEvent.VK_2:
         case KeyEvent.VK_J:
         case KeyEvent.VK_PAGE_DOWN:
            double zMicron = zMovesMicron_[0];
            if (e.isShiftDown()) {
               zMicron = zMovesMicron_[1];
            }
            switch (e.getKeyCode()) {
               case KeyEvent.VK_1:
               case KeyEvent.VK_U:
               case KeyEvent.VK_PAGE_UP:
                  incrementZ(-zMicron);
                  consumed = true;
                  break;
               case KeyEvent.VK_2:
               case KeyEvent.VK_J:
               case KeyEvent.VK_PAGE_DOWN:
                  incrementZ(zMicron);
                  consumed = true;
                  break;
               default:
                  break;
            }
            break;
         default:
            break;
      }
      if (consumed) {
         dkpe.consume();
      }
   }

   /**
    * Relative movement of the Z stage by the given amount.
    *
    * @param micron How much to jove the stage in microns.
    */
   public void incrementZ(double micron) {
      // Get needed info from core
      String zStage = settings_.getString(SELECTED_Z_DRIVE, core_.getFocusDevice());
      if (zStage == null || zStage.length() == 0) {
         return;
      }

      zNavigator_.setPosition(zStage, micron);
   }

}
